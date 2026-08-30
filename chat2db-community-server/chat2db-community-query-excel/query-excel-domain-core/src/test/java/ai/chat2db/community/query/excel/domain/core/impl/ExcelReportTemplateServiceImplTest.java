package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
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
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import ai.chat2db.community.query.excel.domain.core.util.ExcelTemplateFileUtil;
import ai.chat2db.community.query.excel.storage.ExcelReportTemplateStorage;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ExcelReportTemplateServiceImplTest {

    private ExcelReportTemplateStorage storageMock;
    private ISavedQueryViewService viewServiceMock;
    private IQueryDatasetService datasetServiceMock;
    private ExcelReportTemplateServiceImpl service;

    /** Template ids created in this test run, for file cleanup. */
    private final List<Long> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        storageMock = mock(ExcelReportTemplateStorage.class);
        viewServiceMock = mock(ISavedQueryViewService.class);
        datasetServiceMock = mock(IQueryDatasetService.class);
        service = new ExcelReportTemplateServiceImpl(storageMock, viewServiceMock);
    }

    @AfterEach
    void cleanUpFiles() {
        for (Long id : createdIds) {
            if (id != null) {
                File file = ExcelTemplateFileUtil.getTemplateFile(id);
                if (file.exists()) {
                    file.delete();
                }
            }
        }
        createdIds.clear();
    }

    // ── upload ──────────────────────────────────────────────────

    @Test
    void uploadWithValidXlsxAssignsIdAndExtractsMetadata() {
        byte[] content = xlsxBytes("SheetA", "SheetB");
        when(viewServiceMock.getById(100L)).thenReturn(new SavedQueryView());
        when(storageMock.save(any(ExcelReportTemplate.class))).thenAnswer(inv -> {
            ExcelReportTemplate t = inv.getArgument(0);
            t.setId(42L);
            return 42L;
        });
        track(42L);

        Long id = service.upload(7L, "Report", "desc", content, 100L);

        assertEquals(42L, id);
        ArgumentCaptor<ExcelReportTemplate> captor = ArgumentCaptor.forClass(ExcelReportTemplate.class);
        Mockito.verify(storageMock).update(captor.capture());
        ExcelReportTemplate saved = captor.getValue();
        assertEquals(42L, saved.getId());
        assertEquals("Report", saved.getName());
        assertEquals(7L, saved.getWorkspaceId());
        assertEquals(100L, saved.getQueryViewId());
        assertEquals(TemplateStatus.VALID.name(), saved.getStatus());
        assertEquals(1, saved.getTemplateVersion().intValue());
        assertNotNull(saved.getFileHash());
        assertEquals(sha256(content), saved.getFileHash());

        // sheet configs
        List<SheetConfig> configs = saved.getSheetConfigs();
        assertEquals(2, configs.size());
        assertEquals("SheetA", configs.get(0).getSheetName());
        assertEquals(0, configs.get(0).getDataStartRow().intValue());
        assertEquals(0, configs.get(0).getDataStartColumn().intValue());
        assertEquals(RowExpansionMode.INSERT.name(), configs.get(0).getRowExpansionMode());
        assertEquals(EmptyResultBehavior.EMPTY_SHEET.name(), configs.get(0).getEmptyResultBehavior());
        assertNotNull(configs.get(0).getFieldBindings());
        assertTrue(configs.get(0).getFieldBindings().isEmpty());

        // file actually stored on disk
        File file = ExcelTemplateFileUtil.getTemplateFile(42L);
        assertTrue(file.exists());
    }

    @Test
    void uploadWithNullFileThrowsInvalidFormat() {
        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.upload(1L, "n", null, null, 100L));

        assertEquals(ErrorCode.EX_INVALID_FILE_FORMAT.getCode(), ex.getErrorCode());
        Mockito.verify(storageMock, Mockito.never()).save(any());
    }

    @Test
    void uploadWithEmptyFileThrowsInvalidFormat() {
        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.upload(1L, "n", null, new byte[0], 100L));

        assertEquals(ErrorCode.EX_INVALID_FILE_FORMAT.getCode(), ex.getErrorCode());
        Mockito.verify(storageMock, Mockito.never()).save(any());
    }

    @Test
    void uploadWithNonXlsxMagicBytesThrowsInvalidFormat() {
        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.upload(1L, "n", null, "not-an-xlsx".getBytes(), 100L));

        assertEquals(ErrorCode.EX_INVALID_FILE_FORMAT.getCode(), ex.getErrorCode());
        Mockito.verify(storageMock, Mockito.never()).save(any());
    }

    @Test
    void uploadWithPkMagicButCorruptContentThrowsCorrupted() {
        byte[] corrupt = {(byte) 'P', (byte) 'K', 0x03, 0x04, 0x00, 0x01, 0x02, 0x03};
        when(viewServiceMock.getById(100L)).thenReturn(new SavedQueryView());

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.upload(1L, "n", null, corrupt, 100L));

        assertEquals(ErrorCode.EX_CORRUPTED_TEMPLATE.getCode(), ex.getErrorCode());
        Mockito.verify(storageMock, Mockito.never()).save(any());
    }

    @Test
    void uploadWithInvalidQueryViewIdThrowsBindingError() {
        when(viewServiceMock.getById(999L)).thenReturn(null);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.upload(1L, "n", null, xlsxBytes("S"), 999L));

        assertEquals(ErrorCode.EX_BINDING_FIELD_NOT_FOUND.getCode(), ex.getErrorCode());
        Mockito.verify(storageMock, Mockito.never()).save(any());
    }

    @Test
    void uploadWithNullQueryViewIdThrowsBindingError() {
        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.upload(1L, "n", null, xlsxBytes("S"), null));

        assertEquals(ErrorCode.EX_BINDING_FIELD_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    @Test
    void uploadStoresFileHashMatchingContent() {
        byte[] content = xlsxBytes("Data");
        when(viewServiceMock.getById(1L)).thenReturn(new SavedQueryView());
        when(storageMock.save(any(ExcelReportTemplate.class))).thenAnswer(inv -> {
            ExcelReportTemplate t = inv.getArgument(0);
            t.setId(7L);
            return 7L;
        });
        track(7L);

        service.upload(1L, "n", null, content, 1L);

        ArgumentCaptor<ExcelReportTemplate> captor = ArgumentCaptor.forClass(ExcelReportTemplate.class);
        Mockito.verify(storageMock).update(captor.capture());
        assertEquals(sha256(content), captor.getValue().getFileHash());
    }

    // ── create ──────────────────────────────────────────────────

    @Test
    void createMinimalAssignsIdAndVersion() {
        when(storageMock.save(any(ExcelReportTemplate.class))).thenAnswer(inv -> {
            ExcelReportTemplate t = inv.getArgument(0);
            t.setId(1L);
            return 1L;
        });
        ExcelReportTemplate t = new ExcelReportTemplate();
        t.setName("Minimal");

        Long id = service.create(t);

        assertEquals(1L, id);
        assertEquals(1, t.getTemplateVersion().intValue());
        assertNotNull(t.getGmtCreate());
        assertNotNull(t.getGmtModified());
    }

    @Test
    void createWithBlankNameThrows() {
        ExcelReportTemplate t = new ExcelReportTemplate();
        t.setName("  ");

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.create(t));

        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
        Mockito.verify(storageMock, Mockito.never()).save(any());
    }

    @Test
    void createWithNullTemplateThrows() {
        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.create(null));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    // ── update ──────────────────────────────────────────────────

    @Test
    void updateChangesNameAndRefreshesModifiedTime() {
        ExcelReportTemplate stored = template(1L, "Original");
        stored.setTemplateVersion(1);
        stored.setGmtCreate(new Date(123456789L));
        when(storageMock.getById(1L)).thenReturn(stored);

        ExcelReportTemplate edit = new ExcelReportTemplate();
        edit.setId(1L);
        edit.setTemplateVersion(1);
        edit.setName("Renamed");
        service.update(edit);

        ArgumentCaptor<ExcelReportTemplate> captor = ArgumentCaptor.forClass(ExcelReportTemplate.class);
        Mockito.verify(storageMock).update(captor.capture());
        ExcelReportTemplate updated = captor.getValue();
        assertEquals("Renamed", updated.getName());
        assertEquals(new Date(123456789L), updated.getGmtCreate());
        assertNotNull(updated.getGmtModified());
    }

    @Test
    void updateWithVersionConflictThrows() {
        ExcelReportTemplate stored = template(1L, "Original");
        stored.setTemplateVersion(3);
        when(storageMock.getById(1L)).thenReturn(stored);

        ExcelReportTemplate edit = template(1L, "Stale");
        edit.setTemplateVersion(1);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.update(edit));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
        Mockito.verify(storageMock, Mockito.never()).update(any());
    }

    @Test
    void updateUnknownTemplateThrowsNotFound() {
        when(storageMock.getById(99L)).thenReturn(null);
        ExcelReportTemplate edit = new ExcelReportTemplate();
        edit.setId(99L);
        edit.setTemplateVersion(1);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.update(edit));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    // ── delete ──────────────────────────────────────────────────

    @Test
    void deleteRemovesTemplateFromStorage() {
        ExcelReportTemplate stored = template(1L, "ToDelete");
        when(storageMock.getById(1L)).thenReturn(stored);

        service.delete(1L);

        Mockito.verify(storageMock).delete(1L);
    }

    @Test
    void deleteUnknownThrowsNotFound() {
        when(storageMock.getById(1L)).thenReturn(null);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.delete(1L));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
        Mockito.verify(storageMock, Mockito.never()).delete(anyLong());
    }

    // ── copy ────────────────────────────────────────────────────

    @Test
    void copyCreatesNewTemplateNamedCopyOf() {
        ExcelReportTemplate original = template(1L, "Sales Template");
        original.setTemplateVersion(4);
        SheetConfig config = new SheetConfig();
        config.setSheetName("Sheet1");
        original.setSheetConfigs(List.of(config));
        when(storageMock.getById(1L)).thenReturn(original);
        when(storageMock.save(any(ExcelReportTemplate.class))).thenAnswer(inv -> {
            ExcelReportTemplate t = inv.getArgument(0);
            t.setId(2L);
            return 2L;
        });

        Long newId = service.copy(1L, null);

        assertEquals(2L, newId);
        ArgumentCaptor<ExcelReportTemplate> captor = ArgumentCaptor.forClass(ExcelReportTemplate.class);
        Mockito.verify(storageMock).save(captor.capture());
        ExcelReportTemplate copy = captor.getValue();
        assertEquals("Copy of Sales Template", copy.getName());
        assertEquals(1, copy.getTemplateVersion().intValue());
        // original must remain untouched
        assertEquals("Sales Template", original.getName());
        assertEquals(4, original.getTemplateVersion().intValue());
        // sheet configs are deep-copied
        assertNotSame(original.getSheetConfigs(), copy.getSheetConfigs());
    }

    @Test
    void copyWithExplicitNameUsesIt() {
        ExcelReportTemplate original = template(1L, "Sales");
        when(storageMock.getById(1L)).thenReturn(original);
        when(storageMock.save(any(ExcelReportTemplate.class))).thenAnswer(inv -> {
            ExcelReportTemplate t = inv.getArgument(0);
            t.setId(2L);
            return 2L;
        });

        service.copy(1L, "Q2 Sales");

        ArgumentCaptor<ExcelReportTemplate> captor = ArgumentCaptor.forClass(ExcelReportTemplate.class);
        Mockito.verify(storageMock).save(captor.capture());
        assertEquals("Q2 Sales", captor.getValue().getName());
    }

    // ── getSheetNames ───────────────────────────────────────────

    @Test
    void getSheetNamesReturnsNamesFromStoredFile() {
        // Upload a real xlsx to create a template file on disk
        byte[] content = xlsxBytes("Alpha", "Beta");
        when(viewServiceMock.getById(100L)).thenReturn(new SavedQueryView());
        when(storageMock.save(any(ExcelReportTemplate.class))).thenAnswer(inv -> {
            ExcelReportTemplate t = inv.getArgument(0);
            t.setId(5L);
            return 5L;
        });
        track(5L);
        // Capture the template after upload sets the templateFile
        ArgumentCaptor<ExcelReportTemplate> updateCaptor = ArgumentCaptor.forClass(ExcelReportTemplate.class);
        service.upload(1L, "n", null, content, 100L);
        Mockito.verify(storageMock).update(updateCaptor.capture());
        ExcelReportTemplate uploaded = updateCaptor.getValue();

        // Now mock getById to return the uploaded template with the real file path
        when(storageMock.getById(5L)).thenReturn(uploaded);

        List<String> names = service.getSheetNames(5L);

        assertEquals(List.of("Alpha", "Beta"), names);
    }

    @Test
    void getSheetNamesForMissingTemplateThrowsNotFound() {
        when(storageMock.getById(404L)).thenReturn(null);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.getSheetNames(404L));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    // ── list ────────────────────────────────────────────────────

    @Test
    void listPaginatesByWorkspaceAndSearchKey() {
        ExcelReportTemplate a = template(11L, "Report Alpha");
        a.setWorkspaceId(1L);
        ExcelReportTemplate b = template(12L, "Report Beta");
        b.setWorkspaceId(1L);
        ExcelReportTemplate c = template(13L, "Report Gamma");
        c.setWorkspaceId(2L);
        when(storageMock.getDataList()).thenReturn(List.of(a, b, c));

        PageResponse<ExcelReportTemplate> page1 = service.list(1L, 1, 1, null);
        assertEquals(2L, page1.getTotal());
        assertEquals(1, page1.getData().size());
        assertTrue(page1.getHasNextPage());

        PageResponse<ExcelReportTemplate> page2 = service.list(1L, 2, 1, null);
        assertEquals(1, page2.getData().size());
        assertFalse(page2.getHasNextPage());

        PageResponse<ExcelReportTemplate> bySearch = service.list(null, 1, 10, "alpha");
        assertEquals(1L, bySearch.getTotal());
        assertEquals("Report Alpha", bySearch.getData().get(0).getName());
    }

    // ── validate ────────────────────────────────────────────────

    @Test
    void validateValidTemplateReturnsEmptyErrors() {
        ExcelReportTemplate stored = template(1L, "Valid");
        String filePath = createStoredPoiFile("ValidSheet");
        stored.setTemplateFile(filePath);
        stored.setSheetConfigs(defaultConfigs("ValidSheet"));
        stored.setStatus(TemplateStatus.VALID.name());
        when(storageMock.getById(1L)).thenReturn(stored);

        assertTrue(service.validate(1L).isEmpty());
    }

    @Test
    void validateMissingTemplateThrowsNotFound() {
        when(storageMock.getById(404L)).thenReturn(null);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.validate(404L));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    @Test
    void validateTemplateWithNoFileReturnsError() {
        ExcelReportTemplate stored = template(1L, "NoFile");
        when(storageMock.getById(1L)).thenReturn(stored);

        List<ErrorCode> errors = service.validate(1L);

        assertTrue(errors.contains(ErrorCode.EX_TEMPLATE_NOT_FOUND));
    }

    // ── getById ─────────────────────────────────────────────────

    @Test
    void getByIdNonexistentReturnsNull() {
        when(storageMock.getById(999L)).thenReturn(null);
        assertNull(service.getById(999L));
    }

    @Test
    void getByIdReturnsStoredTemplate() {
        ExcelReportTemplate stored = template(1L, "Stored");
        when(storageMock.getById(1L)).thenReturn(stored);
        assertEquals("Stored", service.getById(1L).getName());
    }

    // ── updateSheetConfigs ──────────────────────────────────────

    @Test
    void updateSheetConfigsValidPersisted() {
        String filePath = createStoredPoiFile("Sheet1");
        ExcelReportTemplate stored = template(1L, "Template");
        stored.setQueryViewId(100L);
        stored.setTemplateFile(filePath);
        stored.setSheetConfigs(defaultConfigs("Sheet1"));
        when(storageMock.getById(1L)).thenReturn(stored);
        SavedQueryView view = new SavedQueryView();
        view.setDatasetId(10L);
        when(viewServiceMock.getById(100L)).thenReturn(view);
        QueryDataset dataset = new QueryDataset();
        QueryDatasetField field = new QueryDatasetField();
        field.setFieldId("field1");
        dataset.setFields(List.of(field));
        when(datasetServiceMock.getById(10L)).thenReturn(dataset);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("field1");
        binding.setTargetColumn("A");
        SheetConfig config = new SheetConfig();
        config.setSheetName("Sheet1");
        config.setDataStartRow(0);
        config.setDataStartColumn(0);
        config.setFieldBindings(List.of(binding));

        svc.updateSheetConfigs(1L, List.of(config));

        ArgumentCaptor<ExcelReportTemplate> captor = ArgumentCaptor.forClass(ExcelReportTemplate.class);
        verify(storageMock).update(captor.capture());
        ExcelReportTemplate updated = captor.getValue();
        assertEquals(1, updated.getSheetConfigs().size());
        assertEquals("Sheet1", updated.getSheetConfigs().get(0).getSheetName());
        assertNotNull(updated.getGmtModified());
    }

    @Test
    void updateSheetConfigsWithUnknownSheetNameThrowsSheetNotFound() {
        String filePath = createStoredPoiFile("Sheet1");
        ExcelReportTemplate stored = template(1L, "Template");
        stored.setQueryViewId(100L);
        stored.setTemplateFile(filePath);
        stored.setSheetConfigs(defaultConfigs("Sheet1"));
        when(storageMock.getById(1L)).thenReturn(stored);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        SheetConfig config = new SheetConfig();
        config.setSheetName("Sheet2");
        config.setDataStartRow(0);
        config.setDataStartColumn(0);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> svc.updateSheetConfigs(1L, List.of(config)));
        assertEquals(ErrorCode.EX_SHEET_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    @Test
    void updateSheetConfigsWithBindingReferencingUnknownFieldThrowsBindingNotFound() {
        String filePath = createStoredPoiFile("Sheet1");
        ExcelReportTemplate stored = template(1L, "Template");
        stored.setQueryViewId(100L);
        stored.setTemplateFile(filePath);
        stored.setSheetConfigs(defaultConfigs("Sheet1"));
        when(storageMock.getById(1L)).thenReturn(stored);
        SavedQueryView view = new SavedQueryView();
        view.setDatasetId(10L);
        when(viewServiceMock.getById(100L)).thenReturn(view);
        QueryDataset dataset = new QueryDataset();
        QueryDatasetField field = new QueryDatasetField();
        field.setFieldId("existingField");
        dataset.setFields(List.of(field));
        when(datasetServiceMock.getById(10L)).thenReturn(dataset);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("unknownField");
        binding.setTargetColumn("B");
        SheetConfig config = new SheetConfig();
        config.setSheetName("Sheet1");
        config.setDataStartRow(0);
        config.setDataStartColumn(0);
        config.setFieldBindings(List.of(binding));

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> svc.updateSheetConfigs(1L, List.of(config)));
        assertEquals(ErrorCode.EX_BINDING_FIELD_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    @Test
    void updateSheetConfigsWithInvalidTargetColumnThrowsBindingNotFound() {
        String filePath = createStoredPoiFile("Sheet1");
        ExcelReportTemplate stored = template(1L, "Template");
        stored.setQueryViewId(100L);
        stored.setTemplateFile(filePath);
        stored.setSheetConfigs(defaultConfigs("Sheet1"));
        when(storageMock.getById(1L)).thenReturn(stored);
        SavedQueryView view = new SavedQueryView();
        view.setDatasetId(10L);
        when(viewServiceMock.getById(100L)).thenReturn(view);
        QueryDataset dataset = new QueryDataset();
        QueryDatasetField field = new QueryDatasetField();
        field.setFieldId("field1");
        dataset.setFields(List.of(field));
        when(datasetServiceMock.getById(10L)).thenReturn(dataset);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("field1");
        binding.setTargetColumn("123");
        SheetConfig config = new SheetConfig();
        config.setSheetName("Sheet1");
        config.setDataStartRow(0);
        config.setDataStartColumn(0);
        config.setFieldBindings(List.of(binding));

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> svc.updateSheetConfigs(1L, List.of(config)));
        assertEquals(ErrorCode.EX_BINDING_FIELD_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    @Test
    void updateSheetConfigsNonexistentTemplateThrowsNotFound() {
        when(storageMock.getById(404L)).thenReturn(null);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> svc.updateSheetConfigs(404L, List.of()));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    @Test
    void updateSheetConfigsWithNegativeDataStartRowThrowsCorrupted() {
        String filePath = createStoredPoiFile("Sheet1");
        ExcelReportTemplate stored = template(1L, "Template");
        stored.setTemplateFile(filePath);
        stored.setSheetConfigs(defaultConfigs("Sheet1"));
        when(storageMock.getById(1L)).thenReturn(stored);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        SheetConfig config = new SheetConfig();
        config.setSheetName("Sheet1");
        config.setDataStartRow(-1);
        config.setDataStartColumn(0);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> svc.updateSheetConfigs(1L, List.of(config)));
        assertEquals(ErrorCode.EX_CORRUPTED_TEMPLATE.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    // ── updateFieldBindings ─────────────────────────────────────

    @Test
    void updateFieldBindingsValidPersisted() {
        String filePath = createStoredPoiFile("Sheet1");
        ExcelReportTemplate stored = template(1L, "Template");
        stored.setQueryViewId(100L);
        stored.setTemplateFile(filePath);
        stored.setSheetConfigs(new ArrayList<>(defaultConfigs("Sheet1")));
        when(storageMock.getById(1L)).thenReturn(stored);
        SavedQueryView view = new SavedQueryView();
        view.setDatasetId(10L);
        when(viewServiceMock.getById(100L)).thenReturn(view);
        QueryDataset dataset = new QueryDataset();
        QueryDatasetField field = new QueryDatasetField();
        field.setFieldId("field1");
        dataset.setFields(List.of(field));
        when(datasetServiceMock.getById(10L)).thenReturn(dataset);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("field1");
        binding.setTargetColumn("A");

        svc.updateFieldBindings(1L, "Sheet1", List.of(binding));

        ArgumentCaptor<ExcelReportTemplate> captor = ArgumentCaptor.forClass(ExcelReportTemplate.class);
        verify(storageMock).update(captor.capture());
        ExcelReportTemplate updated = captor.getValue();
        assertEquals(1, updated.getSheetConfigs().get(0).getFieldBindings().size());
        assertEquals("field1", updated.getSheetConfigs().get(0).getFieldBindings().get(0).getQueryFieldId());
        assertNotNull(updated.getGmtModified());
    }

    @Test
    void updateFieldBindingsWithUnknownSheetThrowsSheetNotFound() {
        String filePath = createStoredPoiFile("Sheet1");
        ExcelReportTemplate stored = template(1L, "Template");
        stored.setQueryViewId(100L);
        stored.setTemplateFile(filePath);
        stored.setSheetConfigs(defaultConfigs("Sheet1"));
        when(storageMock.getById(1L)).thenReturn(stored);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> svc.updateFieldBindings(1L, "Sheet2", List.of()));
        assertEquals(ErrorCode.EX_SHEET_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    @Test
    void updateFieldBindingsWithUnknownFieldIdThrowsBindingNotFound() {
        String filePath = createStoredPoiFile("Sheet1");
        ExcelReportTemplate stored = template(1L, "Template");
        stored.setQueryViewId(100L);
        stored.setTemplateFile(filePath);
        stored.setSheetConfigs(new ArrayList<>(defaultConfigs("Sheet1")));
        when(storageMock.getById(1L)).thenReturn(stored);
        SavedQueryView view = new SavedQueryView();
        view.setDatasetId(10L);
        when(viewServiceMock.getById(100L)).thenReturn(view);
        QueryDataset dataset = new QueryDataset();
        QueryDatasetField field = new QueryDatasetField();
        field.setFieldId("existingField");
        dataset.setFields(List.of(field));
        when(datasetServiceMock.getById(10L)).thenReturn(dataset);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("unknownField");
        binding.setTargetColumn("B");

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> svc.updateFieldBindings(1L, "Sheet1", List.of(binding)));
        assertEquals(ErrorCode.EX_BINDING_FIELD_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    @Test
    void updateFieldBindingsWithInvalidTargetColumnThrowsBindingNotFound() {
        String filePath = createStoredPoiFile("Sheet1");
        ExcelReportTemplate stored = template(1L, "Template");
        stored.setQueryViewId(100L);
        stored.setTemplateFile(filePath);
        stored.setSheetConfigs(new ArrayList<>(defaultConfigs("Sheet1")));
        when(storageMock.getById(1L)).thenReturn(stored);
        SavedQueryView view = new SavedQueryView();
        view.setDatasetId(10L);
        when(viewServiceMock.getById(100L)).thenReturn(view);
        QueryDataset dataset = new QueryDataset();
        QueryDatasetField field = new QueryDatasetField();
        field.setFieldId("field1");
        dataset.setFields(List.of(field));
        when(datasetServiceMock.getById(10L)).thenReturn(dataset);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("field1");
        binding.setTargetColumn("1A");

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> svc.updateFieldBindings(1L, "Sheet1", List.of(binding)));
        assertEquals(ErrorCode.EX_BINDING_FIELD_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    @Test
    void updateFieldBindingsNonexistentTemplateThrowsNotFound() {
        when(storageMock.getById(404L)).thenReturn(null);

        ExcelReportTemplateServiceImpl svc = new ExcelReportTemplateServiceImpl(
                storageMock, viewServiceMock, datasetServiceMock);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> svc.updateFieldBindings(404L, "Sheet1", List.of()));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    // ── real storage integration ────────────────────────────────

    @Test
    void realStorageCreateListDeleteRoundTrip() {
        ExcelReportTemplateServiceImpl real = new ExcelReportTemplateServiceImpl(
                ExcelReportTemplateStorage.INSTANCE, viewServiceMock);
        ExcelReportTemplate t = new ExcelReportTemplate();
        t.setWorkspaceId(77L);
        t.setName("it-template-" + UUID.randomUUID());
        Long id = real.create(t);
        track(id);
        assertNotNull(id);
        // Verify it exists
        assertTrue(real.list(77L, 1, 100, null).getData().stream().anyMatch(x -> id.equals(x.getId())));
        assertNotNull(real.getById(id));
        // Delete and verify gone
        real.delete(id);
        assertNull(real.getById(id));
    }

    // ── helpers ──────────────────────────────────────────────────

    private static ExcelReportTemplate template(Long id, String name) {
        ExcelReportTemplate t = new ExcelReportTemplate();
        t.setId(id);
        t.setName(name);
        return t;
    }

    private static List<SheetConfig> defaultConfigs(String... names) {
        List<SheetConfig> configs = new ArrayList<>();
        for (String name : names) {
            SheetConfig c = new SheetConfig();
            c.setSheetName(name);
            configs.add(c);
        }
        return configs;
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

    /** Writes a real .xlsx file into the template dir and returns its absolute path. */
    private static String createStoredPoiFile(String... sheetNames) {
        File dir = ExcelTemplateFileUtil.getTemplatesDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File f = new File(dir, "test-" + System.nanoTime() + ".xlsx");
        byte[] content = xlsxBytes(sheetNames);
        try {
            Files.write(f.toPath(), content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        f.deleteOnExit();
        return f.getAbsolutePath();
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void track(Long id) {
        if (id != null) {
            createdIds.add(id);
        }
    }
}