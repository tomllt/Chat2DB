package ai.chat2db.community.query.excel.domain.core.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.QueryExcelConstants;
import ai.chat2db.community.query.excel.domain.api.enums.ExportStatus;
import ai.chat2db.community.query.excel.domain.api.enums.TemplateStatus;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ExcelExportRecord;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.ExportResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.SheetConfig;
import ai.chat2db.community.query.excel.domain.api.model.ValidationError;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.permission.QueryExcelPermissionChecker;
import ai.chat2db.community.query.excel.domain.api.service.IExcelExportService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelRenderService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelReportTemplateService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelTemplateValidationService;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import ai.chat2db.community.query.excel.domain.core.util.ExcelTemplateFileUtil;
import ai.chat2db.community.query.excel.storage.ExcelExportRecordStorage;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full Excel export flow (requirements §8.10, §8.11, §9.2, §12.3).
 * <p>Validates the template, executes the saved query view query exactly once
 * (§12.3 — the same {@link QueryResult} feeds every sheet), checks the §9.2
 * permission chain, masks sensitive fields (§8.11), renders the .xlsx via
 * {@link IExcelRenderService}, enforces export limits, writes an
 * {@link ExcelExportRecord} audit row, and returns a download token.</p>
 * <p>Export audit records and their .xlsx bytes are kept in memory along with
 * the {@link ExcelExportRecordStorage} collection; {@link #download(String)}
 * resolves a token to the stored bytes until the token expires.</p>
 */
@Service
public class ExcelExportServiceImpl implements IExcelExportService {

    /** Masked value for fields the user has no permission to access (§8.11). */
    static final String SENSITIVE_FIELD_MASK = "***";

    private final IExcelReportTemplateService templateService;
    private final ISavedQueryViewService savedQueryViewService;
    private final IQueryDatasetService queryDatasetService;
    private final IExcelRenderService renderService;
    private final IExcelTemplateValidationService templateValidationService;
    private final QueryExcelPermissionChecker permissionChecker;
    private final ExcelExportRecordStorage exportRecordStorage;

    /** In-memory download tokens → generated .xlsx bytes. */
    private final Map<String, byte[]> downloadStore = new HashMap<>();

    /**
     * Fully injectable constructor. {@code @Autowired} so Spring uses the
     * Spring-managed collaborator beans (which carry the SQL executor wired by
     * the API integration layer); without this, the default constructor would
     * create private, un-wired service instances whose executor stays null and
     * export would fail with {@link UnsupportedOperationException}.
     */
    @org.springframework.beans.factory.annotation.Autowired
    ExcelExportServiceImpl(IExcelReportTemplateService templateService,
                           ISavedQueryViewService savedQueryViewService,
                           IExcelRenderService renderService,
                           IExcelTemplateValidationService templateValidationService,
                           QueryExcelPermissionChecker permissionChecker,
                           ExcelExportRecordStorage exportRecordStorage,
                           IQueryDatasetService queryDatasetService) {
        this.templateService = templateService;
        this.savedQueryViewService = savedQueryViewService;
        this.renderService = renderService;
        this.templateValidationService = templateValidationService;
        this.permissionChecker = permissionChecker;
        this.exportRecordStorage = exportRecordStorage;
        this.queryDatasetService = queryDatasetService;
    }

    /**
     * Default wiring: real services and the singleton export record storage.
     *
     * @deprecated manual wiring only — in a Spring runtime the injectable
     * constructor is used so the SQL executor reaches the query path; this
     * default builds un-wired private service instances whose executor is null.
     */
    @Deprecated
    public ExcelExportServiceImpl() {
        this(new ExcelReportTemplateServiceImpl(),
                new SavedQueryViewServiceImpl(),
                new ExcelRenderServiceImpl(),
                new ExcelTemplateValidationServiceImpl(
                        new ExcelReportTemplateServiceImpl(),
                        new SavedQueryViewServiceImpl(),
                        new QueryDatasetServiceImpl()),
                ai.chat2db.community.query.excel.domain.api.permission.LocalPermissionChecker.INSTANCE,
                ExcelExportRecordStorage.INSTANCE,
                new QueryDatasetServiceImpl());
    }

    @Override
    public ExportResult export(Long templateId, Long queryViewId, List<ViewFilter> filterOverrides) {
        List<ViewFilter> safeFilterOverrides = filterOverrides == null ? List.of() : filterOverrides;

        // Generate a unique query ID for audit (§12.4)
        String queryId = UUID.randomUUID().toString();

        ExcelExportRecord record = new ExcelExportRecord();
        record.setQueryId(queryId);
        record.setTemplateId(templateId);
        record.setQueryViewId(queryViewId);
        record.setStatus(ExportStatus.RUNNING.name());
        record.setGmtCreate(new Date());
        Long exportId = exportRecordStorage.save(record);
        record.setId(exportId);

        try {
            // 1. Template must exist (§8.10)
            ExcelReportTemplate template = templateService.getById(templateId);
            if (template == null) {
                throw ex(ErrorCode.EX_TEMPLATE_NOT_FOUND);
            }

            // 2. Template file must exist on disk (use template.getTemplateFile() if set, else utility path)
            File templateFile = (template.getTemplateFile() != null && !template.getTemplateFile().isBlank())
                    ? new File(template.getTemplateFile())
                    : ExcelTemplateFileUtil.getTemplateFile(templateId);
            if (templateFile == null || !templateFile.exists()) {
                throw ex(ErrorCode.EX_CORRUPTED_TEMPLATE);
            }

            // 3. Template must be in VALID status
            if (!TemplateStatus.VALID.name().equals(template.getStatus())) {
                throw ex(ErrorCode.EX_TEMPLATE_NOT_FOUND);
            }

            // 4. Structural template validation — non-warning errors block export
            List<ValidationError> validationErrors = templateValidationService.validateTemplate(templateId);
            if (hasFatalValidationError(validationErrors)) {
                throw ex(ErrorCode.EX_CORRUPTED_TEMPLATE);
            }

            // 5. Enforce sheet-level limits before query execution (§12.2)
            List<SheetConfig> sheetConfigs = template.getSheetConfigs();
            int sheetCount = sheetConfigs == null ? 0 : sheetConfigs.size();
            if (sheetCount > QueryExcelConstants.MAX_SHEETS) {
                throw ex(ErrorCode.EX_FILE_SIZE_EXCEEDED);
            }
            if (sheetConfigs != null) {
                for (SheetConfig config : sheetConfigs) {
                    List<ExcelColumnBinding> bindings = config.getFieldBindings();
                    if (bindings != null && bindings.size() > QueryExcelConstants.MAX_BINDINGS_PER_SHEET) {
                        throw ex(ErrorCode.EX_FILE_SIZE_EXCEEDED);
                    }
                }
            }

            // 6. Execute the query exactly once; the same result feeds all sheets (§12.3)
            long queryStart = System.currentTimeMillis();
            QueryResult queryResult = savedQueryViewService.executeQuery(queryViewId, safeFilterOverrides);
            long queryMs = System.currentTimeMillis() - queryStart;

            List<String> columns = queryResult.getColumns() == null
                    ? List.of() : queryResult.getColumns();
            List<List<Object>> rows = queryResult.getRows() == null
                    ? List.of() : queryResult.getRows();

            // 7. Enforce row limit before rendering (§12.2)
            if (rows.size() > QueryExcelConstants.MAX_EXPORT_ROWS) {
                throw ex(ErrorCode.EX_ROW_LIMIT_EXCEEDED);
            }

            // 8. Permission chain (§9.2)
            SavedQueryView view = savedQueryViewService.getById(queryViewId);
            Long userId = view == null ? null : view.getOwnerId();
            checkPermissions(userId, template, view, queryViewId);

            // 9. Sensitive field masking (§8.11) — mask values the user cannot access
            List<List<Object>> maskedRows = maskSensitiveFields(userId, rows, columns);

            // 10. Enforce per-sheet row limit before rendering
            if (sheetConfigs != null) {
                for (SheetConfig config : sheetConfigs) {
                    if (rows.size() > QueryExcelConstants.MAX_ROWS_PER_SHEET) {
                        throw ex(ErrorCode.EX_ROW_LIMIT_EXCEEDED);
                    }
                }
            }

            // 11. Render the .xlsx from the (masked) query result
            byte[] rendered;
            try {
                rendered = renderService.render(template, maskedRows, columns);
            } catch (QueryExcelException qe) {
                throw qe;
            } catch (Exception e) {
                throw ex(ErrorCode.EX_CORRUPTED_TEMPLATE);
            }

            // 12. Enforce file size limit after render (§12.2)
            if (rendered.length > QueryExcelConstants.MAX_FILE_SIZE_BYTES) {
                throw ex(ErrorCode.EX_FILE_SIZE_EXCEEDED);
            }

            // 13. Populate full audit record (§12.4)
            record.setUserId(userId);
            record.setWorkspaceId(view == null ? null : view.getWorkspaceId());
            if (view != null) {
                record.setDatasetId(view.getDatasetId());
                record.setDatasetVersion(view.getDatasetVersion());
                record.setQueryViewVersion(view.getVersion());
            }
            if (template != null) {
                record.setTemplateVersion(template.getTemplateVersion());
            }
            record.setQueryMs(queryMs);
            record.setRowCount(rows.size());
            record.setFileSize((long) rendered.length);
            record.setPermissionResult(permissionSummary(userId, template, view));
            record.setExportedAt(new Date());
            record.setStatus(ExportStatus.SUCCESS.name());

            // 14. Generate download token (UUID, 1-hour expiry)
            String downloadToken = UUID.randomUUID().toString();
            record.setDownloadToken(downloadToken);
            record.setDownloadTokenExpiresAt(new Date(System.currentTimeMillis()
                    + QueryExcelConstants.DOWNLOAD_TOKEN_EXPIRY_MINUTES * 60_000L));
            exportRecordStorage.update(record);
            downloadStore.put(downloadToken, rendered);

            // 15. Return result
            return ExportResult.builder()
                    .downloadToken(downloadToken)
                    .exportId(exportId)
                    .rowCount(rows.size())
                    .fileSize(rendered.length)
                    .status(ExportStatus.SUCCESS.name())
                    .build();
        } catch (QueryExcelException qe) {
            record.setStatus(ExportStatus.FAILED.name());
            record.setErrorCode(qe.getErrorCode());
            exportRecordStorage.update(record);
            throw qe;
        } catch (Exception e) {
            record.setStatus(ExportStatus.FAILED.name());
            record.setErrorCode(ErrorCode.EX_CORRUPTED_TEMPLATE.getCode());
            exportRecordStorage.update(record);
            throw new QueryExcelException(ErrorCode.EX_CORRUPTED_TEMPLATE.getCode(),
                    ErrorCode.EX_CORRUPTED_TEMPLATE.getMessage() + ": " + e.getMessage());
        }
    }

    @Override
    public byte[] download(String downloadToken) {
        if (downloadToken == null || downloadToken.isBlank()) {
            throw ex(ErrorCode.EX_TEMPLATE_NOT_FOUND);
        }
        ExcelExportRecord record = findRecordByDownloadToken(downloadToken);
        if (record == null || !ExportStatus.SUCCESS.name().equals(record.getStatus())) {
            throw ex(ErrorCode.EX_TEMPLATE_NOT_FOUND);
        }
        if (record.getDownloadTokenExpiresAt() != null
                && record.getDownloadTokenExpiresAt().before(new Date())) {
            throw ex(ErrorCode.EX_TEMPLATE_NOT_FOUND);
        }
        byte[] bytes = downloadStore.remove(downloadToken);
        if (bytes == null) {
            throw ex(ErrorCode.EX_TEMPLATE_NOT_FOUND);
        }
        // Single-use token: it is deleted after a successful download (§8.10, §9.3)
        record.setDownloadToken(null);
        record.setDownloadTokenExpiresAt(null);
        exportRecordStorage.update(record);
        return bytes;
    }

    // ── permission chain (§9.2) ──────────────────────────────────

    private void checkPermissions(Long userId, ExcelReportTemplate template,
                                  SavedQueryView view, Long queryViewId) {
        if (userId == null && !permissionChecker.canExecuteView(null, queryViewId)) {
            throw ex(ErrorCode.EX_NO_PERMISSION);
        }
        if (!permissionChecker.canExecuteView(userId, queryViewId)) {
            throw ex(ErrorCode.EX_NO_PERMISSION);
        }
        if (view != null && view.getDatasetId() != null
                && !permissionChecker.canViewDataset(userId, view.getDatasetId())) {
            throw ex(ErrorCode.EX_NO_PERMISSION);
        }
        QueryDataset dataset = view == null || view.getDatasetId() == null
                ? null : queryDatasetService.getById(view.getDatasetId());
        if (dataset != null && dataset.getDatasourceId() != null
                && !permissionChecker.canAccessDatasource(userId, dataset.getDatasourceId())) {
            throw ex(ErrorCode.EX_NO_PERMISSION);
        }
        if (!permissionChecker.canExportTemplate(userId, template.getId())) {
            throw ex(ErrorCode.EX_NO_PERMISSION);
        }
    }

    /**
     * Masks every value whose column is bound to a query view field the user
     * cannot access (§8.11, §13.11). Masking happens on the fully qualified
     * column names returned by the query; bindings are not required for masking.
     */
    private List<List<Object>> maskSensitiveFields(Long userId, List<List<Object>> rows, List<String> columns) {
        boolean[] sensitiveOrdinals = new boolean[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            String fieldId = columns.get(i);
            if (fieldId != null && !permissionChecker.canAccessField(userId, fieldId)) {
                sensitiveOrdinals[i] = true;
            }
        }

        boolean anySensitive = false;
        for (boolean sensitive : sensitiveOrdinals) {
            if (sensitive) {
                anySensitive = true;
                break;
            }
        }
        if (!anySensitive) {
            return rows;
        }

        List<List<Object>> masked = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            List<Object> maskedRow = new ArrayList<>(row);
            for (int i = 0; i < sensitiveOrdinals.length; i++) {
                if (sensitiveOrdinals[i] && i < maskedRow.size()) {
                    maskedRow.set(i, SENSITIVE_FIELD_MASK);
                }
            }
            masked.add(maskedRow);
        }
        return masked;
    }

    // ── helpers ──────────────────────────────────────────────────

    /**
     * Returns {@code true} when the validation result contains at least one
     * non-warning error (§8.10 step 4).
     */
    private static boolean hasFatalValidationError(List<ValidationError> errors) {
        if (errors == null) {
            return false;
        }
        return errors.stream().anyMatch(e -> e == null || !e.isWarning());
    }

    private ExcelExportRecord findRecordByDownloadToken(String downloadToken) {
        List<ExcelExportRecord> records = exportRecordStorage.getDataList();
        if (records == null) {
            return null;
        }
        for (ExcelExportRecord record : records) {
            if (record != null && Objects.equals(downloadToken, record.getDownloadToken())) {
                return record;
            }
        }
        return null;
    }

    private static String permissionSummary(Long userId, ExcelReportTemplate template, SavedQueryView view) {
        return "view=" + (view == null ? "?" : view.getId())
                + ",template=" + (template == null ? "?" : template.getId());
    }

    private static QueryExcelException ex(ErrorCode errorCode) {
        return new QueryExcelException(errorCode.getCode(), errorCode.getMessage());
    }
}