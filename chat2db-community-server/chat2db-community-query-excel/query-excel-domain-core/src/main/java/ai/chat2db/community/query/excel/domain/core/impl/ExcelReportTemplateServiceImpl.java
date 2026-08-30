package ai.chat2db.community.query.excel.domain.core.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import ai.chat2db.community.domain.api.converter.LocalStorageConverter;
import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.QueryExcelConstants;
import ai.chat2db.community.query.excel.domain.api.enums.EmptyResultBehavior;
import ai.chat2db.community.query.excel.domain.api.enums.RowExpansionMode;
import ai.chat2db.community.query.excel.domain.api.enums.TemplateStatus;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.SheetConfig;
import ai.chat2db.community.query.excel.domain.api.service.IExcelReportTemplateService;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import ai.chat2db.community.query.excel.domain.core.util.ExcelTemplateFileUtil;
import ai.chat2db.community.query.excel.storage.ExcelReportTemplateStorage;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * File-backed {@link IExcelReportTemplateService} implementation
 * (requirements §8.1-8.3, §8.11).
 * <p>Template metadata is persisted via {@link ExcelReportTemplateStorage};
 * the uploaded .xlsx file itself is stored under
 * {@code {ConfigUtils.getEnvBasePath()}/excel-templates/{id}.xlsx}.</p>
 */
@Service
public class ExcelReportTemplateServiceImpl implements IExcelReportTemplateService {

    /**
     * Persistence delegate. Package-private so tests can substitute a mock.
     */
    ExcelReportTemplateStorage storage = ExcelReportTemplateStorage.INSTANCE;

/**
     * Query view service used to validate the {@code queryViewId} reference on upload.
     */
    private final ISavedQueryViewService savedQueryViewService;

    /**
     * Dataset service used to resolve dataset fields when validating field bindings.
     */
    private final IQueryDatasetService queryDatasetService;

    /**
     * Default wiring: file storage backed by an in-memory collection and a
     * real {@link SavedQueryViewServiceImpl} for view existence checks.
     */
    public ExcelReportTemplateServiceImpl() {
        this(ExcelReportTemplateStorage.INSTANCE, new SavedQueryViewServiceImpl(), new QueryDatasetServiceImpl());
    }

    /**
     * Injectable constructor for tests.
     *
     * @param storage               persistence delegate
     * @param savedQueryViewService query view service for reference validation
     */
    public ExcelReportTemplateServiceImpl(ExcelReportTemplateStorage storage,
                                          ISavedQueryViewService savedQueryViewService) {
        this(storage, savedQueryViewService, new QueryDatasetServiceImpl());
    }

    /**
     * Fully injectable constructor for tests and advanced wiring.
     *
     * @param storage               persistence delegate
     * @param savedQueryViewService query view service for reference validation
     * @param queryDatasetService   dataset service for field binding validation
     */
    ExcelReportTemplateServiceImpl(ExcelReportTemplateStorage storage,
                                   ISavedQueryViewService savedQueryViewService,
                                   IQueryDatasetService queryDatasetService) {
        this.storage = storage;
        this.savedQueryViewService = savedQueryViewService;
        this.queryDatasetService = queryDatasetService;
    }

    @Override
    public PageResponse<ExcelReportTemplate> list(Long workspaceId, int pageNo, int pageSize, String searchKey) {
        int safePageNo = pageNo <= 0 ? 1 : pageNo;
        int safePageSize = pageSize <= 0 ? QueryExcelConstants.DEFAULT_PAGE_SIZE : pageSize;

        List<ExcelReportTemplate> filtered = storage.getDataList().stream()
                .filter(t -> workspaceId == null || Objects.equals(workspaceId, t.getWorkspaceId()))
                .filter(t -> isBlank(searchKey) || (t.getName() != null
                        && t.getName().toLowerCase(Locale.ROOT).contains(searchKey.toLowerCase(Locale.ROOT))))
                .collect(Collectors.toList());

        long total = filtered.size();
        int from = Math.min((safePageNo - 1) * safePageSize, filtered.size());
        int to = Math.min(from + safePageSize, filtered.size());
        return PageResponse.of(new ArrayList<>(filtered.subList(from, to)), total, safePageNo, safePageSize);
    }

    @Override
    public ExcelReportTemplate getById(Long id) {
        return storage.getById(id);
    }

