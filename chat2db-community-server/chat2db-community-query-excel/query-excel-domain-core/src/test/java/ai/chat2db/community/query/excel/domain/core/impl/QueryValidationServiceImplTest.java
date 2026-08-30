package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.QueryExcelConstants;
import ai.chat2db.community.query.excel.domain.api.enums.FieldRole;
import ai.chat2db.community.query.excel.domain.api.enums.FilterOperator;
import ai.chat2db.community.query.excel.domain.api.enums.FilterType;
import ai.chat2db.community.query.excel.domain.api.enums.QueryDatasetStatus;
import ai.chat2db.community.query.excel.domain.api.model.ColumnInfo;
import ai.chat2db.community.query.excel.domain.api.model.DatasetFilter;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.model.ViewMeasure;
import ai.chat2db.community.query.excel.domain.api.model.ViewSort;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBMetadataProvider;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.PluginCapabilityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryValidationServiceImplTest {

    private QueryValidationServiceImpl service;
    private Chat2DBMetadataProvider metadataProvider;
    private QueryValidationServiceImpl viewService;
    private IQueryDatasetService datasetService;
    private PluginCapabilityProvider pluginCapabilityProvider;

    @BeforeEach
    void setUp() {
        metadataProvider = mock(Chat2DBMetadataProvider.class);
        lenient().when(metadataProvider.testConnection(anyLong())).thenReturn(true);
        lenient().when(metadataProvider.getTableColumns(anyLong(), any(), any(), any()))
                .thenReturn(List.of(columnInfo("amount", "DECIMAL"), columnInfo("region", "VARCHAR")));
        service = new QueryValidationServiceImpl(metadataProvider);
    }

    @BeforeEach
    void setUpViewService() {
        datasetService = mock(IQueryDatasetService.class);
        lenient().when(datasetService.getById(anyLong())).thenReturn(publishedDataset());
        pluginCapabilityProvider = mock(PluginCapabilityProvider.class);
        lenient().when(pluginCapabilityProvider.supportsAggregation(anyLong(), any())).thenReturn(true);
        lenient().when(pluginCapabilityProvider.supportsPagination(anyLong())).thenReturn(true);
        lenient().when(pluginCapabilityProvider.supportsDateOperators(anyLong())).thenReturn(true);
        lenient().when(pluginCapabilityProvider.supportsIdentifierQuoting(anyLong())).thenReturn(true);
        viewService = new QueryValidationServiceImpl(metadataProvider, datasetService, pluginCapabilityProvider);
    }

    // ── validateDatasetForPublish ────────────────────────────────

    @Test
    void validDatasetReturnsEmptyList() {
        QueryDataset dataset = validDataset();

        assertTrue(service.validateDatasetForPublish(dataset).isEmpty());
    }

    @Test
    void datasetWithZeroFieldsReturnsNoFields() {
        QueryDataset dataset = validDataset();
        dataset.setFields(Collections.emptyList());

        List<ErrorCode> errors = service.validateDatasetForPublish(dataset);

        assertEquals(List.of(ErrorCode.DS_NO_FIELDS), errors);
    }

    @Test
    void measureFieldWithoutAggregationReturnsInvalidAggregation() {
        QueryDataset dataset = validDataset();
        dataset.getFields().get(0).setAggregation(null);

        List<ErrorCode> errors = service.validateDatasetForPublish(dataset);

        assertEquals(1, errors.size());
        assertEquals(ErrorCode.DS_INVALID_AGGREGATION, errors.get(0));
    }

    @Test
    void textFieldWithSumReturnsTextAggregation() {
        QueryDataset dataset = validDataset();
        dataset.getFields().get(1).setAggregation("SUM");

        List<ErrorCode> errors = service.validateDatasetForPublish(dataset);

        assertEquals(1, errors.size());
        assertEquals(ErrorCode.DS_TEXT_AGGREGATION, errors.get(0));
    }

    @Test
    void filterOnUnknownFieldReturnsNotFilterable() {
        QueryDataset dataset = validDataset();
        DatasetFilter filter = new DatasetFilter();
        filter.setFieldId("missing");
        dataset.setBaseFilters(List.of(filter));

        List<ErrorCode> errors = service.validateDatasetForPublish(dataset);

        assertEquals(1, errors.size());
        assertEquals(ErrorCode.DS_FILTER_FIELD_NOT_FILTERABLE, errors.get(0));
    }

    @Test
    void filterOnNonFilterableFieldReturnsNotFilterable() {
        QueryDataset dataset = validDataset();
        dataset.getFields().get(0).setFilterable(false);
        DatasetFilter filter = new DatasetFilter();
        filter.setFieldId("f1");
        dataset.setBaseFilters(List.of(filter));

        List<ErrorCode> errors = service.validateDatasetForPublish(dataset);

        assertEquals(1, errors.size());
        assertEquals(ErrorCode.DS_FILTER_FIELD_NOT_FILTERABLE, errors.get(0));
    }

    @Test
    void sortOnNonSortableFieldReturnsNotSortable() {
        QueryDatasetField f1 = field("f1", "amount", "DECIMAL", FieldRole.MEASURE.name(), "SUM", true, false);
        QueryDatasetField f2 = field("f2", "region", "VARCHAR", FieldRole.DIMENSION.name(), null, true, true);

        ViewSort sort = new ViewSort();
        sort.setFieldId("f1");
        sort.setDirection("ASC");

        List<ErrorCode> errors = service.validateSort(List.of(sort), List.of(f1, f2));

        assertEquals(1, errors.size());
        assertEquals(ErrorCode.DS_SORT_FIELD_NOT_SORTABLE, errors.get(0));
    }

    @Test
    void emptyColumnsFromProviderReturnsSourceTableDeleted() {
        when(metadataProvider.getTableColumns(anyLong(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        List<ErrorCode> errors = service.validateDatasetForPublish(validDataset());

        assertEquals(1, errors.size());
        assertEquals(ErrorCode.DS_SOURCE_TABLE_DELETED, errors.get(0));
    }

    @Test
    void providerColumnsMissingSourceColumnReturnsSourceFieldDeleted() {
        when(metadataProvider.getTableColumns(anyLong(), any(), any(), any()))
                .thenReturn(List.of(columnInfo("amount", "DECIMAL")));

        List<ErrorCode> errors = service.validateDatasetForPublish(validDataset());

        assertEquals(1, errors.size());
        assertEquals(ErrorCode.DS_SOURCE_FIELD_DELETED, errors.get(0));
    }

    @Test
    void connectionFailureReturnsConnectionFailed() {
        when(metadataProvider.testConnection(anyLong())).thenReturn(false);

        List<ErrorCode> errors = service.validateDatasetForPublish(validDataset());

        assertEquals(1, errors.size());
        assertEquals(ErrorCode.DS_CONNECTION_FAILED, errors.get(0));
    }

    // ── validateField ────────────────────────────────────────────

    @Test
    void numericWithSumIsValid() {
        QueryDatasetField field = field("f1", "amount", "DECIMAL", FieldRole.MEASURE.name(), "SUM", true, true);
        assertTrue(service.validateField(field, "DECIMAL"));
    }

    @Test
    void numericWithAvgIsValid() {
        QueryDatasetField field = field("f1", "amount", "DECIMAL", FieldRole.MEASURE.name(), "AVG", true, true);
        assertTrue(service.validateField(field, "DECIMAL"));
    }

    @Test
    void textWithCountIsValid() {
        QueryDatasetField field = field("f2", "region", "VARCHAR", FieldRole.DIMENSION.name(), "COUNT", true, true);
        assertTrue(service.validateField(field, "VARCHAR"));
    }

    @Test
    void textWithSumIsInvalid() {
        QueryDatasetField field = field("f2", "region", "VARCHAR", FieldRole.DIMENSION.name(), "SUM", true, true);
        assertFalse(service.validateField(field, "VARCHAR"));
    }

    @Test
    void textWithAvgIsInvalid() {
        QueryDatasetField field = field("f2", "region", "VARCHAR", FieldRole.DIMENSION.name(), "AVG", true, true);
        assertFalse(service.validateField(field, "VARCHAR"));
    }

    // ── helpers ──────────────────────────────────────────────────

    private static QueryDataset validDataset() {
        QueryDataset dataset = new QueryDataset();
        dataset.setName("Test Dataset");
        dataset.setDatasourceId(1L);
        dataset.setTableName("test_table");
        dataset.setFields(new ArrayList<>(Arrays.asList(
                field("f1", "amount", "DECIMAL", FieldRole.MEASURE.name(), "SUM", true, true),
                field("f2", "region", "VARCHAR", FieldRole.DIMENSION.name(), null, true, true))));
        return dataset;
    }

    private static QueryDatasetField field(String fieldId, String sourceColumn, String dataType,
                                           String role, String aggregation,
                                           boolean filterable, boolean sortable) {
        QueryDatasetField f = new QueryDatasetField();
        f.setFieldId(fieldId);
        f.setSourceColumn(sourceColumn);
        f.setDisplayName(sourceColumn);
        f.setDataType(dataType);
        f.setRole(role);
        f.setAggregation(aggregation);
        f.setFilterable(filterable);
        f.setSortable(sortable);
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

    // ── validateView (§6.4) ──────────────────────────────────────

    @Test
    void validateViewValidReturnsEmptyErrors() {
        SavedQueryView view = baseView();
        ViewFilter filter = new ViewFilter();
        filter.setFieldId("f1");
        filter.setFilterType(FilterType.TEXT.name());
        filter.setOperator(FilterOperator.EQ.name());
        filter.setValue("east");
        view.setFilters(List.of(filter));

        ViewSort sort = new ViewSort();
        sort.setFieldId("f2");
        sort.setDirection("ASC");
        view.setSort(List.of(sort));

        assertTrue(viewService.validateView(view).isEmpty());
    }

    @Test
    void validateViewNonPublishedDatasetReturnsNotPublished() {
        QueryDataset draft = publishedDataset();
        draft.setStatus(QueryDatasetStatus.DRAFT.name());
        when(datasetService.getById(anyLong())).thenReturn(draft);

        List<ErrorCode> errors = viewService.validateView(baseView());

        assertEquals(1, errors.size());
        assertEquals(ErrorCode.QV_DATASET_NOT_PUBLISHED, errors.get(0));
    }

    @Test
    void validateViewMissingDatasetReturnsNotPublished() {
        when(datasetService.getById(anyLong())).thenReturn(null);

        List<ErrorCode> errors = viewService.validateView(baseView());

        assertEquals(List.of(ErrorCode.QV_DATASET_NOT_PUBLISHED), errors);
    }

    @Test
    void validateViewNonExistentFieldReturnsFieldNotFound() {
        SavedQueryView view = baseView();
        ViewFilter filter = new ViewFilter();
        filter.setFieldId("missing");
        filter.setOperator(FilterOperator.EQ.name());
        view.setFilters(List.of(filter));

        List<ErrorCode> errors = viewService.validateView(view);

        assertTrue(errors.contains(ErrorCode.QV_FIELD_NOT_FOUND));
    }

    @Test
    void validateViewTextFieldWithGtReturnsInvalidFilterField() {
        SavedQueryView view = baseView();
        ViewFilter filter = new ViewFilter();
        filter.setFieldId("f1"); // VARCHAR → TEXT
        filter.setOperator(FilterOperator.GT.name());
        view.setFilters(List.of(filter));

        List<ErrorCode> errors = viewService.validateView(view);

        assertTrue(errors.contains(ErrorCode.QV_INVALID_FILTER_FIELD));
    }

    @Test
    void validateViewNumericFieldWithContainsReturnsInvalidFilterField() {
        SavedQueryView view = baseView();
        ViewFilter filter = new ViewFilter();
        filter.setFieldId("f2"); // DECIMAL → NUMERIC
        filter.setFilterType(FilterType.NUMERIC.name());
        filter.setOperator(FilterOperator.CONTAINS.name());
        view.setFilters(List.of(filter));

        List<ErrorCode> errors = viewService.validateView(view);

        assertTrue(errors.contains(ErrorCode.QV_INVALID_FILTER_FIELD));
    }

    @Test
    void validateViewDateFieldWithEqIsValid() {
        SavedQueryView view = baseView();
        ViewFilter filter = new ViewFilter();
        filter.setFieldId("f3"); // DATE
        filter.setOperator(FilterOperator.EQ.name());
        view.setFilters(List.of(filter));

        assertTrue(viewService.validateView(view).isEmpty());
    }

    @Test
    void validateViewNonSortableSortReturnsInvalidSortField() {
        QueryDataset dataset = publishedDataset();
        dataset.getFields().get(0).setSortable(false); // f1 no longer sortable
        when(datasetService.getById(anyLong())).thenReturn(dataset);

        SavedQueryView view = baseView();
        ViewSort sort = new ViewSort();
        sort.setFieldId("f1");
        sort.setDirection("ASC");
        view.setSort(List.of(sort));

        List<ErrorCode> errors = viewService.validateView(view);

        assertTrue(errors.contains(ErrorCode.QV_INVALID_SORT_FIELD));
    }

    @Test
    void validateViewPageSizeOverMaxReturnsPageSizeExceeded() {
        SavedQueryView view = baseView();
        view.setPageSize(QueryExcelConstants.MAX_PAGE_SIZE + 1);

        List<ErrorCode> errors = viewService.validateView(view);

        assertTrue(errors.contains(ErrorCode.QV_PAGE_SIZE_EXCEEDED));
    }

    // ── validateViewFilters / validateViewSort (standalone) ─────

    @Test
    void validateViewFiltersStandaloneReturnsInvalidFilterField() {
        SavedQueryView view = new SavedQueryView();
        ViewFilter filter = new ViewFilter();
        filter.setFieldId("f1"); // TEXT
        filter.setOperator(FilterOperator.GT.name()); // invalid for TEXT
        view.setFilters(List.of(filter));

        List<ErrorCode> errors = viewService.validateViewFilters(view, publishedDataset().getFields());

        assertEquals(List.of(ErrorCode.QV_INVALID_FILTER_FIELD), errors);
    }

    @Test
    void validateViewFiltersStandaloneMissingFieldReturnsFieldNotFound() {
        SavedQueryView view = new SavedQueryView();
        ViewFilter filter = new ViewFilter();
        filter.setFieldId("missing");
        filter.setOperator(FilterOperator.EQ.name());
        view.setFilters(List.of(filter));

        List<ErrorCode> errors = viewService.validateViewFilters(view, publishedDataset().getFields());

        assertEquals(List.of(ErrorCode.QV_FIELD_NOT_FOUND), errors);
    }

    @Test
    void validateViewSortStandaloneReturnsInvalidSortField() {
        List<QueryDatasetField> fields = publishedDataset().getFields();
        fields.get(0).setSortable(false); // f1 no longer sortable

        SavedQueryView view = new SavedQueryView();
        ViewSort sort = new ViewSort();
        sort.setFieldId("f1");
        view.setSort(List.of(sort));

        List<ErrorCode> errors = viewService.validateViewSort(view, fields);

        assertEquals(List.of(ErrorCode.QV_INVALID_SORT_FIELD), errors);
    }

    @Test
    void validateViewSortStandaloneMissingFieldReturnsFieldNotFound() {
        SavedQueryView view = new SavedQueryView();
        ViewSort sort = new ViewSort();
        sort.setFieldId("missing");
        view.setSort(List.of(sort));

        List<ErrorCode> errors = viewService.validateViewSort(view, publishedDataset().getFields());

        assertEquals(List.of(ErrorCode.QV_FIELD_NOT_FOUND), errors);
    }

    // ── validateViewCompatibility ────────────────────────────────

    @Test
    void validateViewCompatibilitySameVersionReturnsTrue() {
        assertTrue(viewService.validateViewCompatibility(baseView(), publishedDataset()));
    }

    @Test
    void validateViewCompatibilityVersionChangedFieldStillExistsReturnsTrue() {
        QueryDataset dataset = publishedDataset();
        dataset.setVersion(2); // version changed, but referenced fields remain

        assertTrue(viewService.validateViewCompatibility(baseView(), dataset));
    }

    @Test
    void validateViewCompatibilityVersionChangedFieldRemovedReturnsFalse() {
        QueryDataset dataset = publishedDataset();
        dataset.setVersion(2);
        dataset.setFields(List.of(
                field("f2", "amount", "DECIMAL", FieldRole.MEASURE.name(), "SUM", true, true))); // f1 removed

        assertFalse(viewService.validateViewCompatibility(baseView(), dataset));
    }

    // ── plugin capability ────────────────────────────────────────

    @Test
    void validateViewUnsupportedAggregationReturnsPluginError() {
        PluginCapabilityProvider strictPlugin = mock(PluginCapabilityProvider.class);
        when(strictPlugin.supportsAggregation(anyLong(), any())).thenReturn(false);
        when(strictPlugin.supportsPagination(anyLong())).thenReturn(true);
        when(strictPlugin.supportsDateOperators(anyLong())).thenReturn(true);
        when(strictPlugin.supportsIdentifierQuoting(anyLong())).thenReturn(true);
        QueryValidationServiceImpl strictService =
                new QueryValidationServiceImpl(metadataProvider, datasetService, strictPlugin);

        SavedQueryView view = baseView();
        ViewMeasure measure = new ViewMeasure();
        measure.setFieldId("f2");
        measure.setAggregation("SUM");
        view.setMeasures(List.of(measure));

        List<ErrorCode> errors = strictService.validateView(view);

        assertTrue(errors.contains(ErrorCode.QV_PLUGIN_CAPABILITY_UNSUPPORTED));
    }

    // ── view helpers ─────────────────────────────────────────────

    private static QueryDataset publishedDataset() {
        QueryDataset dataset = new QueryDataset();
        dataset.setId(1L);
        dataset.setName("Published Dataset");
        dataset.setDatasourceId(1L);
        dataset.setStatus(QueryDatasetStatus.PUBLISHED.name());
        dataset.setVersion(1);
        dataset.setTableName("test_table");
        dataset.setFields(new ArrayList<>(Arrays.asList(
                field("f1", "region", "VARCHAR", FieldRole.DIMENSION.name(), null, true, true),
                field("f2", "amount", "DECIMAL", FieldRole.MEASURE.name(), "SUM", true, true),
                field("f3", "created", "DATE", FieldRole.DIMENSION.name(), null, true, true),
                field("f4", "active", "BOOLEAN", FieldRole.DIMENSION.name(), null, true, true))));
        return dataset;
    }

    private static SavedQueryView baseView() {
        SavedQueryView view = new SavedQueryView();
        view.setDatasetId(1L);
        view.setDatasetVersion(1);
        view.setRowFields(new ArrayList<>(List.of("f1")));
        view.setColumnFields(new ArrayList<>(List.of("f2")));
        view.setPageSize(100);
        return view;
    }
}