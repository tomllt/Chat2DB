package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
import ai.chat2db.community.query.excel.domain.api.permission.QueryExcelPermissionChecker;
import ai.chat2db.community.query.excel.domain.api.service.IExcelRenderService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelReportTemplateService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelTemplateValidationService;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import ai.chat2db.community.query.excel.domain.core.util.ExcelTemplateFileUtil;
import ai.chat2db.community.query.excel.storage.ExcelExportRecordStorage;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for {@link ExcelExportServiceImpl} (requirements §8.10, §8.11, §9.2, §12.3).
 * <p>All dependencies are mocked; the template file is backed by a real .xlsx
 * written via {@link ExcelTemplateFileUtil} so the file-existence contract is
 * exercised.</p>
 */
class ExcelExportServiceImplTest {

    /** Fixed template ID used by all tests that need a valid on-disk file. */
    private static final long TEMPLATE_ID = 10L;

    private IExcelReportTemplateService templateService;
    private ISavedQueryViewService savedQueryViewService;
    private IQueryDatasetService queryDatasetService;
    private IExcelRenderService renderService;
    private IExcelTemplateValidationService templateValidationService;
    private QueryExcelPermissionChecker permissionChecker;
    private ExcelExportRecordStorage storage;
    private ExcelExportServiceImpl service;