    @Override
    public Long create(ExcelReportTemplate template) {
        if (template == null || isBlank(template.getName())) {
            throw ex(ErrorCode.EX_TEMPLATE_NOT_FOUND);
        }
        Date now = new Date();
        template.setTemplateVersion(1);
        template.setStatus(isBlank(template.getTemplateFile())
                ? TemplateStatus.VALID.name()
                : TemplateStatus.VALID.name());
        template.setGmtCreate(now);
        template.setGmtModified(now);
        return storage.save(template);
    }

    @Override
    public void update(ExcelReportTemplate template) {
        ExcelReportTemplate existing = requireById(template == null ? null : template.getId());
        // Optimistic locking: reject writes based on a stale version.
        if (!Objects.equals(existing.getTemplateVersion(), template.getTemplateVersion())) {
            throw ex(ErrorCode.EX_TEMPLATE_NOT_FOUND);
        }
        ExcelReportTemplate merged = LocalStorageConverter.mergeNotNullProperties(existing, template);
        merged.setGmtModified(new Date());
        storage.update(merged);
    }

    @Override
    public void delete(Long id) {
        ExcelReportTemplate template = requireById(id);
        storage.delete(id);
        ExcelTemplateFileUtil.getTemplateFile(id).delete();
    }

    @Override
    public List<ErrorCode> validate(Long id) {
        ExcelReportTemplate template = requireById(id);
        List<ErrorCode> errors = new ArrayList<>();
        if (isBlank(template.getTemplateFile()) || !new File(template.getTemplateFile()).exists()) {
            errors.add(ErrorCode.EX_TEMPLATE_NOT_FOUND);
            return errors;
        }
        List<String> sheetNames;
        try {
            sheetNames = readSheetNames(template.getTemplateFile());
        } catch (IOException e) {
            errors.add(ErrorCode.EX_CORRUPTED_TEMPLATE);
            return errors;
        }
        if (sheetNames.isEmpty()) {
            errors.add(ErrorCode.EX_CORRUPTED_TEMPLATE);
        }
        if (!TemplateStatus.VALID.name().equals(template.getStatus())) {
            errors.add(ErrorCode.EX_CORRUPTED_TEMPLATE);
        }
        return errors;
    }

    @Override
    public Long copy(Long id, String newName) {
        ExcelReportTemplate original = requireById(id);
        ExcelReportTemplate copy = deepCopy(original);
        copy.setId(null);
        copy.setName(isBlank(newName) ? "Copy of " + original.getName() : newName);
        copy.setTemplateVersion(1);
        copy.setStatus(TemplateStatus.VALID.name());
        copy.setGmtCreate(new Date());
        copy.setGmtModified(new Date());
        return storage.save(copy);
    }

