package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.enums.FieldRole;
import ai.chat2db.community.query.excel.domain.api.enums.QueryDatasetStatus;
import ai.chat2db.community.query.excel.domain.api.model.ColumnInfo;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBMetadataProvider;
import ai.chat2db.community.query.excel.domain.api.service.IQueryValidationService;
import ai.chat2db.community.query.excel.storage.QueryDatasetStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * T7: publish/versioning/source-schema-hash — full validation via
 * {@link IQueryValidationService}, published-dataset immutability and
 * {@code checkSourceChanged}.
 */
class QueryDatasetPublishTest {

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
                .thenReturn(Collections.emptyList());
        lenient().when(storageMock.save(any(QueryDataset.class))).thenAnswer(inv -> {
            QueryDataset d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(1L);
            }
            return d.getId();
        });
        lenient().doAnswer(inv -> {
            QueryDataset d = inv.getArgument(0);
            d.setId(d.getId() == null ? 1L : d.getId());
            return null;
        }).when(storageMock).update(any(QueryDataset.class));
    }

    // ── publish ──────────────────────────────────────────────────

    @Test
    void publishValidDraftSetsPublishedVersionAndHash() {
        QueryDataset draft = validDataset();
        draft.setId(1L);
        draft.setStatus(QueryDatasetStatus.DRAFT.name());
        draft.setVersion(1);
        when(storageMock.getById(1L)).thenReturn(draft);

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
    void publishNullVersionStartsAtOne() {
        QueryDataset draft = validDataset();
        draft.setId(1L);
        draft.setVersion(null);
        when(storageMock.getById(1L)).thenReturn(draft);

        service.publish(1L);

        ArgumentCaptor<QueryDataset> captor = ArgumentCaptor.forClass(QueryDataset.class);
        verify(storageMock).save(captor.capture());
        assertEquals(1, captor.getValue().getVersion().intValue());
    }

    @Test
    void publishWithValidationFailureThrowsPublishFailed() {
        QueryDataset draft = validDataset();
        draft.setId(1L);
        when(storageMock.getById(1L)).thenReturn(draft);
        when(validationMock.validateDatasetForPublish(any(QueryDataset.class)))
                .thenReturn(List.of(ErrorCode.DS_NO_FIELDS));

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.publish(1L));

        assertEquals(ErrorCode.DS_PUBLISH_FAILED.getCode(), ex.getErrorCode());
        assertTrue(ex.getMessage().contains(ErrorCode.DS_NO_FIELDS.getMessage()));
        verify(storageMock, never()).save(any());
    }

    @Test
    void republishAlreadyPublishedBumpsVersionAgain() {
        QueryDataset published = validDataset();
        published.setId(1L);
        published.setStatus(QueryDatasetStatus.PUBLISHED.name());
        published.setVersion(2);
        when(storageMock.getById(1L)).thenReturn(published);

        service.publish(1L);

        ArgumentCaptor<QueryDataset> captor = ArgumentCaptor.forClass(QueryDataset.class);
        verify(storageMock).save(captor.capture());
        assertEquals(QueryDatasetStatus.PUBLISHED.name(), captor.getValue().getStatus());
        assertEquals(3, captor.getValue().getVersion().intValue());
    }

    // ── immutability ─────────────────────────────────────────────

    @Test
    void updatePublishedDatasetThrowsImmutabilityError() {
        QueryDataset published = validDataset();
        published.setId(1L);
        published.setStatus(QueryDatasetStatus.PUBLISHED.name());
        published.setVersion(2);
        when(storageMock.getById(1L)).thenReturn(published);

        QueryDataset edit = validDataset();
        edit.setId(1L);
        edit.setVersion(2);
        edit.setName("Renamed");

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.update(edit));

        assertEquals(ErrorCode.DS_PUBLISH_FAILED.getCode(), ex.getErrorCode());
        assertTrue(ex.getMessage().toLowerCase().contains("immutable"));
        verify(storageMock, never()).update(any());
    }

    @Test
    void updateDraftStillWorks() {
        QueryDataset stored = validDataset();
        stored.setId(1L);
        stored.setStatus(QueryDatasetStatus.DRAFT.name());
        stored.setVersion(1);
        when(storageMock.getById(1L)).thenReturn(stored);

        QueryDataset edit = validDataset();
        edit.setId(1L);
        edit.setVersion(1);
        edit.setName("Renamed");

        service.update(edit);

        ArgumentCaptor<QueryDataset> captor = ArgumentCaptor.forClass(QueryDataset.class);
        verify(storageMock).update(captor.capture());
        assertEquals("Renamed", captor.getValue().getName());
        assertEquals(QueryDatasetStatus.DRAFT.name(), captor.getValue().getStatus());
    }

    // ── checkSourceChanged ───────────────────────────────────────

    @Test
    void checkSourceChangedSameColumnsReturnsFalse() {
        QueryDataset published = validDataset();
        published.setId(1L);
        when(storageMock.getById(1L)).thenReturn(published);
        when(metadataMock.getTableColumns(anyLong(), any(), any(), any()))
                .thenReturn(List.of(columnInfo("amount", "DECIMAL"), columnInfo("region", "VARCHAR")));
        published.setSourceSchemaHash(computeHashFor(published));

        assertFalse(service.checkSourceChanged(1L));
        verify(storageMock, never()).update(any());
    }

    @Test
    void checkSourceChangedDifferentColumnsReturnsTrueAndRefreshesHash() {
        QueryDataset published = validDataset();
        published.setId(1L);
        when(storageMock.getById(1L)).thenReturn(published);
        when(metadataMock.getTableColumns(anyLong(), any(), any(), any()))
                .thenReturn(List.of(columnInfo("amount", "DECIMAL"), columnInfo("region", "VARCHAR")));
        published.setSourceSchemaHash("stale-hash");

        assertTrue(service.checkSourceChanged(1L));

        ArgumentCaptor<QueryDataset> captor = ArgumentCaptor.forClass(QueryDataset.class);
        verify(storageMock).update(captor.capture());
        assertNotNull(captor.getValue().getSourceSchemaHash());
        assertEquals(computeHashFor(published), captor.getValue().getSourceSchemaHash());
    }

    @Test
    void checkSourceChangedUnknownDatasetThrowsNotFound() {
        when(storageMock.getById(999L)).thenReturn(null);

        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> service.checkSourceChanged(999L));

        assertEquals(ErrorCode.DS_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    // ── hash ─────────────────────────────────────────────────────

    @Test
    void hashIsDeterministicForSameDataset() {
        QueryDataset first = validDataset();
        QueryDataset second = validDataset();

        assertEquals(QueryDatasetServiceImpl.computeSourceSchemaHash(first),
                QueryDatasetServiceImpl.computeSourceSchemaHash(second));
    }

    @Test
    void hashChangesWhenFieldSetChanges() {
        QueryDataset original = validDataset();
        QueryDataset altered = validDataset();
        altered.getFields().add(field("f3", "quantity", "INTEGER", FieldRole.MEASURE.name(), "SUM"));

        assertEquals(64, QueryDatasetServiceImpl.computeSourceSchemaHash(original).length());
        assertFalse(QueryDatasetServiceImpl.computeSourceSchemaHash(original)
                .equals(QueryDatasetServiceImpl.computeSourceSchemaHash(altered)));
    }

    // ── disable ──────────────────────────────────────────────────

    @Test
    void disableMarksDisabled() {
        QueryDataset published = validDataset();
        published.setId(1L);
        published.setStatus(QueryDatasetStatus.PUBLISHED.name());
        when(storageMock.getById(1L)).thenReturn(published);

        service.disable(1L);

        ArgumentCaptor<QueryDataset> captor = ArgumentCaptor.forClass(QueryDataset.class);
        verify(storageMock).save(captor.capture());
        assertEquals(QueryDatasetStatus.DISABLED.name(), captor.getValue().getStatus());
    }

    // ── helpers ──────────────────────────────────────────────────

    private static QueryDataset validDataset() {
        QueryDataset dataset = new QueryDataset();
        dataset.setName("Test Dataset");
        dataset.setDatasourceId(1L);
        dataset.setTableName("sales");
        dataset.setFields(new ArrayList<>(Arrays.asList(
                field("f1", "amount", "DECIMAL", FieldRole.MEASURE.name(), "SUM"),
                field("f2", "region", "VARCHAR", FieldRole.DIMENSION.name(), null))));
        return dataset;
    }

    private static QueryDatasetField field(String fieldId, String sourceColumn, String dataType,
                                           String role, String aggregation) {
        QueryDatasetField f = new QueryDatasetField();
        f.setFieldId(fieldId);
        f.setSourceColumn(sourceColumn);
        f.setDisplayName(sourceColumn);
        f.setDataType(dataType);
        f.setRole(role);
        f.setAggregation(aggregation);
        f.setFilterable(true);
        f.setSortable(true);
        f.setVisible(true);
        return f;
    }

    private static ColumnInfo columnInfo(String name, String dataType) {
        ColumnInfo c = new ColumnInfo();
        c.setColumnName(name);
        c.setDataType(dataType);
        c.setNullable(false);
        return c;
    }

    private static String computeHashFor(QueryDataset dataset) {
        return QueryDatasetServiceImpl.computeSourceSchemaHash(dataset);
    }
}