    private final List<Long> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        templateService = mock(IExcelReportTemplateService.class);
        savedQueryViewService = mock(ISavedQueryViewService.class);
        queryDatasetService = mock(IQueryDatasetService.class);
        renderService = mock(IExcelRenderService.class);
        templateValidationService = mock(IExcelTemplateValidationService.class);
        permissionChecker = mock(QueryExcelPermissionChecker.class);
        storage = mock(ExcelExportRecordStorage.class);
        when(storage.save(any(ExcelExportRecord.class))).thenAnswer(inv -> {
            ExcelExportRecord r = inv.getArgument(0);
            r.setId(1L);
            return 1L;
        });
        service = new ExcelExportServiceImpl(templateService, savedQueryViewService,
                renderService, templateValidationService, permissionChecker, storage, queryDatasetService);
        // Community mode: grant everything by default, tests override as needed.
        when(permissionChecker.canExecuteView(any(), anyLong())).thenReturn(true);
        when(permissionChecker.canViewDataset(any(), anyLong())).thenReturn(true);
        when(permissionChecker.canAccessDatasource(any(), anyLong())).thenReturn(true);
        when(permissionChecker.canAccessField(any(), anyString())).thenReturn(true);
        when(permissionChecker.canExportTemplate(any(), anyLong())).thenReturn(true);
    }

    @AfterEach
    void cleanUpFiles() {
        for (Long id : createdIds) {
            if (id != null) {
                File f = ExcelTemplateFileUtil.getTemplateFile(id);
                if (f.exists()) {
                    f.delete();
                }
            }
        }
        createdIds.clear();
    }

    // ── export: success path ─────────────────────────────────────

    @Test
    void exportValidReturnsResultWithToken() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        SavedQueryView view = viewWithDataset();
        when(savedQueryViewService.getById(100L)).thenReturn(view);
        QueryResult result = QueryResult.builder()
                .columns(Arrays.asList("name", "amount"))
                .rows(Arrays.asList(
                        Arrays.asList("Alice", 100),
                        Arrays.asList("Bob", 200)))
                .total(2)
                .build();
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(result);
        byte[] xlsx = xlsxBytes("Data");
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(xlsx);

        ExportResult export = service.export(TEMPLATE_ID, 100L, null);

        assertNotNull(export.getDownloadToken());
        assertEquals(2, export.getRowCount());
        assertEquals(xlsx.length, export.getFileSize());
        assertEquals(ExportStatus.SUCCESS.name(), export.getStatus());
        assertNotNull(export.getExportId());

        // audit record persisted with the full field set
        ArgumentCaptor<ExcelExportRecord> recordCaptor = ArgumentCaptor.forClass(ExcelExportRecord.class);
        verify(storage).update(recordCaptor.capture());
        ExcelExportRecord record = recordCaptor.getValue();
        assertEquals(ExportStatus.SUCCESS.name(), record.getStatus());
        assertEquals(TEMPLATE_ID, record.getTemplateId());
        assertEquals(100L, record.getQueryViewId());
        assertEquals(2, record.getRowCount());
        assertEquals(xlsx.length, record.getFileSize());
        assertNotNull(record.getQueryMs());
        assertNotNull(record.getPermissionResult());
        assertNotNull(record.getExportedAt());
        assertEquals(export.getDownloadToken(), record.getDownloadToken());
        assertNotNull(record.getDownloadTokenExpiresAt());
        assertTrue(record.getDownloadTokenExpiresAt().after(new Date()));
    }

    @Test
    void exportRunsQueryExactlyOnceForAllSheets() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        QueryResult result = QueryResult.builder()
                .columns(Arrays.asList("name", "amount"))
                .rows(Collections.singletonList(Arrays.asList("Alice", 100)))
                .total(1)
                .build();
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(result);
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(xlsxBytes("Data"));

        service.export(TEMPLATE_ID, 100L, null);

        verify(savedQueryViewService, times(1)).executeQuery(eq(100L), anyList());
    }

    // ── export: template failure paths ───────────────────────────

    @Test
    void exportWithMissingTemplateThrowsNotFound() {
        when(templateService.getById(999L)).thenReturn(null);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(999L, 100L, null));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWithNoTemplateFileThrowsCorrupted() {
        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(42L);
        template.setStatus(TemplateStatus.VALID.name());
        template.setTemplateFile("/nonexistent/path/42.xlsx");
        when(templateService.getById(42L)).thenReturn(template);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(42L, 100L, null));
        assertEquals(ErrorCode.EX_CORRUPTED_TEMPLATE.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWithInvalidTemplateStatusThrowsNotFound() {
        ExcelReportTemplate template = validTemplate();
        template.setStatus(TemplateStatus.INVALID.name());
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWithFatalValidationErrorThrowsCorrupted() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        ValidationError fatal = ValidationError.builder()
                .errorCode(ErrorCode.EX_SHEET_NOT_FOUND.getCode())
                .message("sheet missing")
                .build();
        when(templateValidationService.validateTemplate(TEMPLATE_ID))
                .thenReturn(Collections.singletonList(fatal));

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_CORRUPTED_TEMPLATE.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWithOnlyWarningValidationErrorProceeds() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        ValidationError warning = ValidationError.builder()
                .errorCode(ErrorCode.EX_FONT_FALLBACK.getCode())
                .message("font fallback")
                .warning(true)
                .build();
        when(templateValidationService.validateTemplate(TEMPLATE_ID))
                .thenReturn(Collections.singletonList(warning));
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(singleRowResult());
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(xlsxBytes("Data"));

        ExportResult export = service.export(TEMPLATE_ID, 100L, null);

        assertNotNull(export.getDownloadToken());
        assertEquals(ExportStatus.SUCCESS.name(), export.getStatus());
    }

    // ── export: query failure paths ──────────────────────────────

    @Test
    void exportWithQueryTimeoutThrowsQueryTimeout() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.executeQuery(eq(100L), anyList()))
                .thenThrow(new QueryExcelException(ErrorCode.EX_QUERY_TIMEOUT.getCode(),
                        ErrorCode.EX_QUERY_TIMEOUT.getMessage()));

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_QUERY_TIMEOUT.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWithEmptyResultRendersEmptySheets() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        QueryResult empty = QueryResult.builder()
                .columns(Arrays.asList("name"))
                .rows(Collections.emptyList())
                .total(0)
                .build();
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(empty);
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(xlsxBytes("Data"));

        ExportResult export = service.export(TEMPLATE_ID, 100L, null);

        assertEquals(0, export.getRowCount());
        assertEquals(ExportStatus.SUCCESS.name(), export.getStatus());
        assertNotNull(export.getDownloadToken());
    }

    // ── export: row limit ────────────────────────────────────────

    @Test
    void exportWithRowLimitExceededThrowsRowLimit() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        List<List<Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < QueryExcelConstants.MAX_EXPORT_ROWS + 1; i++) {
            tooMany.add(Collections.singletonList("r" + i));
        }
        QueryResult result = QueryResult.builder()
                .columns(Collections.singletonList("name"))
                .rows(tooMany)
                .total(tooMany.size())
                .build();
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(result);
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(xlsxBytes("Data"));

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_ROW_LIMIT_EXCEEDED.getCode(), ex.getErrorCode());
    }

    // ── export: permission failures (§9.2) ───────────────────────

    @Test
    void exportWhenExecuteViewDeniedThrowsNoPermission() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        // Mock query to succeed (permission check happens after query)
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(singleRowResult());
        when(permissionChecker.canExecuteView(any(), anyLong())).thenReturn(false);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_NO_PERMISSION.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWhenDatasetAccessDeniedThrowsNoPermission() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        SavedQueryView view = viewWithDataset();
        when(savedQueryViewService.getById(100L)).thenReturn(view);
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(singleRowResult());
        when(permissionChecker.canViewDataset(any(), anyLong())).thenReturn(false);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_NO_PERMISSION.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWhenDatasourceAccessDeniedThrowsNoPermission() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(singleRowResult());
        when(queryDatasetService.getById(7L)).thenReturn(datasetWithDatasource());
        when(permissionChecker.canAccessDatasource(any(), anyLong())).thenReturn(false);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_NO_PERMISSION.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWhenTemplateExportDeniedThrowsNoPermission() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(singleRowResult());
        when(permissionChecker.canExportTemplate(any(), anyLong())).thenReturn(false);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_NO_PERMISSION.getCode(), ex.getErrorCode());
    }

    // ── export: sensitive field masking (§8.11) ──────────────────

    @Test
    void exportMasksSensitiveFieldWithThreeAsterisks() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        QueryResult result = QueryResult.builder()
                .columns(Arrays.asList("salary", "name"))
                .rows(Collections.singletonList(Arrays.asList(50000, "Alice")))
                .total(1)
                .build();
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(result);
        // "salary" is sensitive → masked, "name" is accessible → intact
        when(permissionChecker.canAccessField(any(), eq("salary"))).thenReturn(false);
        when(permissionChecker.canAccessField(any(), eq("name"))).thenReturn(true);
        byte[] captured = xlsxBytes("Data");
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(captured);

        ExportResult export = service.export(TEMPLATE_ID, 100L, null);

        assertNotNull(export.getDownloadToken());
        ArgumentCaptor<List<List<Object>>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(renderService).render(eq(template), rowsCaptor.capture(), anyList());
        List<Object> maskedRow = rowsCaptor.getValue().get(0);
        assertEquals(ExcelExportServiceImpl.SENSITIVE_FIELD_MASK, maskedRow.get(0));
        assertEquals("Alice", maskedRow.get(1));
    }

    @Test
    void exportLeavesAccessibleFieldsUntouched() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        QueryResult result = QueryResult.builder()
                .columns(Collections.singletonList("name"))
                .rows(Collections.singletonList(Collections.singletonList("Alice")))
                .total(1)
                .build();
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(result);
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(xlsxBytes("Data"));

        service.export(TEMPLATE_ID, 100L, null);

        ArgumentCaptor<List<List<Object>>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(renderService).render(eq(template), rowsCaptor.capture(), anyList());
        assertEquals("Alice", rowsCaptor.getValue().get(0).get(0));
        // canAccessField must have been consulted for the single column
        verify(permissionChecker).canAccessField(any(), eq("name"));
    }

    // ── export: audit record on failure ──────────────────────────

    @Test
    void exportRecordsFailureStatusOnError() {
        when(templateService.getById(999L)).thenReturn(null);

        assertThrows(QueryExcelException.class, () -> service.export(999L, 100L, null));

        ArgumentCaptor<ExcelExportRecord> recordCaptor = ArgumentCaptor.forClass(ExcelExportRecord.class);
        verify(storage).update(recordCaptor.capture());
        assertEquals(ExportStatus.FAILED.name(), recordCaptor.getValue().getStatus());
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), recordCaptor.getValue().getErrorCode());
    }

    // ── download: success and failure paths ──────────────────────

    @Test
    void downloadWithValidTokenReturnsBytes() {
        ExcelExportRecord record = successRecord("token-1");
        ExcelExportRecord stored = cloneRecord(record);
        when(storage.getDataList()).thenReturn(Collections.singletonList(stored));

        byte[] bytes = xlsxBytes("Data");
        injectDownload("token-1", bytes);

        byte[] downloaded = service.download("token-1");

        assertArrayEquals(bytes, downloaded);
    }

    @Test
    void downloadWithUnknownTokenThrowsNotFound() {
        when(storage.getDataList()).thenReturn(Collections.emptyList());

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.download("no-such-token"));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    @Test
    void downloadWithExpiredTokenThrowsNotFound() {
        ExcelExportRecord stored = cloneRecord(successRecord("token-expired"));
        stored.setDownloadTokenExpiresAt(new Date(System.currentTimeMillis() - 60_000L));
        when(storage.getDataList()).thenReturn(Collections.singletonList(stored));
        injectDownload("token-expired", xlsxBytes("Data"));

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.download("token-expired"));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    @Test
    void downloadWithNullTokenThrowsNotFound() {
        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.download(null));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    @Test
    void downloadWithBlankTokenThrowsNotFound() {
        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.download("  "));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    // ── export: limits (§12.2) ───────────────────────────────────

    @Test
    void exportWithFileSizeLimitExceededThrowsFileSizeLimit() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(singleRowResult());
        byte[] huge = new byte[(int) (QueryExcelConstants.MAX_FILE_SIZE_BYTES + 1)];
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(huge);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_FILE_SIZE_EXCEEDED.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWithMaxSheetsExceededThrowsError() {
        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(TEMPLATE_ID);
        template.setName("Max Sheets Template");
        template.setStatus(TemplateStatus.VALID.name());
        File file = ExcelTemplateFileUtil.getTemplateFile(TEMPLATE_ID);
        try {
            Files.write(file.toPath(), xlsxBytes("Sheet1"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        createdIds.add(TEMPLATE_ID);
        List<SheetConfig> sheets = new ArrayList<>();
        for (int i = 0; i < QueryExcelConstants.MAX_SHEETS + 1; i++) {
            SheetConfig config = new SheetConfig();
            config.setSheetName("Sheet" + i);
            config.setDataStartRow(0);
            config.setDataStartColumn(0);
            config.setFieldBindings(new ArrayList<>());
            sheets.add(config);
        }
        template.setSheetConfigs(sheets);
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_FILE_SIZE_EXCEEDED.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWithMaxBindingsPerSheetExceededThrowsError() {
        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(TEMPLATE_ID);
        template.setName("Bindings Test");
        template.setStatus(TemplateStatus.VALID.name());
        File file = ExcelTemplateFileUtil.getTemplateFile(TEMPLATE_ID);
        try {
            Files.write(file.toPath(), xlsxBytes("Data"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        createdIds.add(TEMPLATE_ID);
        SheetConfig config = new SheetConfig();
        config.setSheetName("Data");
        config.setDataStartRow(0);
        config.setDataStartColumn(0);
        List<ExcelColumnBinding> manyBindings = new ArrayList<>();
        for (int i = 0; i < QueryExcelConstants.MAX_BINDINGS_PER_SHEET + 1; i++) {
            ExcelColumnBinding b = new ExcelColumnBinding();
            b.setQueryFieldId("field" + i);
            b.setTargetColumn("A");
            manyBindings.add(b);
        }
        config.setFieldBindings(manyBindings);
        template.setSheetConfigs(Collections.singletonList(config));
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_FILE_SIZE_EXCEEDED.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWithMaxRowsPerSheetExceededThrowsRowLimit() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        List<List<Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < QueryExcelConstants.MAX_ROWS_PER_SHEET + 1; i++) {
            tooMany.add(Collections.singletonList("r" + i));
        }
        QueryResult result = QueryResult.builder()
                .columns(Collections.singletonList("name"))
                .rows(tooMany)
                .total(tooMany.size())
                .build();
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(result);
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(xlsxBytes("Data"));

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_ROW_LIMIT_EXCEEDED.getCode(), ex.getErrorCode());
    }

    // ── export: audit record (§12.4) ─────────────────────────────

    @Test
    void exportPopulatesAllSection124AuditFields() {
        ExcelReportTemplate template = validTemplate();
        template.setTemplateVersion(3);
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        SavedQueryView view = new SavedQueryView();
        view.setId(100L);
        view.setDatasetId(7L);
        view.setDatasetVersion(2);
        view.setVersion(5);
        view.setWorkspaceId(42L);
        view.setOwnerId(5L);
        when(savedQueryViewService.getById(100L)).thenReturn(view);
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(singleRowResult());
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(xlsxBytes("Data"));

        service.export(TEMPLATE_ID, 100L, null);

        ArgumentCaptor<ExcelExportRecord> recordCaptor = ArgumentCaptor.forClass(ExcelExportRecord.class);
        verify(storage).update(recordCaptor.capture());
        ExcelExportRecord record = recordCaptor.getValue();
        assertNotNull(record.getQueryId());
        assertEquals(5L, record.getUserId().longValue());
        assertEquals(42L, record.getWorkspaceId().longValue());
        assertEquals(7L, record.getDatasetId().longValue());
        assertEquals(2, record.getDatasetVersion().intValue());
        assertEquals(5, record.getQueryViewVersion().intValue());
        assertEquals(3, record.getTemplateVersion().intValue());
        assertNotNull(record.getQueryMs());
        assertEquals(1, record.getRowCount().intValue());
        assertNotNull(record.getFileSize());
        assertEquals(ExportStatus.SUCCESS.name(), record.getStatus());
        assertNull(record.getErrorCode());
        assertNotNull(record.getPermissionResult());
        assertNotNull(record.getExportedAt());
    }

    @Test
    void exportAuditDoesNotContainCredentials() {
        ExcelReportTemplate template = validTemplate();
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        QueryResult result = QueryResult.builder()
                .columns(Arrays.asList("name", "password"))
                .rows(Collections.singletonList(Arrays.asList("Alice", "my-secret-password")))
                .total(1)
                .build();
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(result);
        when(permissionChecker.canAccessField(any(), anyString())).thenReturn(true);
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(xlsxBytes("Data"));

        service.export(TEMPLATE_ID, 100L, null);

        ArgumentCaptor<ExcelExportRecord> recordCaptor = ArgumentCaptor.forClass(ExcelExportRecord.class);
        verify(storage).update(recordCaptor.capture());
        ExcelExportRecord record = recordCaptor.getValue();
        assertFalse(record.toString().contains("my-secret-password"),
                "Audit record must not contain credential values");
    }

    // ── download: single-use token ───────────────────────────────

    @Test
    void downloadTokenDeletedAfterUse() {
        String token = "token-to-delete";
        ExcelExportRecord record = successRecord(token);
        ExcelExportRecord stored = cloneRecord(record);
        when(storage.getDataList()).thenReturn(Collections.singletonList(stored));
        injectDownload(token, xlsxBytes("Data"));

        service.download(token);

        ArgumentCaptor<ExcelExportRecord> recordCaptor = ArgumentCaptor.forClass(ExcelExportRecord.class);
        verify(storage).update(recordCaptor.capture());
        ExcelExportRecord updatedRecord = recordCaptor.getValue();
        assertNull(updatedRecord.getDownloadToken());
        assertNull(updatedRecord.getDownloadTokenExpiresAt());
    }

    @Test
    void downloadTokenRemovedFromStoreAfterDownload() {
        String token = "token-to-remove";
        ExcelExportRecord record = successRecord(token);
        ExcelExportRecord stored = cloneRecord(record);
        when(storage.getDataList()).thenReturn(Collections.singletonList(stored));
        injectDownload(token, xlsxBytes("Data"));

        service.download(token);

        try {
            java.lang.reflect.Field field = ExcelExportServiceImpl.class
                    .getDeclaredField("downloadStore");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, byte[]> store =
                    (java.util.Map<String, byte[]>) field.get(service);
            assertFalse(store.containsKey(token));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void exportWithBindingsExceededOnSecondSheetThrowsError() {
        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(TEMPLATE_ID);
        template.setName("Bindings Test");
        template.setStatus(TemplateStatus.VALID.name());
        File file = ExcelTemplateFileUtil.getTemplateFile(TEMPLATE_ID);
        try {
            Files.write(file.toPath(), xlsxBytes("Sheet1", "Sheet2"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        createdIds.add(TEMPLATE_ID);
        List<SheetConfig> sheets = new ArrayList<>();
        SheetConfig sheet1 = new SheetConfig();
        sheet1.setSheetName("Sheet1");
        sheet1.setDataStartRow(0);
        sheet1.setDataStartColumn(0);
        ExcelColumnBinding binding1 = new ExcelColumnBinding();
        binding1.setQueryFieldId("name");
        binding1.setTargetColumn("A");
        sheet1.setFieldBindings(Collections.singletonList(binding1));
        sheets.add(sheet1);
        SheetConfig sheet2 = new SheetConfig();
        sheet2.setSheetName("Sheet2");
        sheet2.setDataStartRow(0);
        sheet2.setDataStartColumn(0);
        List<ExcelColumnBinding> manyBindings = new ArrayList<>();
        for (int i = 0; i < QueryExcelConstants.MAX_BINDINGS_PER_SHEET + 1; i++) {
            ExcelColumnBinding b = new ExcelColumnBinding();
            b.setQueryFieldId("field" + i);
            b.setTargetColumn("A");
            manyBindings.add(b);
        }
        sheet2.setFieldBindings(manyBindings);
        sheets.add(sheet2);
        template.setSheetConfigs(sheets);
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_FILE_SIZE_EXCEEDED.getCode(), ex.getErrorCode());
    }

    @Test
    void exportWithFileSizeLimitExceededOnSecondRenderThrowsFileSizeLimit() {
        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(TEMPLATE_ID);
        template.setName("Render Size Test");
        template.setStatus(TemplateStatus.VALID.name());
        template.setQueryViewId(100L);
        File file = ExcelTemplateFileUtil.getTemplateFile(TEMPLATE_ID);
        try {
            Files.write(file.toPath(), xlsxBytes("Sheet1", "Sheet2"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        createdIds.add(TEMPLATE_ID);
        List<SheetConfig> sheets = new ArrayList<>();
        SheetConfig sheet1 = new SheetConfig();
        sheet1.setSheetName("Sheet1");
        sheet1.setDataStartRow(0);
        sheet1.setDataStartColumn(0);
        ExcelColumnBinding binding1 = new ExcelColumnBinding();
        binding1.setQueryFieldId("name");
        binding1.setTargetColumn("A");
        sheet1.setFieldBindings(Collections.singletonList(binding1));
        sheets.add(sheet1);
        SheetConfig sheet2 = new SheetConfig();
        sheet2.setSheetName("Sheet2");
        sheet2.setDataStartRow(0);
        sheet2.setDataStartColumn(0);
        ExcelColumnBinding binding2 = new ExcelColumnBinding();
        binding2.setQueryFieldId("name");
        binding2.setTargetColumn("A");
        sheet2.setFieldBindings(Collections.singletonList(binding2));
        sheets.add(sheet2);
        template.setSheetConfigs(sheets);
        when(templateService.getById(TEMPLATE_ID)).thenReturn(template);
        when(templateValidationService.validateTemplate(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(savedQueryViewService.getById(100L)).thenReturn(viewWithDataset());
        when(savedQueryViewService.executeQuery(eq(100L), anyList())).thenReturn(singleRowResult());
        byte[] huge = new byte[(int) (QueryExcelConstants.MAX_FILE_SIZE_BYTES + 1)];
        when(renderService.render(eq(template), anyList(), anyList())).thenReturn(huge);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.export(TEMPLATE_ID, 100L, null));
        assertEquals(ErrorCode.EX_FILE_SIZE_EXCEEDED.getCode(), ex.getErrorCode());
    }

    // ── helpers ──────────────────────────────────────────────────

    /**
     * Builds a template whose stored .xlsx file exists on disk at the path
     * {@link ExcelTemplateFileUtil#getTemplateFile(TEMPLATE_ID)}.
     */
    private ExcelReportTemplate validTemplate() {
        File file = ExcelTemplateFileUtil.getTemplateFile(TEMPLATE_ID);
        try {
            Files.write(file.toPath(), xlsxBytes("Data"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        createdIds.add(TEMPLATE_ID);

        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(TEMPLATE_ID);
        template.setName("Test Template");
        template.setStatus(TemplateStatus.VALID.name());
        template.setQueryViewId(100L);
        SheetConfig config = new SheetConfig();
        config.setSheetName("Data");
        config.setDataStartRow(0);
        config.setDataStartColumn(0);
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("name");
        binding.setTargetColumn("A");
        config.setFieldBindings(Collections.singletonList(binding));
        template.setSheetConfigs(Collections.singletonList(config));
        return template;
    }

    private static SavedQueryView viewWithDataset() {
        SavedQueryView view = new SavedQueryView();
        view.setId(100L);
        view.setDatasetId(7L);
        view.setOwnerId(5L);
        return view;
    }

    private static QueryDataset datasetWithDatasource() {
        QueryDataset dataset = new QueryDataset();
        dataset.setId(7L);
        dataset.setDatasourceId(3L);
        return dataset;
    }

    private static QueryResult singleRowResult() {
        return QueryResult.builder()
                .columns(Collections.singletonList("name"))
                .rows(Collections.singletonList(Collections.singletonList("Alice")))
                .total(1)
                .build();
    }

    private static ExcelExportRecord successRecord(String token) {
        ExcelExportRecord record = new ExcelExportRecord();
        record.setId(1L);
        record.setTemplateId(10L);
        record.setQueryViewId(100L);
        record.setStatus(ExportStatus.SUCCESS.name());
        record.setDownloadToken(token);
        record.setDownloadTokenExpiresAt(new Date(System.currentTimeMillis()
                + 3_600_000L));
        record.setRowCount(1);
        record.setFileSize(100L);
        return record;
    }

    private static ExcelExportRecord cloneRecord(ExcelExportRecord src) {
        ExcelExportRecord copy = new ExcelExportRecord();
        copy.setId(src.getId());
        copy.setTemplateId(src.getTemplateId());
        copy.setQueryViewId(src.getQueryViewId());
        copy.setStatus(src.getStatus());
        copy.setDownloadToken(src.getDownloadToken());
        copy.setDownloadTokenExpiresAt(src.getDownloadTokenExpiresAt());
        copy.setRowCount(src.getRowCount());
        copy.setFileSize(src.getFileSize());
        return copy;
    }

    /**
     * Injects the given bytes into the service's in-memory download store via
     * reflection so {@link #download(String)} can resolve them without running
     * a full export.
     */
    private void injectDownload(String token, byte[] bytes) {
        try {
            java.lang.reflect.Field field = ExcelExportServiceImpl.class
                    .getDeclaredField("downloadStore");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, byte[]> store =
                    (java.util.Map<String, byte[]>) field.get(service);
            store.put(token, bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Builds a real .xlsx byte array with the given sheet names via POI. */
    private static byte[] xlsxBytes(String... sheetNames) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            for (String name : sheetNames) {
                Sheet sheet = wb.createSheet(name);
                sheet.createRow(0).createCell(0).setCellValue("header");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}