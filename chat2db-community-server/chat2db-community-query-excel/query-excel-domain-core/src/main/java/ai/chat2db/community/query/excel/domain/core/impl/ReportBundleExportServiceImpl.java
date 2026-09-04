package ai.chat2db.community.query.excel.domain.core.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.QueryExcelConstants;
import ai.chat2db.community.query.excel.domain.api.enums.ExportStatus;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.ExportResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleExportRequest;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.query.excel.domain.api.model.SheetConfig;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.service.IExcelExportService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelRenderService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelReportTemplateService;
import ai.chat2db.community.query.excel.domain.api.service.IReportBundleExportService;
import ai.chat2db.community.query.excel.domain.api.service.IReportBundleService;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Exports report versions from immutable snapshots rather than mutable drafts.
 * <p>Identifier quoting, query execution, and dialect handling are delegated to
 * the existing {@link ISavedQueryViewService} and {@link SqlGenerator}; the
 * upload-template rendering semantics in {@link IExcelRenderService} are
 * preserved by feeding a snapshot-derived template instead of a mutated draft.
 * Each successful export produces a single-use download token that resolves via
 * {@link #download(String)} using the same envelope shape as
 * {@link IExcelExportService}.</p>
 */
@Service
public class ReportBundleExportServiceImpl implements IReportBundleExportService {

    private final IReportBundleService reportBundleService;
    private final IExcelExportService excelExportService;
    private final IExcelReportTemplateService templateService;
    private final ISavedQueryViewService savedQueryViewService;
    private final IExcelRenderService renderService;

    /** In-memory single-use tokens for rendered snapshot bytes. */
    private final Map<String, byte[]> downloadStore = new ConcurrentHashMap<>();

    /** Workspace binding for each token, enforcing local workspace ownership at download. */
    private final Map<String, Long> tokenWorkspaceStore = new ConcurrentHashMap<>();

    /** Token expiry timestamps (epoch millis); null-safe via {@link Date}. */
    private final Map<String, Long> tokenExpiryStore = new ConcurrentHashMap<>();

    /** Monotonic counter for synthetic export record identifiers. */
    private final AtomicLong exportIdSequence = new AtomicLong();

    public ReportBundleExportServiceImpl(IReportBundleService reportBundleService,
                                          IExcelExportService excelExportService) {
        this(reportBundleService, excelExportService, null, null, null);
    }

    @Autowired
    public ReportBundleExportServiceImpl(IReportBundleService reportBundleService,
                                          IExcelExportService excelExportService,
                                          IExcelReportTemplateService templateService,
                                          ISavedQueryViewService savedQueryViewService,
                                          IExcelRenderService renderService) {
        this.reportBundleService = reportBundleService;
        this.excelExportService = excelExportService;
        this.templateService = templateService;
        this.savedQueryViewService = savedQueryViewService;
        this.renderService = renderService;
    }

    @Override
    public ExportResult export(Long workspaceId, Long bundleId, Long versionId, Long templateId,
                                List<ViewFilter> runtimeFilters) {
        return exportSnapshot(workspaceId, bundleId, versionId,
                ReportBundleExportRequest.builder().templateId(templateId).runtimeFilters(runtimeFilters).build());
    }

    @Override
    public ExportResult exportSnapshot(Long workspaceId, Long bundleId, Long versionId,
                                        ReportBundleExportRequest request) {
        ReportBundleVersion version = reportBundleService.getVersion(workspaceId, bundleId, versionId);
        ReportBundle bundle = reportBundleService.getById(workspaceId, bundleId);
        if (bundle == null || !Objects.equals(workspaceId, bundle.getWorkspaceId())
                || !Objects.equals(workspaceId, version.getWorkspaceId())
                || !Objects.equals(bundleId, version.getBundleId())) {
            throw invalidVersion();
        }
        if (templateService == null || savedQueryViewService == null || renderService == null) {
            throw invalidVersion();
        }
        Long queryViewId = bundle.getQueryViewId();
        List<ViewFilter> effectiveFilters = mergeFilters(version.getPresetRowFiltersSnapshot(), version.getRowFilter(),
                request.getRuntimeFilters());
        QueryResult result = savedQueryViewService.executeQuery(queryViewId, effectiveFilters);
        List<Map<String, Object>> selectedRows = selectRows(result.getRows(), result.getColumns(),
                version.getSelectedRowKeys());
        ExcelReportTemplate template = snapshotTemplate(templateService.getById(request.getTemplateId()),
                version.getBoundFieldsSnapshot());
        List<List<Object>> rows = selectedRows.stream().map(row -> result.getColumns().stream()
                .map(row::get).toList()).toList();
        byte[] rendered = renderService.render(template, rows, result.getColumns());

        // Issue a single-use download token that mirrors the legacy IExcelExportService
        // envelope so callers can download the rendered snapshot via download(token).
        String downloadToken = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis()
                + QueryExcelConstants.DOWNLOAD_TOKEN_EXPIRY_MINUTES * 60_000L;
        evictExpiredTokens(System.currentTimeMillis());
        downloadStore.put(downloadToken, rendered);
        tokenWorkspaceStore.put(downloadToken, workspaceId);
        tokenExpiryStore.put(downloadToken, expiresAt);

        long exportId = exportIdSequence.incrementAndGet();
        return ExportResult.builder()
                .downloadToken(downloadToken)
                .exportId(exportId)
                .rowCount(rows.size())
                .fileSize(rendered.length)
                .status(ExportStatus.SUCCESS.name())
                .build();
    }

    @Override
    public byte[] download(Long workspaceId, String downloadToken) {
        long now = System.currentTimeMillis();
        evictExpiredTokens(now);
        if (workspaceId == null || downloadToken == null || downloadToken.isBlank()) {
            throw tokenNotFound();
        }
        Long tokenWorkspaceId = tokenWorkspaceStore.get(downloadToken);
        Long expiresAt = tokenExpiryStore.get(downloadToken);
        if (expiresAt == null || expiresAt < now) {
            removeToken(downloadToken);
            throw tokenNotFound();
        }
        if (!Objects.equals(workspaceId, tokenWorkspaceId)) {
            throw tokenNotFound();
        }
        byte[] bytes = downloadStore.remove(downloadToken);
        removeToken(downloadToken);
        if (bytes == null) {
            throw tokenNotFound();
        }
        return bytes;
    }

    private static ExcelReportTemplate snapshotTemplate(ExcelReportTemplate source,
                                                         List<ExcelColumnBinding> bindings) {
        if (source == null) {
            throw new QueryExcelException(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(),
                    ErrorCode.EX_TEMPLATE_NOT_FOUND.getMessage());
        }
        ExcelReportTemplate target = new ExcelReportTemplate();
        target.setId(source.getId());
        target.setTemplateFile(source.getTemplateFile());
        target.setStatus(source.getStatus());
        target.setTemplateVersion(source.getTemplateVersion());
        target.setSheetConfigs(source.getSheetConfigs() == null ? null : source.getSheetConfigs().stream()
                .map(config -> snapshotSheet(config, bindings)).toList());
        return target;
    }

    private static SheetConfig snapshotSheet(SheetConfig source, List<ExcelColumnBinding> bindings) {
        SheetConfig target = new SheetConfig();
        target.setSheetName(source.getSheetName());
        target.setDataStartRow(source.getDataStartRow());
        target.setDataStartColumn(source.getDataStartColumn());
        target.setHeaderMapping(source.getHeaderMapping());
        target.setRowExpansionMode(source.getRowExpansionMode());
        target.setFreezeRows(source.getFreezeRows());
        target.setFreezeColumns(source.getFreezeColumns());
        target.setMergeRanges(source.getMergeRanges());
        target.setAutoWidth(source.getAutoWidth());
        target.setEmptyResultBehavior(source.getEmptyResultBehavior());
        target.setFieldBindings(bindings == null ? source.getFieldBindings() : new ArrayList<>(bindings));
        return target;
    }

    private static List<Map<String, Object>> selectRows(List<List<Object>> rows, List<String> columns,
                                                          List<String> selectedKeys) {
        List<String> safeColumns = columns == null ? List.of() : columns;
        if (selectedKeys == null || selectedKeys.isEmpty()) {
            return rows.stream().map(row -> rowToMap(row, safeColumns)).toList();
        }
        return rows.stream().map(row -> rowToMap(row, safeColumns))
                .filter(row -> selectedKeys.contains(String.valueOf(row.get("__row_key")))).toList();
    }

    private static Map<String, Object> rowToMap(List<Object> row, List<String> columns) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < row.size(); i++) {
            result.put(i < columns.size() ? columns.get(i) : String.valueOf(i), row.get(i));
        }
        return result;
    }

    private static List<ViewFilter> mergeFilters(List<ViewFilter> preset, List<ViewFilter> rowFilter,
                                                  List<ViewFilter> runtime) {
        Map<String, ViewFilter> merged = new LinkedHashMap<>();
        List<List<ViewFilter>> filterGroups = List.of(preset == null ? List.<ViewFilter>of() : preset,
                rowFilter == null ? List.<ViewFilter>of() : rowFilter,
                runtime == null ? List.<ViewFilter>of() : runtime);
        for (List<ViewFilter> filters : filterGroups) {
            for (ViewFilter filter : filters) {
                if (filter != null && filter.getFieldId() != null) {
                    merged.put(filter.getFieldId(), filter);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private void evictExpiredTokens(long now) {
        tokenExpiryStore.forEach((token, expiresAt) -> {
            if (expiresAt < now) {
                removeToken(token);
            }
        });
    }

    private void removeToken(String token) {
        downloadStore.remove(token);
        tokenWorkspaceStore.remove(token);
        tokenExpiryStore.remove(token);
    }

    private static QueryExcelException tokenNotFound() {
        return new QueryExcelException(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(),
                ErrorCode.EX_TEMPLATE_NOT_FOUND.getMessage());
    }

    private static QueryExcelException invalidVersion() {
        return new QueryExcelException(ErrorCode.EX_REPORT_VERSION_INVALID.getCode(),
                ErrorCode.EX_REPORT_VERSION_INVALID.getMessage());
    }
}