    @Override
    public Long upload(Long workspaceId, String name, String description, byte[] fileContent, Long queryViewId) {
        if (fileContent == null || fileContent.length == 0) {
            throw ex(ErrorCode.EX_INVALID_FILE_FORMAT);
        }
        // .xlsx magic bytes: PK\x03\x04 (ZIP container)
        if (fileContent.length < 4
                || fileContent[0] != 'P' || fileContent[1] != 'K'
                || fileContent[2] != 0x03 || fileContent[3] != 0x04) {
            throw ex(ErrorCode.EX_INVALID_FILE_FORMAT);
        }
        // Must also be a structurally valid xlsx workbook.
        List<String> sheetNames;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileContent))) {
            sheetNames = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheetNames.add(workbook.getSheetName(i));
            }
        } catch (Exception e) {
            throw ex(ErrorCode.EX_CORRUPTED_TEMPLATE);
        }

        // queryViewId must reference an existing saved query view.
        if (queryViewId == null || savedQueryViewService.getById(queryViewId) == null) {
            throw ex(ErrorCode.EX_BINDING_FIELD_NOT_FOUND);
        }

        ExcelReportTemplate template = new ExcelReportTemplate();
        Long id = storage.save(template);

        File target = ExcelTemplateFileUtil.getTemplateFile(id);
        String fileHash = writeFileAtomic(fileContent, target);
        if (fileHash == null) {
            storage.delete(id);
            throw ex(ErrorCode.EX_INVALID_FILE_FORMAT);
        }

        template.setWorkspaceId(workspaceId);
        template.setName(name);
        template.setDescription(description);
        template.setTemplateFile(target.getAbsolutePath());
        template.setFileHash(fileHash);
        template.setTemplateVersion(1);
        template.setQueryViewId(queryViewId);
        template.setSheetConfigs(toDefaultSheetConfigs(sheetNames));
        template.setStatus(TemplateStatus.VALID.name());
        Date now = new Date();
        template.setGmtCreate(now);
        template.setGmtModified(now);

        try {
            storage.update(template);
        } catch (Exception e) {
            target.delete();
            throw e;
        }
        return id;
    }

    @Override
    public List<String> getSheetNames(Long templateId) {
        ExcelReportTemplate template = requireById(templateId);
        if (isBlank(template.getTemplateFile())) {
            throw ex(ErrorCode.EX_TEMPLATE_NOT_FOUND);
        }
        try {
            return readSheetNames(template.getTemplateFile());
        } catch (IOException e) {
            throw ex(ErrorCode.EX_CORRUPTED_TEMPLATE);
        }
    }

    @Override
    public void updateSheetConfigs(Long templateId, List<SheetConfig> sheetConfigs) {
        ExcelReportTemplate template = requireById(templateId);
        List<String> actualSheetNames = getSheetNames(templateId);

        for (SheetConfig config : sheetConfigs) {
            if (config.getSheetName() == null || !actualSheetNames.contains(config.getSheetName())) {
                throw ex(ErrorCode.EX_SHEET_NOT_FOUND);
            }
            if (config.getDataStartRow() == null || config.getDataStartRow() < 0) {
                throw ex(ErrorCode.EX_CORRUPTED_TEMPLATE);
            }
            if (config.getDataStartColumn() == null || config.getDataStartColumn() < 0) {
                throw ex(ErrorCode.EX_CORRUPTED_TEMPLATE);
            }
            if (config.getFieldBindings() != null) {
                for (ExcelColumnBinding binding : config.getFieldBindings()) {
                    validateBinding(binding, template);
                }
            }
        }

        template.setSheetConfigs(sheetConfigs);
        template.setGmtModified(new Date());
        storage.update(template);
    }

    @Override
    public void updateFieldBindings(Long templateId, String sheetName, List<ExcelColumnBinding> bindings) {
        ExcelReportTemplate template = requireById(templateId);
        List<String> actualSheetNames = getSheetNames(templateId);

        if (sheetName == null || !actualSheetNames.contains(sheetName)) {
            throw ex(ErrorCode.EX_SHEET_NOT_FOUND);
        }

        for (ExcelColumnBinding binding : bindings) {
            validateBinding(binding, template);
        }

        List<SheetConfig> configs = template.getSheetConfigs();
        if (configs == null) {
            throw ex(ErrorCode.EX_SHEET_NOT_FOUND);
        }
        SheetConfig target = configs.stream()
                .filter(c -> sheetName.equals(c.getSheetName()))
                .findFirst()
                .orElseThrow(() -> ex(ErrorCode.EX_SHEET_NOT_FOUND));

        target.setFieldBindings(bindings);
        template.setGmtModified(new Date());
        storage.update(template);
    }

    // ── helpers ──────────────────────────────────────────────────

    /**
     * Validates a single field binding:
     * <ul>
     *   <li>{@code queryFieldId} must not be blank</li>
     *   <li>{@code targetColumn} must be a valid Excel column letter ({@code [A-Za-z]+})</li>
     *   <li>{@code queryFieldId} must exist in the template's query view dataset</li>
     * </ul>
     */
    private void validateBinding(ExcelColumnBinding binding, ExcelReportTemplate template) {
        if (binding == null || isBlank(binding.getQueryFieldId())
                || isBlank(binding.getTargetColumn())) {
            throw ex(ErrorCode.EX_BINDING_FIELD_NOT_FOUND);
        }
        if (!binding.getTargetColumn().matches("[A-Za-z]+")) {
            throw ex(ErrorCode.EX_BINDING_FIELD_NOT_FOUND);
        }
        List<String> fieldIds = getDatasetFieldIds(template.getQueryViewId());
        if (!fieldIds.contains(binding.getQueryFieldId())) {
            throw ex(ErrorCode.EX_BINDING_FIELD_NOT_FOUND);
        }
    }

    /**
     * Returns the set of field IDs from the dataset attached to the given query view.
     */
    private List<String> getDatasetFieldIds(Long queryViewId) {
        SavedQueryView view = savedQueryViewService.getById(queryViewId);
        if (view == null || view.getDatasetId() == null) {
            return Collections.emptyList();
        }
        QueryDataset dataset = queryDatasetService.getById(view.getDatasetId());
        if (dataset == null || dataset.getFields() == null) {
            return Collections.emptyList();
        }
        return dataset.getFields().stream()
                .map(QueryDatasetField::getFieldId)
                .collect(Collectors.toList());
    }

    private ExcelReportTemplate requireById(Long id) {
        ExcelReportTemplate template = storage.getById(id);
        if (template == null) {
            throw ex(ErrorCode.EX_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private static QueryExcelException ex(ErrorCode errorCode) {
        return new QueryExcelException(errorCode.getCode(), errorCode.getMessage());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Reads the sheet names of a stored .xlsx file via POI.
     */
    private static List<String> readSheetNames(String filePath) throws IOException {
        try (InputStream in = new FileInputStream(filePath); Workbook workbook = new XSSFWorkbook(in)) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                names.add(workbook.getSheetName(i));
            }
            return names;
        }
    }

    /**
     * Builds default {@link SheetConfig}s for the freshly uploaded workbook:
     * data starts at row/column 0, rows expand by insertion, an empty query
     * result keeps an empty sheet, and no field bindings are configured yet
     * (bindings are a later wave, T14+).
     */
    private static List<SheetConfig> toDefaultSheetConfigs(List<String> sheetNames) {
        List<SheetConfig> configs = new ArrayList<>();
        for (String sheetName : sheetNames) {
            SheetConfig config = new SheetConfig();
            config.setSheetName(sheetName);
            config.setDataStartRow(0);
            config.setDataStartColumn(0);
            config.setRowExpansionMode(RowExpansionMode.INSERT.name());
            config.setEmptyResultBehavior(EmptyResultBehavior.EMPTY_SHEET.name());
            config.setFieldBindings(Collections.emptyList());
            configs.add(config);
        }
        return configs;
    }

    /**
     * Writes the uploaded bytes to the target file and returns the SHA-256
     * hex digest, or {@code null} when the write failed.
     */
    private static String writeFileAtomic(byte[] fileContent, File target) {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(fileContent);
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(fileContent);
            }
            return hex(hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            target.delete();
            return null;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * Deep copy preserving every property except {@code id}.
     */
    private static ExcelReportTemplate deepCopy(ExcelReportTemplate src) {
        ExcelReportTemplate copy = new ExcelReportTemplate();
        copy.setWorkspaceId(src.getWorkspaceId());
        copy.setName(src.getName());
        copy.setDescription(src.getDescription());
        copy.setTemplateFile(src.getTemplateFile());
        copy.setFileHash(src.getFileHash());
        copy.setTemplateVersion(src.getTemplateVersion());
        copy.setQueryViewId(src.getQueryViewId());
        copy.setSheetConfigs(src.getSheetConfigs() == null ? null
                : src.getSheetConfigs().stream()
                        .map(ExcelReportTemplateServiceImpl::copySheetConfig)
                        .collect(Collectors.toList()));
        copy.setStatus(src.getStatus());
        copy.setOwnerId(src.getOwnerId());
        copy.setGmtCreate(src.getGmtCreate());
        copy.setGmtModified(src.getGmtModified());
        return copy;
    }

    private static SheetConfig copySheetConfig(SheetConfig src) {
        SheetConfig copy = new SheetConfig();
        copy.setSheetName(src.getSheetName());
        copy.setDataStartRow(src.getDataStartRow());
        copy.setDataStartColumn(src.getDataStartColumn());
        copy.setHeaderMapping(src.getHeaderMapping());
        copy.setRowExpansionMode(src.getRowExpansionMode());
        copy.setFreezeRows(src.getFreezeRows());
        copy.setFreezeColumns(src.getFreezeColumns());
        copy.setMergeRanges(src.getMergeRanges() == null ? null
                : src.getMergeRanges().stream()
                        .map(r -> {
                            ai.chat2db.community.query.excel.domain.api.model.MergeRange m =
                                    new ai.chat2db.community.query.excel.domain.api.model.MergeRange();
                            m.setStartRow(r.getStartRow());
                            m.setEndRow(r.getEndRow());
                            m.setStartColumn(r.getStartColumn());
                            m.setEndColumn(r.getEndColumn());
                            return m;
                        })
                        .collect(Collectors.toList()));
        copy.setAutoWidth(src.getAutoWidth());
        copy.setEmptyResultBehavior(src.getEmptyResultBehavior());
        copy.setFieldBindings(src.getFieldBindings() == null ? null
                : src.getFieldBindings().stream()
                        .map(b -> {
                            ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding c =
                                    new ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding();
                            c.setQueryFieldId(b.getQueryFieldId());
                            c.setTargetColumn(b.getTargetColumn());
                            c.setDisplayName(b.getDisplayName());
                            c.setNumberFormat(b.getNumberFormat());
                            c.setNullDisplay(b.getNullDisplay());
                            c.setAlignment(b.getAlignment());
                            c.setExportEnabled(b.getExportEnabled());
                            return c;
                        })
                        .collect(Collectors.toList()));
        return copy;
    }
}