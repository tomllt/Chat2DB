package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.QueryExcelConstants;
import ai.chat2db.community.query.excel.domain.api.enums.FieldRole;
import ai.chat2db.community.query.excel.domain.api.enums.QueryDatasetStatus;
import ai.chat2db.community.query.excel.domain.api.enums.SavedQueryViewStatus;
import ai.chat2db.community.query.excel.domain.api.model.ColumnInfo;
import ai.chat2db.community.query.excel.domain.api.model.ExecuteQueryRequest;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.ViewDimension;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.model.ViewMeasure;
import ai.chat2db.community.query.excel.domain.api.model.ViewSort;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBSqlExecutor;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBMetadataProvider;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.IQueryValidationService;
import ai.chat2db.community.query.excel.storage.SavedQueryViewStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SavedQueryViewServiceImplTest {

    private SavedQueryViewServiceImpl service;
    private SavedQueryViewStorage storageMock;
    private IQueryValidationService validationMock;
    private Chat2DBMetadataProvider metadataMock;
    private IQueryDatasetService datasetServiceMock;

    @BeforeEach
    void setUp() {
        storageMock = mock(SavedQueryViewStorage.class);
        validationMock = mock(IQueryValidationService.class);
        metadataMock = mock(Chat2DBMetadataProvider.class);
        datasetServiceMock = mock(IQueryDatasetService.class);
        service = new SavedQueryViewServiceImpl(storageMock, validationMock, metadataMock, datasetServiceMock);
        lenient().when(validationMock.validateFilters(anyList(), anyList())).thenReturn(Collections.emptyList());
        lenient().when(validationMock.validateSort(anyList(), anyList())).thenReturn(Collections.emptyList());
        lenient().when(metadataMock.testConnection(anyLong())).thenReturn(true);
        lenient().when(metadataMock.getTableColumns(anyLong(), any(), any(), any()))
                .thenReturn(List.of(columnInfo("amount", "DECIMAL"), columnInfo("region", "VARCHAR")));
    }

    // ── create ───────────────────────────────────────────────────

    @Test
    void createValidViewAssignsIdAndDefaults() {
        stubSaveAssignsId(1L);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        SavedQueryView view = validView();

        Long id = service.create(view);

        assertEquals(1L, id);
        assertEquals(1, view.getVersion());
        assertEquals(SavedQueryViewStatus.DRAFT.name(), view.getStatus());
        assertNotNull(view.getGmtCreate());
        assertNotNull(view.getGmtModified());
    }

    @Test
    void createWithNoRowAndNoColumnFieldsThrows() {
        SavedQueryView view = validView();
        view.setRowFields(null);
        view.setColumnFields(null);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.create(view));

        assertEquals(ErrorCode.QV_NO_ROW_OR_COLUMN_FIELD.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    @Test
    void createWithNonPublishedDatasetThrows() {
        QueryDataset draft = publishedDataset();
        draft.setStatus(QueryDatasetStatus.DRAFT.name());
        when(datasetServiceMock.getById(100L)).thenReturn(draft);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.create(validView()));

        assertEquals(ErrorCode.QV_DATASET_NOT_PUBLISHED.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    @Test
    void createWithNonExistentFieldThrows() {
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        SavedQueryView view = validView();
        view.getDimensions().get(0).setFieldId("nope");

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.create(view));

        assertEquals(ErrorCode.QV_FIELD_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    @Test
    void createWithInvalidFilterOperatorThrows() {
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        SavedQueryView view = validView();
        // VARCHAR field with a numeric-only operator
        ViewFilter filter = view.getFilters().get(0);
        filter.setFieldId("f2");
        filter.setOperator("GT");

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.create(view));

        assertEquals(ErrorCode.QV_INVALID_FILTER_FIELD.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    @Test
    void createWithNonSortableFieldThrows() {
        QueryDataset dataset = publishedDataset();
        dataset.getFields().get(1).setSortable(false);
        when(datasetServiceMock.getById(100L)).thenReturn(dataset);
        SavedQueryView view = validView();
        ViewSort sort = new ViewSort();
        sort.setFieldId("f2");
        sort.setDirection("ASC");
        view.setSort(List.of(sort));

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.create(view));

        assertEquals(ErrorCode.QV_INVALID_SORT_FIELD.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    @Test
    void createWithValidFieldsPassesAllRules() {
        stubSaveAssignsId(1L);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        SavedQueryView view = validView();

        Long id = service.create(view);

        assertEquals(1L, id);
        verify(storageMock).save(view);
    }

    // ── update ───────────────────────────────────────────────────

    @Test
    void updateWithStaleVersionThrowsConflict() {
        SavedQueryView stored = validView();
        stored.setId(1L);
        stored.setVersion(2);
        when(storageMock.getById(1L)).thenReturn(stored);

        SavedQueryView edit = validView();
        edit.setId(1L);
        edit.setVersion(1);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.update(edit));

        assertEquals(ErrorCode.QV_VERSION_CONFLICT.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    @Test
    void updateUnknownViewThrowsNotFound() {
        when(storageMock.getById(999L)).thenReturn(null);
        SavedQueryView edit = new SavedQueryView();
        edit.setId(999L);
        edit.setVersion(1);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.update(edit));

        assertEquals(ErrorCode.QV_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    @Test
    void updateAppliesEditsAndRefreshesModifiedTime() {
        SavedQueryView stored = validView();
        stored.setId(1L);
        stored.setVersion(1);
        stored.setGmtCreate(new Date(123456789L));
        stored.setName("Original");
        when(storageMock.getById(1L)).thenReturn(stored);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());

        SavedQueryView edit = new SavedQueryView();
        edit.setId(1L);
        edit.setVersion(1);
        edit.setName("Renamed");
        service.update(edit);

        ArgumentCaptor<SavedQueryView> captor = ArgumentCaptor.forClass(SavedQueryView.class);
        verify(storageMock).update(captor.capture());
        SavedQueryView updated = captor.getValue();
        assertEquals("Renamed", updated.getName());
        assertEquals(1, updated.getVersion().intValue());
        assertEquals(new Date(123456789L), updated.getGmtCreate());
        assertNotNull(updated.getGmtModified());
    }

    @Test
    void updatePublishedViewThrowsImmutabilityError() {
        SavedQueryView stored = validView();
        stored.setId(1L);
        stored.setStatus(SavedQueryViewStatus.PUBLISHED.name());
        stored.setVersion(1);
        when(storageMock.getById(1L)).thenReturn(stored);

        SavedQueryView edit = validView();
        edit.setId(1L);
        edit.setVersion(1);
        edit.setName("Renamed");

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.update(edit));

        assertEquals(ErrorCode.QV_PUBLISH_FAILED.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    // ── copy ─────────────────────────────────────────────────────

    @Test
    void copyCreatesNewDraft() {
        SavedQueryView original = validView();
        original.setId(1L);
        original.setName("Sales View");
        original.setStatus(SavedQueryViewStatus.PUBLISHED.name());
        original.setVersion(3);
        when(storageMock.getById(1L)).thenReturn(original);
        stubSaveAssignsId(2L);

        Long newId = service.copy(1L, null);

        assertEquals(2L, newId);
        ArgumentCaptor<SavedQueryView> captor = ArgumentCaptor.forClass(SavedQueryView.class);
        verify(storageMock).save(captor.capture());
        SavedQueryView copy = captor.getValue();
        assertEquals("Copy of Sales View", copy.getName());
        assertEquals(SavedQueryViewStatus.DRAFT.name(), copy.getStatus());
        assertEquals(1, copy.getVersion().intValue());
        assertNotSame(original.getDimensions(), copy.getDimensions());
        assertNotSame(original.getDimensions().get(0), copy.getDimensions().get(0));
        // original must remain untouched
        assertEquals("Sales View", original.getName());
        assertEquals(SavedQueryViewStatus.PUBLISHED.name(), original.getStatus());
    }

    @Test
    void copyWithExplicitNameUsesIt() {
        SavedQueryView original = validView();
        original.setId(1L);
        original.setName("Sales View");
        when(storageMock.getById(1L)).thenReturn(original);
        stubSaveAssignsId(2L);

        service.copy(1L, "Q2 Sales View");

        ArgumentCaptor<SavedQueryView> captor = ArgumentCaptor.forClass(SavedQueryView.class);
        verify(storageMock).save(captor.capture());
        assertEquals("Q2 Sales View", captor.getValue().getName());
    }

    // ── publish / disable ────────────────────────────────────────

    @Test
    void publishMarksPublishedAndIncrementsVersion() {
        SavedQueryView draft = validView();
        draft.setId(1L);
        draft.setStatus(SavedQueryViewStatus.DRAFT.name());
        draft.setVersion(1);
        when(storageMock.getById(1L)).thenReturn(draft);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        when(validationMock.validateView(any())).thenReturn(Collections.emptyList());
        stubSaveAssignsId(1L);

        service.publish(1L);

        ArgumentCaptor<SavedQueryView> captor = ArgumentCaptor.forClass(SavedQueryView.class);
        verify(storageMock).save(captor.capture());
        SavedQueryView published = captor.getValue();
        assertEquals(SavedQueryViewStatus.PUBLISHED.name(), published.getStatus());
        assertEquals(2, published.getVersion().intValue());
    }

    @Test
    void publishWithValidationFailureThrows() {
        SavedQueryView draft = validView();
        draft.setId(1L);
        draft.setRowFields(null);
        draft.setColumnFields(null);
        when(storageMock.getById(1L)).thenReturn(draft);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        when(validationMock.validateView(any())).thenReturn(List.of(ErrorCode.QV_NO_ROW_OR_COLUMN_FIELD));

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.publish(1L));

        assertEquals(ErrorCode.QV_PUBLISH_FAILED.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    @Test
    void disableMarksDisabled() {
        SavedQueryView published = validView();
        published.setId(1L);
        published.setStatus(SavedQueryViewStatus.PUBLISHED.name());
        when(storageMock.getById(1L)).thenReturn(published);
        stubSaveAssignsId(1L);

        service.disable(1L);

        ArgumentCaptor<SavedQueryView> captor = ArgumentCaptor.forClass(SavedQueryView.class);
        verify(storageMock).save(captor.capture());
        assertEquals(SavedQueryViewStatus.DISABLED.name(), captor.getValue().getStatus());
    }

    // ── checkCompatibility / INVALID detection ─────────────────

    @Test
    void checkCompatibilityWithSameVersionReturnsTrue() {
        SavedQueryView published = validView();
        published.setId(1L);
        published.setDatasetVersion(1);
        published.setStatus(SavedQueryViewStatus.PUBLISHED.name());
        when(storageMock.getById(1L)).thenReturn(published);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        when(validationMock.validateViewCompatibility(any(), any())).thenReturn(true);

        boolean compatible = service.checkCompatibility(1L);

        assertTrue(compatible);
        verify(validationMock).validateViewCompatibility(any(), any());
        verify(storageMock, never()).save(any());
    }

    @Test
    void checkCompatibilityWithVersionChangedAndFieldsCompatibleReturnsTrue() {
        SavedQueryView published = validView();
        published.setId(1L);
        published.setDatasetVersion(1);
        published.setStatus(SavedQueryViewStatus.PUBLISHED.name());
        when(storageMock.getById(1L)).thenReturn(published);
        QueryDataset dataset = publishedDataset();
        dataset.setVersion(2); // version changed
        when(datasetServiceMock.getById(100L)).thenReturn(dataset);
        when(validationMock.validateViewCompatibility(any(), any())).thenReturn(true);

        assertTrue(service.checkCompatibility(1L));

        verify(validationMock).validateViewCompatibility(any(), any());
    }

    @Test
    void checkCompatibilityWithRemovedFieldMarksViewInvalid() {
        SavedQueryView published = validView();
        published.setId(1L);
        published.setDatasetVersion(1);
        published.setStatus(SavedQueryViewStatus.PUBLISHED.name());
        when(storageMock.getById(1L)).thenReturn(published);
        QueryDataset dataset = publishedDataset();
        dataset.setVersion(2); // version changed
        when(datasetServiceMock.getById(100L)).thenReturn(dataset);
        when(validationMock.validateViewCompatibility(any(), any())).thenReturn(false);

        assertFalse(service.checkCompatibility(1L));

        ArgumentCaptor<SavedQueryView> captor = ArgumentCaptor.forClass(SavedQueryView.class);
        verify(storageMock).save(captor.capture());
        assertEquals(SavedQueryViewStatus.INVALID.name(), captor.getValue().getStatus());
    }

    @Test
    void checkCompatibilityWithMissingDatasetMarksViewInvalid() {
        SavedQueryView published = validView();
        published.setId(1L);
        published.setStatus(SavedQueryViewStatus.PUBLISHED.name());
        when(storageMock.getById(1L)).thenReturn(published);
        when(datasetServiceMock.getById(100L)).thenReturn(null);

        assertFalse(service.checkCompatibility(1L));

        ArgumentCaptor<SavedQueryView> captor = ArgumentCaptor.forClass(SavedQueryView.class);
        verify(storageMock).save(captor.capture());
        assertEquals(SavedQueryViewStatus.INVALID.name(), captor.getValue().getStatus());
        verify(validationMock, never()).validateViewCompatibility(any(), any());
    }

    @Test
    void getByIdPreservesInvalidStatusAfterIncompatibility() {
        SavedQueryView published = validView();
        published.setId(1L);
        published.setDatasetVersion(1);
        published.setStatus(SavedQueryViewStatus.PUBLISHED.name());
        when(storageMock.getById(1L)).thenReturn(published);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        when(validationMock.validateViewCompatibility(any(), any())).thenReturn(false);

        SavedQueryView result = service.getById(1L);

        assertEquals(SavedQueryViewStatus.INVALID.name(), result.getStatus());
    }

    @Test
    void getByIdDoesNotCheckCompatibilityForNonPublishedViews() {
        SavedQueryView draft = validView();
        draft.setId(1L);
        draft.setStatus(SavedQueryViewStatus.DRAFT.name());
        when(storageMock.getById(1L)).thenReturn(draft);

        SavedQueryView result = service.getById(1L);

        assertNotNull(result);
        assertEquals(SavedQueryViewStatus.DRAFT.name(), result.getStatus());
        verify(validationMock, never()).validateViewCompatibility(any(), any());
    }

    // ── validate ─────────────────────────────────────────────────

    @Test
    void validateReturnsCollectedErrors() {
        SavedQueryView broken = validView();
        broken.setId(1L);
        broken.setRowFields(null);
        broken.setColumnFields(null);
        broken.getDimensions().get(0).setFieldId("nope"); // non-existent field too
        when(storageMock.getById(1L)).thenReturn(broken);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());

        List<ErrorCode> errors = service.validate(1L);

        assertTrue(errors.contains(ErrorCode.QV_NO_ROW_OR_COLUMN_FIELD));
        assertTrue(errors.contains(ErrorCode.QV_FIELD_NOT_FOUND));
    }

    @Test
    void validateValidViewReturnsEmptyList() {
        SavedQueryView good = validView();
        good.setId(1L);
        when(storageMock.getById(1L)).thenReturn(good);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());

        assertTrue(service.validate(1L).isEmpty());
    }

    // ── preview ──────────────────────────────────────────────────

    @Test
    void previewExecutesGeneratedSqlAndMapsRows() {
        SavedQueryView view = validView();
        view.setId(1L);
        when(storageMock.getById(1L)).thenReturn(view);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        service.executor = mock(Chat2DBSqlExecutor.class);
        when(service.executor.execute(any(ExecuteQueryRequest.class)))
                .thenReturn(QueryResult.builder()
                        .columns(List.of("region", "amount"))
                        .rows(List.of(List.of("EU", 100), List.of("US", 200)))
                        .total(2L)
                        .build());

        PreviewResult result = service.preview(1L, 1, 20, Collections.emptyList());

        assertEquals(2, result.getRows().size());
        assertEquals("EU", result.getRows().get(0).get("Region"));
        assertEquals(100, result.getRows().get(0).get("Amount"));
        assertEquals(2L, result.getTotal());
        assertEquals(1, result.getPageNo());
        assertEquals(20, result.getPageSize());
        assertEquals(2, result.getColumns().size());
        assertEquals("Region", result.getColumns().get(0));
        assertEquals("Amount", result.getColumns().get(1));
    }

    @Test
    void previewPassesGeneratedSqlAndTimeoutToExecutor() {
        SavedQueryView view = validView();
        view.setId(1L);
        when(storageMock.getById(1L)).thenReturn(view);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        Chat2DBSqlExecutor executorMock = mock(Chat2DBSqlExecutor.class);
        service.executor = executorMock;
        when(executorMock.execute(any(ExecuteQueryRequest.class)))
                .thenReturn(QueryResult.builder().columns(List.of()).rows(List.of()).total(0L).build());

        service.preview(1L, 2, 10, Collections.emptyList());

        ArgumentCaptor<ExecuteQueryRequest> captor = ArgumentCaptor.forClass(ExecuteQueryRequest.class);
        verify(executorMock).execute(captor.capture());

        ExecuteQueryRequest req = captor.getValue();
        String sql = req.getSql();
        assertTrue(sql.startsWith("SELECT "));
        assertTrue(sql.contains("FROM `test_db`.`sales`"));
        assertTrue(sql.contains("GROUP BY `region`"));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
        // view filter (EQ EU) + LIMIT/OFFSET = 3 bound params
        assertEquals(3, req.getParams().size());
        assertEquals("EU", req.getParams().get(0));
        assertEquals(10, req.getParams().get(1));
        assertEquals(10L, req.getParams().get(2));
        assertEquals(QueryExcelConstants.QUERY_TIMEOUT_MS, req.getTimeoutMs());
        assertEquals(1L, req.getDatasourceId());
        assertEquals("test_db", req.getDatabaseName());
        assertEquals("sales", req.getTableName());
    }

    @Test
    void previewWithoutDimensionOrMeasureThrows() {
        SavedQueryView view = validView();
        view.setDimensions(null);
        view.setMeasures(null);
        view.setId(1L);
        when(storageMock.getById(1L)).thenReturn(view);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        service.executor = mock(Chat2DBSqlExecutor.class);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.preview(1L, 1, 20, Collections.emptyList()));

        assertEquals(ErrorCode.QV_NO_DIMENSION_OR_MEASURE.getCode(), ex.getErrorCode());
    }

    @Test
    void previewWithoutExecutorThrowsUnsupportedOperation() {
        SavedQueryView view = validView();
        view.setId(1L);
        when(storageMock.getById(1L)).thenReturn(view);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());

        assertThrows(UnsupportedOperationException.class,
                () -> service.preview(1L, 1, 20, Collections.emptyList()));
    }

    @Test
    void previewWithUnknownViewThrowsNotFound() {
        when(storageMock.getById(999L)).thenReturn(null);
        service.executor = mock(Chat2DBSqlExecutor.class);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.preview(999L, 1, 20, Collections.emptyList()));

        assertEquals(ErrorCode.QV_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    @Test
    void previewWithFilterOverridesAppliesThem() {
        SavedQueryView view = validView();
        view.setId(1L);
        when(storageMock.getById(1L)).thenReturn(view);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        Chat2DBSqlExecutor executorMock = mock(Chat2DBSqlExecutor.class);
        service.executor = executorMock;
        when(executorMock.execute(any(ExecuteQueryRequest.class)))
                .thenReturn(QueryResult.builder().columns(List.of()).rows(List.of()).total(0L).build());

        ViewFilter override = new ViewFilter();
        override.setFieldId("f1");
        override.setOperator("GT");
        override.setValue("500");
        service.preview(1L, 1, 20, List.of(override));

        ArgumentCaptor<ExecuteQueryRequest> captor = ArgumentCaptor.forClass(ExecuteQueryRequest.class);
        verify(executorMock).execute(captor.capture());
        assertTrue(captor.getValue().getSql().contains("`amount` > ?"));
    }

    // ── executeQuery ─────────────────────────────────────────────

    @Test
    void executeQueryReturnsQueryResult() {
        SavedQueryView view = validView();
        view.setId(1L);
        when(storageMock.getById(1L)).thenReturn(view);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        Chat2DBSqlExecutor executorMock = mock(Chat2DBSqlExecutor.class);
        service.executor = executorMock;
        when(executorMock.execute(any(ExecuteQueryRequest.class)))
                .thenReturn(QueryResult.builder()
                        .columns(List.of("region", "amount"))
                        .rows(List.of(List.of("EU", 100), List.of("US", 200), List.of("APAC", 300)))
                        .total(3L)
                        .build());

        QueryResult result = service.executeQuery(1L, Collections.emptyList());

        assertNotNull(result);
        assertEquals(3L, result.getTotal());
        assertEquals(3, result.getRows().size());
        assertEquals("Region", result.getColumns().get(0));
    }

    @Test
    void executeQueryHasNoLimitClause() {
        SavedQueryView view = validView();
        view.setId(1L);
        when(storageMock.getById(1L)).thenReturn(view);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        Chat2DBSqlExecutor executorMock = mock(Chat2DBSqlExecutor.class);
        service.executor = executorMock;
        when(executorMock.execute(any(ExecuteQueryRequest.class)))
                .thenReturn(QueryResult.builder().columns(List.of()).rows(List.of()).total(0L).build());

        service.executeQuery(1L, Collections.emptyList());

        ArgumentCaptor<ExecuteQueryRequest> captor = ArgumentCaptor.forClass(ExecuteQueryRequest.class);
        verify(executorMock).execute(captor.capture());
        assertFalse(captor.getValue().getSql().contains("LIMIT"));
    }

    // ── delete ───────────────────────────────────────────────────

    @Test
    void deleteUsesStorageWhenFound() {
        SavedQueryView view = validView();
        view.setId(1L);
        when(storageMock.getById(1L)).thenReturn(view);

        service.delete(1L);

        verify(storageMock).delete(1L);
    }

    @Test
    void deleteUnknownThrowsNotFound() {
        when(storageMock.getById(999L)).thenReturn(null);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.delete(999L));

        assertEquals(ErrorCode.QV_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).delete(any());
    }

    // ── list ─────────────────────────────────────────────────────

    @Test
    void listPaginatesAndFiltersAcrossWorkspacesAndSearchKey() {
        SavedQueryViewServiceImpl real = new SavedQueryViewServiceImpl(
                SavedQueryViewStorage.INSTANCE, validationMock, metadataMock, datasetServiceMock);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        String marker = "it-view-list-" + UUID.randomUUID();
        Long workspace = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        List<Long> ids = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                SavedQueryView v = validView();
                v.setName(marker + "-" + i);
                v.setWorkspaceId(workspace);
                ids.add(real.create(v));
            }
            SavedQueryView other = validView();
            other.setName(marker + "-other-workspace");
            other.setWorkspaceId(workspace + 1);
            ids.add(real.create(other));

            PageResponse<SavedQueryView> page1 = real.list(workspace, 1, 2, null);
            assertEquals(5L, page1.getTotal());
            assertEquals(2, page1.getData().size());
            assertTrue(page1.getHasNextPage());

            PageResponse<SavedQueryView> page3 = real.list(workspace, 3, 2, null);
            assertEquals(1, page3.getData().size());
            assertFalse(page3.getHasNextPage());

            PageResponse<SavedQueryView> bySearch = real.list(null, 1, 100, marker);
            assertEquals(6L, bySearch.getTotal());
            assertTrue(bySearch.getData().stream().allMatch(v -> v.getName().contains(marker)));
        } finally {
            for (Long id : ids) {
                if (id != null) {
                    real.delete(id);
                }
            }
        }
    }

    // ── real storage integration ─────────────────────────────────

    @Test
    void deleteRemovesViewFromRealStorage() {
        SavedQueryViewServiceImpl real = new SavedQueryViewServiceImpl(
                SavedQueryViewStorage.INSTANCE, validationMock, metadataMock, datasetServiceMock);
        when(datasetServiceMock.getById(100L)).thenReturn(publishedDataset());
        SavedQueryView view = validView();
        view.setName("it-view-delete-" + UUID.randomUUID());

        Long id = real.create(view);

        assertNotNull(id);
        assertNotNull(real.getById(id));
        real.delete(id);
        assertNull(real.getById(id));
    }

    // ── helpers ──────────────────────────────────────────────────

    /**
     * A valid view: row+column fields present, dimensions/measures present,
     * all referenced fields exist in the published dataset, valid filters/sort.
     */
    private static SavedQueryView validView() {
        SavedQueryView view = new SavedQueryView();
        view.setDatasetId(100L);
        view.setName("Sales View");
        view.setRowFields(new ArrayList<>(List.of("f2")));
        view.setColumnFields(new ArrayList<>(List.of("f1")));

        ViewDimension dim = new ViewDimension();
        dim.setFieldId("f2");
        view.setDimensions(new ArrayList<>(List.of(dim)));

        ViewMeasure measure = new ViewMeasure();
        measure.setFieldId("f1");
        measure.setAggregation("SUM");
        view.setMeasures(new ArrayList<>(List.of(measure)));

        ViewFilter filter = new ViewFilter();
        filter.setFieldId("f2");
        filter.setOperator("EQ");
        filter.setValue("EU");
        view.setFilters(new ArrayList<>(List.of(filter)));

        ViewSort sort = new ViewSort();
        sort.setFieldId("f2");
        sort.setDirection("ASC");
        view.setSort(new ArrayList<>(List.of(sort)));

        view.setPageSize(100);
        return view;
    }

    private static QueryDataset publishedDataset() {
        QueryDataset dataset = new QueryDataset();
        dataset.setId(100L);
        dataset.setName("Sales");
        dataset.setDatasourceId(1L);
        dataset.setTableName("sales");
        dataset.setDatabaseName("test_db");
        dataset.setStatus(QueryDatasetStatus.PUBLISHED.name());
        dataset.setVersion(1);
        dataset.setFields(new ArrayList<>(Arrays.asList(measureField(), dimensionField())));
        return dataset;
    }

    private static QueryDatasetField measureField() {
        QueryDatasetField f = new QueryDatasetField();
        f.setFieldId("f1");
        f.setSourceColumn("amount");
        f.setDisplayName("Amount");
        f.setDataType("DECIMAL");
        f.setRole(FieldRole.MEASURE.name());
        f.setAggregation("SUM");
        f.setFilterable(true);
        f.setSortable(true);
        f.setVisible(true);
        return f;
    }

    private static QueryDatasetField dimensionField() {
        QueryDatasetField f = new QueryDatasetField();
        f.setFieldId("f2");
        f.setSourceColumn("region");
        f.setDisplayName("Region");
        f.setDataType("VARCHAR");
        f.setRole(FieldRole.DIMENSION.name());
        f.setFilterable(true);
        f.setSortable(true);
        f.setVisible(true);
        return f;
    }

    private void stubSaveAssignsId(Long id) {
        when(storageMock.save(any(SavedQueryView.class))).thenAnswer(inv -> {
            SavedQueryView v = inv.getArgument(0);
            v.setId(id);
            return id;
        });
    }

    private static ColumnInfo columnInfo(String name, String dataType) {
        ColumnInfo c = new ColumnInfo();
        c.setColumnName(name);
        c.setDataType(dataType);
        c.setNullable(false);
        return c;
    }
}