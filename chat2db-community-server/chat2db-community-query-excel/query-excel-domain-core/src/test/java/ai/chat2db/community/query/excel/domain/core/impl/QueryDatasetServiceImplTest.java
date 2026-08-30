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
import ai.chat2db.community.query.excel.domain.api.model.ColumnInfo;
import ai.chat2db.community.query.excel.domain.api.model.DatasetFilter;
import ai.chat2db.community.query.excel.domain.api.model.ExecuteQueryRequest;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBSqlExecutor;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBMetadataProvider;
import ai.chat2db.community.query.excel.domain.api.service.IQueryValidationService;
import ai.chat2db.community.query.excel.storage.QueryDatasetStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QueryDatasetServiceImplTest {

    private QueryDatasetServiceImpl service;
    private QueryDatasetStorage storageMock;
    private IQueryValidationService validationMock;
    private Chat2DBMetadataProvider metadataMock;

    @BeforeEach
    void setUp() {
        storageMock = mock(QueryDatasetStorage.class);
        validationMock = mock(IQueryValidationService.class);
        metadataMock = mock(Chat2DBMetadataProvider.class);
        service = new QueryDatasetServiceImpl(storageMock, validationMock, metadataMock);
        lenient().when(validationMock.validateDatasetForPublish(any(QueryDataset.class)))
                .thenAnswer(inv -> {
                    QueryDataset d = inv.getArgument(0);
                    if (d.getFields() == null || d.getFields().isEmpty()) {
                        return List.of(ErrorCode.DS_NO_FIELDS);
                    }
                    return Collections.emptyList();
                });
        lenient().when(metadataMock.testConnection(anyLong())).thenReturn(true);
        lenient().when(metadataMock.getTableColumns(anyLong(), any(), any(), any()))
                .thenReturn(List.of(columnInfo("amount", "DECIMAL"), columnInfo("region", "VARCHAR")));
    }

    // ── create ───────────────────────────────────────────────────

    @Test
    void createWithValidFieldsAssignsIdAndDefaults() {
        stubSaveAssignsId(1L);
        QueryDataset dataset = validDataset();

        Long id = service.create(dataset);

        assertEquals(1L, id);
        assertEquals(1, dataset.getVersion());
        assertEquals(QueryDatasetStatus.DRAFT.name(), dataset.getStatus());
        assertNotNull(dataset.getGmtCreate());
        assertNotNull(dataset.getGmtModified());
    }

    @Test
    void createWithoutFieldsThrowsNoFields() {
        QueryDataset dataset = validDataset();
        dataset.setFields(null);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.create(dataset));

        assertEquals(ErrorCode.DS_NO_FIELDS.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    @Test
    void createTextFieldWithSumAggregationThrows() {
        QueryDataset dataset = validDataset();
        dataset.getFields().get(1).setAggregation("SUM"); // f2 is VARCHAR

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.create(dataset));

        assertEquals(ErrorCode.DS_TEXT_AGGREGATION.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    @Test
    void createMeasureWithoutAggregationThrows() {
        QueryDataset dataset = validDataset();
        dataset.getFields().get(0).setAggregation(null); // f1 is MEASURE

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.create(dataset));

        assertEquals(ErrorCode.DS_INVALID_AGGREGATION.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    @Test
    void createFilterOnNonFilterableFieldThrows() {
        QueryDataset dataset = validDataset();
        dataset.getFields().get(1).setFilterable(false);
        DatasetFilter filter = new DatasetFilter();
        filter.setFieldId("f2");
        dataset.setBaseFilters(List.of(filter));

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.create(dataset));

        assertEquals(ErrorCode.DS_FILTER_FIELD_NOT_FILTERABLE.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    // ── update ───────────────────────────────────────────────────

    @Test
    void updateAppliesEditsAndRefreshesModifiedTime() {
        QueryDataset stored = validDataset();
        stored.setId(1L);
        stored.setVersion(1);
        stored.setGmtCreate(new Date(123456789L));
        stored.setName("Original");
        when(storageMock.getById(1L)).thenReturn(stored);

        QueryDataset edit = new QueryDataset();
        edit.setId(1L);
        edit.setVersion(1);
        edit.setName("Renamed");
        service.update(edit);

        ArgumentCaptor<QueryDataset> captor = ArgumentCaptor.forClass(QueryDataset.class);
        verify(storageMock).update(captor.capture());
        QueryDataset updated = captor.getValue();
        assertEquals("Renamed", updated.getName());
        assertEquals(1, updated.getVersion().intValue());
        assertEquals(new Date(123456789L), updated.getGmtCreate());
        assertNotNull(updated.getGmtModified());
    }

    @Test
    void updateWithStaleVersionThrowsConflict() {
        QueryDataset stored = validDataset();
        stored.setId(1L);
        stored.setVersion(2);
        when(storageMock.getById(1L)).thenReturn(stored);

        QueryDataset edit = validDataset();
        edit.setId(1L);
        edit.setVersion(1);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.update(edit));

        assertEquals(ErrorCode.DS_VERSION_CONFLICT.getCode(), ex.getErrorCode());
        verify(storageMock, never()).update(any());
    }

    @Test
    void updateUnknownDatasetThrowsNotFound() {
        when(storageMock.getById(999L)).thenReturn(null);
        QueryDataset edit = new QueryDataset();
        edit.setId(999L);
        edit.setVersion(1);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.update(edit));

        assertEquals(ErrorCode.DS_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    // ── copy ─────────────────────────────────────────────────────

    @Test
    void copyCreatesDraftWithCopyOfName() {
        QueryDataset original = validDataset();
        original.setId(1L);
        original.setName("Sales");
        original.setStatus(QueryDatasetStatus.PUBLISHED.name());
        original.setVersion(3);
        original.setSourceSchemaHash("abc123");
        when(storageMock.getById(1L)).thenReturn(original);
        stubSaveAssignsId(2L);

        Long newId = service.copy(1L, null);

        assertEquals(2L, newId);
        ArgumentCaptor<QueryDataset> captor = ArgumentCaptor.forClass(QueryDataset.class);
        verify(storageMock).save(captor.capture());
        QueryDataset copy = captor.getValue();
        assertEquals("Copy of Sales", copy.getName());
        assertEquals(QueryDatasetStatus.DRAFT.name(), copy.getStatus());
        assertEquals(1, copy.getVersion().intValue());
        assertNull(copy.getSourceSchemaHash());
        assertNotSame(original.getFields(), copy.getFields());
        assertNotSame(original.getFields().get(0), copy.getFields().get(0));
        // original must remain untouched
        assertEquals("Sales", original.getName());
        assertEquals(QueryDatasetStatus.PUBLISHED.name(), original.getStatus());
    }

    @Test
    void copyWithExplicitNameUsesIt() {
        QueryDataset original = validDataset();
        original.setId(1L);
        original.setName("Sales");
        when(storageMock.getById(1L)).thenReturn(original);
        stubSaveAssignsId(2L);

        service.copy(1L, "Q2 Sales");

        ArgumentCaptor<QueryDataset> captor = ArgumentCaptor.forClass(QueryDataset.class);
        verify(storageMock).save(captor.capture());
        assertEquals("Q2 Sales", captor.getValue().getName());
    }

    // ── publish / disable ────────────────────────────────────────

    @Test
    void publishMarksPublishedAndIncrementsVersion() {
        QueryDataset draft = validDataset();
        draft.setId(1L);
        draft.setStatus(QueryDatasetStatus.DRAFT.name());
        draft.setVersion(1);
        when(storageMock.getById(1L)).thenReturn(draft);
        stubSaveAssignsId(1L);

        service.publish(1L);

        ArgumentCaptor<QueryDataset> captor = ArgumentCaptor.forClass(QueryDataset.class);
        verify(storageMock).save(captor.capture());
        QueryDataset published = captor.getValue();
        assertEquals(QueryDatasetStatus.PUBLISHED.name(), published.getStatus());
        assertEquals(2, published.getVersion().intValue());
        assertNotNull(published.getSourceSchemaHash());
        assertFalse(published.getSourceSchemaHash().isBlank());
    }

    @Test
    void publishInvalidDatasetThrowsPublishFailed() {
        QueryDataset draft = validDataset();
        draft.setId(1L);
        draft.setFields(null);
        when(storageMock.getById(1L)).thenReturn(draft);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.publish(1L));

        assertEquals(ErrorCode.DS_PUBLISH_FAILED.getCode(), ex.getErrorCode());
        verify(storageMock, never()).save(any());
    }

    @Test
    void disableMarksDisabled() {
        QueryDataset published = validDataset();
        published.setId(1L);
        published.setStatus(QueryDatasetStatus.PUBLISHED.name());
        when(storageMock.getById(1L)).thenReturn(published);
        stubSaveAssignsId(1L);

        service.disable(1L);

        ArgumentCaptor<QueryDataset> captor = ArgumentCaptor.forClass(QueryDataset.class);
        verify(storageMock).save(captor.capture());
        assertEquals(QueryDatasetStatus.DISABLED.name(), captor.getValue().getStatus());
    }

    // ── validate ─────────────────────────────────────────────────

    @Test
    void validateReturnsCollectedErrors() {
        QueryDataset broken = validDataset();
        broken.setId(1L);
        broken.getFields().get(0).setAggregation(null); // MEASURE without aggregation
        broken.getFields().get(1).setAggregation("SUM"); // VARCHAR with SUM
        when(storageMock.getById(1L)).thenReturn(broken);

        List<ErrorCode> errors = service.validate(1L);

        assertEquals(2, errors.size());
        assertTrue(errors.contains(ErrorCode.DS_INVALID_AGGREGATION));
        assertTrue(errors.contains(ErrorCode.DS_TEXT_AGGREGATION));
    }

    @Test
    void validateValidDatasetReturnsEmptyList() {
        QueryDataset good = validDataset();
        good.setId(1L);
        when(storageMock.getById(1L)).thenReturn(good);

        assertTrue(service.validate(1L).isEmpty());
    }

    // ── preview ──────────────────────────────────────────────────

    @Test
    void previewReturnsEmptyResultShape() {
        QueryDataset dataset = validDataset();
        dataset.setId(1L);
        when(storageMock.getById(1L)).thenReturn(dataset);
        service.executor = mock(Chat2DBSqlExecutor.class);
        when(service.executor.execute(any(ExecuteQueryRequest.class)))
                .thenReturn(QueryResult.builder()
                        .columns(List.of("Amount", "Region"))
                        .rows(Collections.emptyList())
                        .total(0L)
                        .build());

        PreviewResult result = service.preview(1L, 1, 20);

        assertTrue(result.getRows().isEmpty());
        assertEquals(0L, result.getTotal());
        assertEquals(1, result.getPageNo());
        assertEquals(20, result.getPageSize());
        assertEquals(2, result.getColumns().size());
        assertEquals("Amount", result.getColumns().get(0));
        assertEquals("Region", result.getColumns().get(1));
    }

    @Test
    void previewWithoutFieldsThrows() {
        QueryDataset dataset = validDataset();
        dataset.setId(1L);
        dataset.setFields(null);
        when(storageMock.getById(1L)).thenReturn(dataset);
        service.executor = mock(Chat2DBSqlExecutor.class);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.preview(1L, 1, 20));

        assertEquals(ErrorCode.DS_NO_FIELDS.getCode(), ex.getErrorCode());
    }

    // ── preview with SQL executor (T8) ──────────────────────────

    @Test
    void previewExecutesGeneratedSqlAndMapsRows() {
        QueryDataset dataset = validDataset();
        dataset.setId(1L);
        when(storageMock.getById(1L)).thenReturn(dataset);
        Chat2DBSqlExecutor executorMock = mock(Chat2DBSqlExecutor.class);
        service.executor = executorMock;
        when(executorMock.execute(any(ExecuteQueryRequest.class)))
                .thenReturn(QueryResult.builder()
                        .columns(List.of("amount", "region"))
                        .rows(List.of(List.of(100, "EU"), List.of(200, "US")))
                        .total(2L)
                        .build());

        PreviewResult result = service.preview(1L, 1, 20);

        assertEquals(2, result.getRows().size());
        assertEquals(100, result.getRows().get(0).get("Amount"));
        assertEquals("EU", result.getRows().get(0).get("Region"));
        assertEquals(2L, result.getTotal());
        assertEquals(1, result.getPageNo());
        assertEquals(20, result.getPageSize());
        assertEquals("Amount", result.getColumns().get(0));
        assertEquals("Region", result.getColumns().get(1));
    }

    @Test
    void previewPassesGeneratedSqlAndTimeoutToExecutor() {
        QueryDataset dataset = validDataset();
        dataset.setId(1L);
        when(storageMock.getById(1L)).thenReturn(dataset);
        Chat2DBSqlExecutor executorMock = mock(Chat2DBSqlExecutor.class);
        service.executor = executorMock;
        when(executorMock.execute(any(ExecuteQueryRequest.class)))
                .thenReturn(QueryResult.builder().columns(List.of()).rows(List.of()).total(0L).build());

        service.preview(1L, 2, 10);

        ArgumentCaptor<ExecuteQueryRequest> captor = ArgumentCaptor.forClass(ExecuteQueryRequest.class);
        verify(executorMock).execute(captor.capture());

        ExecuteQueryRequest req = captor.getValue();
        String sql = req.getSql();
        assertTrue(sql.startsWith("SELECT "));
        assertTrue(sql.contains("FROM `test_db`.`sales`"));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
        assertEquals(2, req.getParams().size());
        assertEquals(10, req.getParams().get(0));
        assertEquals(10L, req.getParams().get(1));
        assertEquals(QueryExcelConstants.QUERY_TIMEOUT_MS, req.getTimeoutMs());
        assertEquals(1L, req.getDatasourceId());
        assertEquals("test_db", req.getDatabaseName());
        assertEquals("sales", req.getTableName());
    }

    @Test
    void previewWithoutExecutorThrowsUnsupportedOperation() {
        QueryDataset dataset = validDataset();
        dataset.setId(1L);
        when(storageMock.getById(1L)).thenReturn(dataset);

        assertThrows(UnsupportedOperationException.class, () -> service.preview(1L, 1, 20));
    }

    @Test
    void previewWithTimeoutErrorThrowsQueryTimeout() {
        QueryDataset dataset = validDataset();
        dataset.setId(1L);
        when(storageMock.getById(1L)).thenReturn(dataset);
        Chat2DBSqlExecutor executorMock = mock(Chat2DBSqlExecutor.class);
        service.executor = executorMock;
        when(executorMock.execute(any(ExecuteQueryRequest.class)))
                .thenThrow(new QueryExcelException(ErrorCode.EX_QUERY_TIMEOUT.getCode(),
                        ErrorCode.EX_QUERY_TIMEOUT.getMessage()));

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.preview(1L, 1, 20));

        assertEquals(ErrorCode.EX_QUERY_TIMEOUT.getCode(), ex.getErrorCode());
    }

    @Test
    void previewWithUnknownDatasetThrowsNotFound() {
        when(storageMock.getById(999L)).thenReturn(null);
        service.executor = mock(Chat2DBSqlExecutor.class);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.preview(999L, 1, 20));

        assertEquals(ErrorCode.DS_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    // ── delete ───────────────────────────────────────────────────

    @Test
    void deleteUsesStorageWhenFound() {
        QueryDataset dataset = validDataset();
        dataset.setId(1L);
        when(storageMock.getById(1L)).thenReturn(dataset);

        service.delete(1L);

        verify(storageMock).delete(1L);
    }

    @Test
    void deleteUnknownThrowsNotFound() {
        when(storageMock.getById(999L)).thenReturn(null);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.delete(999L));

        assertEquals(ErrorCode.DS_NOT_FOUND.getCode(), ex.getErrorCode());
        verify(storageMock, never()).delete(any());
    }

    // ── real storage integration ─────────────────────────────────

    @Test
    void deleteRemovesDatasetFromRealStorage() {
        QueryDatasetServiceImpl real = new QueryDatasetServiceImpl();
        QueryDataset dataset = validDataset();
        dataset.setName("it-delete-" + UUID.randomUUID());

        Long id = real.create(dataset);

        assertNotNull(id);
        assertNotNull(real.getById(id));
        real.delete(id);
        assertNull(real.getById(id));
    }

    @Test
    void listPaginatesAndFiltersAcrossWorkspacesAndSearchKey() {
        QueryDatasetServiceImpl real = new QueryDatasetServiceImpl();
        String marker = "it-list-" + UUID.randomUUID();
        Long workspace = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        List<Long> ids = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                QueryDataset d = validDataset();
                d.setName(marker + "-" + i);
                d.setWorkspaceId(workspace);
                ids.add(real.create(d));
            }
            QueryDataset other = validDataset();
            other.setName(marker + "-other-workspace");
            other.setWorkspaceId(workspace + 1);
            ids.add(real.create(other));

            PageResponse<QueryDataset> page1 = real.list(workspace, 1, 2, null);
            assertEquals(5L, page1.getTotal());
            assertEquals(2, page1.getData().size());
            assertTrue(page1.getHasNextPage());

            PageResponse<QueryDataset> page3 = real.list(workspace, 3, 2, null);
            assertEquals(1, page3.getData().size());
            assertFalse(page3.getHasNextPage());

            PageResponse<QueryDataset> bySearch = real.list(null, 1, 100, marker);
            assertEquals(6L, bySearch.getTotal());
            assertTrue(bySearch.getData().stream().allMatch(d -> d.getName().contains(marker)));
        } finally {
            for (Long id : ids) {
                if (id != null) {
                    real.delete(id);
                }
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────

    private static QueryDataset validDataset() {
        QueryDataset dataset = new QueryDataset();
        dataset.setName("Test Dataset");
        dataset.setDatasourceId(1L);
        dataset.setTableName("sales");
        dataset.setDatabaseName("test_db");
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
        when(storageMock.save(any(QueryDataset.class))).thenAnswer(inv -> {
            QueryDataset d = inv.getArgument(0);
            d.setId(id);
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