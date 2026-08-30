package ai.chat2db.community.query.excel.domain.core.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.QueryExcelConstants;
import ai.chat2db.community.query.excel.domain.api.enums.FieldRole;
import ai.chat2db.community.query.excel.domain.api.enums.FilterOperator;
import ai.chat2db.community.query.excel.domain.api.enums.FilterType;
import ai.chat2db.community.query.excel.domain.api.model.ColumnInfo;
import ai.chat2db.community.query.excel.domain.api.model.DatasetFilter;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.ViewDimension;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.model.ViewMeasure;
import ai.chat2db.community.query.excel.domain.api.model.ViewSort;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBMetadataProvider;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.IQueryValidationService;
import ai.chat2db.community.query.excel.domain.api.service.PluginCapabilityProvider;
import org.springframework.stereotype.Service;

/**
 * Full domain implementation of {@link IQueryValidationService} (requirements §5.4/§5.6, §6.4).
 * <p>Every publish-readiness, field-compatibility, filter/sort-integrity, and
 * source-structure-existence rule is enforced here. The {@link Chat2DBMetadataProvider}
 * decouples validation from the actual datasource connection layer. The
 * {@link IQueryDatasetService} and {@link PluginCapabilityProvider} enable
 * SavedQueryView validation (§6.4).</p>
 */
@Service
public class QueryValidationServiceImpl implements IQueryValidationService {

    private static final Set<String> TEXT_TYPES = Set.of("VARCHAR", "TEXT", "CHAR", "STRING");

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Chat2DBMetadataProvider metadataProvider;
    /**
     * Dataset service — NOT autowired (would create circular dependency with
     * {@link QueryDatasetServiceImpl}). Only used for SavedQueryView validation
     * (via {@link #validateView(SavedQueryView)}); callers that need dataset
     * resolution already hold their own {@link IQueryDatasetService} reference.
     * When {@code null}, {@link #resolveDataset(SavedQueryView)} returns null
     * and view validation falls back to the caller's own dataset checks.
     */
    private IQueryDatasetService datasetService;
    private PluginCapabilityProvider pluginCapabilityProvider;

    /**
     * Spring-safe default wiring for the file-backed local runtime.
     */
    public QueryValidationServiceImpl() {
        this(new LocalMetadataProvider());
    }

    /**
     * Legacy constructor — used when only dataset validation is needed.
     * SavedQueryView validation methods will not be fully functional
     * (dataset lookup returns null, plugin checks default to unsupported).
     */
    public QueryValidationServiceImpl(Chat2DBMetadataProvider metadataProvider) {
        this(metadataProvider, null, new NoopPluginCapabilityProvider());
    }

    /**
     * Full constructor for SavedQueryView validation.
     *
     * @param metadataProvider          source metadata access
     * @param datasetService            dataset lookup for status/field checks
     * @param pluginCapabilityProvider  datasource plugin capability checks
     */
    public QueryValidationServiceImpl(Chat2DBMetadataProvider metadataProvider,
                                      IQueryDatasetService datasetService,
                                      PluginCapabilityProvider pluginCapabilityProvider) {
        this.metadataProvider = metadataProvider;
        this.datasetService = datasetService;
        this.pluginCapabilityProvider = pluginCapabilityProvider != null
                ? pluginCapabilityProvider : new NoopPluginCapabilityProvider();
    }

    @Override
    public List<ErrorCode> validateDatasetForPublish(QueryDataset dataset) {
        List<ErrorCode> errors = new ArrayList<>();

        // 1. Null checks — no fields means nothing to publish
        if (dataset == null || dataset.getFields() == null || dataset.getFields().isEmpty()) {
            errors.add(ErrorCode.DS_NO_FIELDS);
            return errors;
        }

        // 2. Field-level validation (§5.4)
        for (QueryDatasetField field : dataset.getFields()) {
            boolean measure = FieldRole.MEASURE.name().equals(field.getRole());
            if (measure && (field.getAggregation() == null || field.getAggregation().isBlank())) {
                errors.add(ErrorCode.DS_INVALID_AGGREGATION);
            }
            if (isTextType(field.getDataType()) && isSumOrAvg(field.getAggregation())) {
                errors.add(ErrorCode.DS_TEXT_AGGREGATION);
            }
        }

        // 3. Filter validation (§5.4)
        if (dataset.getBaseFilters() != null) {
            errors.addAll(validateFilters(dataset.getBaseFilters(), dataset.getFields()));
        }

        // 4. Sort validation — the dataset model carries no sort property natively;
        //    sort rules are enforced at the SavedQueryView level via validateSort().
        //    (See §5.4 note: sort references live on ViewSort, not QueryDataset.)

        // 5. Structural validation (§5.6)
        errors.addAll(validateStructure(dataset));

        return errors;
    }

    @Override
    public boolean validateField(QueryDatasetField field, String actualColumnType) {
        if (field == null || field.getAggregation() == null || field.getAggregation().isBlank()) {
            return true;
        }
        String type = actualColumnType == null ? "" : actualColumnType.toUpperCase();
        String agg = field.getAggregation().toUpperCase();

        // Text types reject SUM and AVG
        if (isTextType(type) && isSumOrAvg(agg)) {
            return false;
        }
        return true;
    }

    @Override
    public List<ErrorCode> validateFilters(List<DatasetFilter> filters, List<QueryDatasetField> availableFields) {
        if (filters == null || filters.isEmpty()) {
            return Collections.emptyList();
        }
        List<ErrorCode> errors = new ArrayList<>();
        for (DatasetFilter filter : filters) {
            QueryDatasetField field = findField(availableFields, filter.getFieldId());
            if (field == null || !Boolean.TRUE.equals(field.getFilterable())) {
                errors.add(ErrorCode.DS_FILTER_FIELD_NOT_FILTERABLE);
            }
        }
        return errors;
    }

    @Override
    public List<ErrorCode> validateSort(List<ViewSort> sort, List<QueryDatasetField> availableFields) {
        if (sort == null || sort.isEmpty()) {
            return Collections.emptyList();
        }
        List<ErrorCode> errors = new ArrayList<>();
        for (ViewSort s : sort) {
            QueryDatasetField field = findField(availableFields, s.getFieldId());
            if (field == null || !Boolean.TRUE.equals(field.getSortable())) {
                errors.add(ErrorCode.DS_SORT_FIELD_NOT_SORTABLE);
            }
        }
        return errors;
    }

    @Override
    public List<ErrorCode> validateStructure(QueryDataset dataset) {
        List<ErrorCode> errors = new ArrayList<>();
        if (dataset == null) {
            return errors;
        }

        // Connection check first
        if (metadataProvider == null) {
            errors.add(ErrorCode.DS_CONNECTION_FAILED);
            return errors;
        }

        try {
            if (!metadataProvider.testConnection(dataset.getDatasourceId())) {
                errors.add(ErrorCode.DS_CONNECTION_FAILED);
                return errors;
            }
        } catch (Exception e) {
            errors.add(ErrorCode.DS_CONNECTION_FAILED);
            return errors;
        }

        // Source table existence check
        if (dataset.getTableName() != null && !dataset.getTableName().isBlank()) {
            List<ColumnInfo> columns;
            try {
                columns = metadataProvider.getTableColumns(
                        dataset.getDatasourceId(),
                        dataset.getDatabaseName(),
                        dataset.getSchemaName(),
                        dataset.getTableName());
            } catch (Exception e) {
                errors.add(ErrorCode.DS_CONNECTION_FAILED);
                return errors;
            }

            if (columns == null || columns.isEmpty()) {
                errors.add(ErrorCode.DS_SOURCE_TABLE_DELETED);
                return errors;
            }

            // Source field existence check — every field.sourceColumn must
            // still be present in the live table schema.
            if (dataset.getFields() != null) {
                Set<String> columnNames = columns.stream()
                        .map(ColumnInfo::getColumnName)
                        .filter(Objects::nonNull)
                        .map(String::toUpperCase)
                        .collect(Collectors.toSet());

                for (QueryDatasetField field : dataset.getFields()) {
                    if (field.getSourceColumn() != null && !field.getSourceColumn().isBlank()) {
                        if (!columnNames.contains(field.getSourceColumn().toUpperCase())) {
                            errors.add(ErrorCode.DS_SOURCE_FIELD_DELETED);
                        }
                    }
                }
            }
        }

        return errors;
    }

    // ── SavedQueryView validation (§6.4) ──────────────────────────

    @Override
    public List<ErrorCode> validateView(SavedQueryView view) {
        List<ErrorCode> errors = new ArrayList<>();
        if (view == null) {
            return errors;
        }

        // 1. Load dataset — if not found → QV_DATASET_NOT_PUBLISHED
        QueryDataset dataset = resolveDataset(view);
        if (dataset == null) {
            errors.add(ErrorCode.QV_DATASET_NOT_PUBLISHED);
            return errors;
        }

        // 2. Dataset must be PUBLISHED
        if (!"PUBLISHED".equalsIgnoreCase(dataset.getStatus())) {
            errors.add(ErrorCode.QV_DATASET_NOT_PUBLISHED);
            return errors;
        }

        List<QueryDatasetField> datasetFields = dataset.getFields();
        if (datasetFields == null) {
            datasetFields = Collections.emptyList();
        }

        // 3. All fieldIds in dimensions, measures, filters, sort must exist
        errors.addAll(validateReferencedFields(view, datasetFields));

        // 4. Filter operators must match field type per §6.4.4
        errors.addAll(validateViewFilterOperatorCompatibility(view, datasetFields));

        // 5. Sort fields must be sortable
        errors.addAll(validateViewSortInternal(view, datasetFields));

        // 6. pageSize ≤ 1000
        if (view.getPageSize() != null && view.getPageSize() > QueryExcelConstants.MAX_PAGE_SIZE) {
            errors.add(ErrorCode.QV_PAGE_SIZE_EXCEEDED);
        }

        // 7. Plugin capability checks
        if (pluginCapabilityProvider != null) {
            errors.addAll(validatePluginCapability(view, dataset));
        }

        return errors;
    }

    @Override
    public List<ErrorCode> validateViewFilters(SavedQueryView view, List<QueryDatasetField> datasetFields) {
        List<ErrorCode> errors = new ArrayList<>();
        if (view == null || view.getFilters() == null || view.getFilters().isEmpty()) {
            return errors;
        }
        if (datasetFields == null) {
            datasetFields = Collections.emptyList();
        }

        // Check each filter field exists
        for (ViewFilter filter : view.getFilters()) {
            if (filter.getFieldId() == null) {
                continue;
            }
            QueryDatasetField field = findField(datasetFields, filter.getFieldId());
            if (field == null) {
                errors.add(ErrorCode.QV_FIELD_NOT_FOUND);
            }
        }

        // Check operator compatibility
        errors.addAll(validateViewFilterOperatorCompatibility(view, datasetFields));

        return errors;
    }

    @Override
    public List<ErrorCode> validateViewSort(SavedQueryView view, List<QueryDatasetField> datasetFields) {
        List<ErrorCode> errors = new ArrayList<>();
        if (view == null || view.getSort() == null || view.getSort().isEmpty()) {
            return errors;
        }
        if (datasetFields == null) {
            datasetFields = Collections.emptyList();
        }

        // Check each sort field exists and is sortable
        for (ViewSort s : view.getSort()) {
            if (s.getFieldId() == null) {
                continue;
            }
            QueryDatasetField field = findField(datasetFields, s.getFieldId());
            if (field == null) {
                errors.add(ErrorCode.QV_FIELD_NOT_FOUND);
            } else if (!Boolean.TRUE.equals(field.getSortable())) {
                errors.add(ErrorCode.QV_INVALID_SORT_FIELD);
            }
        }

        return errors;
    }

    @Override
    public boolean validateViewCompatibility(SavedQueryView view, QueryDataset dataset) {
        if (view == null || dataset == null) {
            return false;
        }

        // If dataset version hasn't changed, the view is compatible
        Integer viewVersion = view.getDatasetVersion();
        Integer datasetVersion = dataset.getVersion();
        if (viewVersion == null || datasetVersion == null) {
            return true;
        }
        if (viewVersion.equals(datasetVersion)) {
            return true;
        }

        // Version changed: check if any fields used by the view were removed
        List<QueryDatasetField> datasetFields = dataset.getFields();
        if (datasetFields == null) {
            // No fields in dataset — view references would be broken
            return false;
        }

        Set<String> availableFieldIds = datasetFields.stream()
                .map(QueryDatasetField::getFieldId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Collect all fieldIds referenced by the view
        for (String fieldId : collectReferencedFieldIds(view)) {
            if (!availableFieldIds.contains(fieldId)) {
                return false;
            }
        }

        return true;
    }

    // ── helpers ──────────────────────────────────────────────────

    /**
     * Resolves the dataset for a view, returning {@code null} when the dataset
     * service is unavailable or the dataset does not exist.
     */
    private QueryDataset resolveDataset(SavedQueryView view) {
        if (view.getDatasetId() == null || datasetService == null) {
            return null;
        }
        return datasetService.getById(view.getDatasetId());
    }

    /**
     * Validates that every fieldId referenced by dimensions, measures, filters,
     * and sort entries exists in the dataset's field list.
     */
    private List<ErrorCode> validateReferencedFields(SavedQueryView view, List<QueryDatasetField> datasetFields) {
        List<ErrorCode> errors = new ArrayList<>();
        Set<String> availableFieldIds = datasetFields.stream()
                .map(QueryDatasetField::getFieldId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String refId : collectReferencedFieldIds(view)) {
            if (!availableFieldIds.contains(refId)) {
                errors.add(ErrorCode.QV_FIELD_NOT_FOUND);
            }
        }
        return errors;
    }

    /**
     * Collects all non-null fieldIds from the view's dimensions, measures,
     * filters, and sort entries.
     */
    private static Set<String> collectReferencedFieldIds(SavedQueryView view) {
        Set<String> ids = new HashSet<>();
        if (view.getRowFields() != null) {
            ids.addAll(view.getRowFields().stream()
                    .filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        if (view.getColumnFields() != null) {
            ids.addAll(view.getColumnFields().stream()
                    .filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        if (view.getDimensions() != null) {
            ids.addAll(view.getDimensions().stream()
                    .map(ViewDimension::getFieldId).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        if (view.getMeasures() != null) {
            ids.addAll(view.getMeasures().stream()
                    .map(ViewMeasure::getFieldId).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        if (view.getFilters() != null) {
            ids.addAll(view.getFilters().stream()
                    .map(ViewFilter::getFieldId).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        if (view.getSort() != null) {
            ids.addAll(view.getSort().stream()
                    .map(ViewSort::getFieldId).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        return ids;
    }

    /**
     * Validates that every filter operator is compatible with its field type
     * per §6.4.4. The {@link ViewFilter#filterType} field is used to determine
     * the type category; if absent, the field's {@code dataType} is used as a
     * fallback.
     */
    private List<ErrorCode> validateViewFilterOperatorCompatibility(
            SavedQueryView view, List<QueryDatasetField> datasetFields) {
        List<ErrorCode> errors = new ArrayList<>();
        if (view.getFilters() == null || datasetFields.isEmpty()) {
            return errors;
        }

        for (ViewFilter filter : view.getFilters()) {
            if (filter.getFieldId() == null || filter.getOperator() == null) {
                continue;
            }

            if (!isOperatorCompatibleWithFieldType(filter, datasetFields)) {
                errors.add(ErrorCode.QV_INVALID_FILTER_FIELD);
            }
        }

        return errors;
    }

    /**
     * Returns {@code true} when the filter operator is compatible with the
     * field type per §6.4.4 rules.
     */
    private static boolean isOperatorCompatibleWithFieldType(
            ViewFilter filter, List<QueryDatasetField> datasetFields) {
        FilterOperator op;
        try {
            op = FilterOperator.valueOf(filter.getOperator());
        } catch (IllegalArgumentException e) {
            return false;
        }

        // Resolve the filter type category
        FilterType filterType = resolveFilterType(filter, datasetFields);
        if (filterType == null) {
            return true; // unknown type — accept all operators
        }

        switch (filterType) {
            case TEXT:
                return op == FilterOperator.EQ
                        || op == FilterOperator.NEQ
                        || op == FilterOperator.CONTAINS
                        || op == FilterOperator.IN;
            case NUMERIC:
                return op == FilterOperator.EQ
                        || op == FilterOperator.NEQ
                        || op == FilterOperator.GT
                        || op == FilterOperator.GTE
                        || op == FilterOperator.LT
                        || op == FilterOperator.LTE
                        || op == FilterOperator.BETWEEN;
            case DATE:
                return op == FilterOperator.EQ
                        || op == FilterOperator.DATE_BEFORE
                        || op == FilterOperator.DATE_AFTER
                        || op == FilterOperator.DATE_RANGE;
            case BOOLEAN:
                return op == FilterOperator.EQ || op == FilterOperator.NEQ;
            default:
                return true;
        }
    }

    /**
     * Resolves the {@link FilterType} category for a view filter. Uses the
     * explicit {@link ViewFilter#filterType} if set; otherwise falls back to
     * the field's {@code dataType} in the dataset.
     */
    private static FilterType resolveFilterType(ViewFilter filter, List<QueryDatasetField> datasetFields) {
        // Try the explicit filterType on the ViewFilter first
        if (filter.getFilterType() != null) {
            try {
                return FilterType.valueOf(filter.getFilterType());
            } catch (IllegalArgumentException e) {
                // fall through to field-level resolution
            }
        }

        // Fall back to the dataset field's dataType
        QueryDatasetField field = findField(datasetFields, filter.getFieldId());
        if (field == null || field.getDataType() == null) {
            return null;
        }

        String dt = field.getDataType().toUpperCase();
        if (TEXT_TYPES.contains(dt)) {
            return FilterType.TEXT;
        }
        // Numeric types
        if (dt.contains("INT") || dt.contains("DECIMAL") || dt.contains("FLOAT")
                || dt.contains("DOUBLE") || dt.contains("NUMERIC") || dt.contains("NUMBER")
                || dt.contains("BIGINT") || dt.contains("SMALLINT") || dt.contains("TINYINT")
                || dt.contains("REAL") || dt.contains("MONEY") || "BIT".equals(dt)) {
            return FilterType.NUMERIC;
        }
        // Date types
        if (dt.contains("DATE") || dt.contains("TIME") || dt.contains("TIMESTAMP")
                || dt.contains("DATETIME") || dt.contains("YEAR")) {
            return FilterType.DATE;
        }
        // Boolean types
        if ("BOOLEAN".equals(dt) || "BOOL".equals(dt)) {
            return FilterType.BOOLEAN;
        }

        return null;
    }

    /**
     * Validates sort fields: each must reference an existing field that is
     * marked sortable. Uses {@link ErrorCode#QV_INVALID_SORT_FIELD} for
     * non-sortable fields (field existence is tracked separately).
     */
    private List<ErrorCode> validateViewSortInternal(
            SavedQueryView view, List<QueryDatasetField> datasetFields) {
        List<ErrorCode> errors = new ArrayList<>();
        if (view.getSort() == null || datasetFields.isEmpty()) {
            return errors;
        }

        for (ViewSort s : view.getSort()) {
            if (s.getFieldId() == null) {
                continue;
            }
            QueryDatasetField field = findField(datasetFields, s.getFieldId());
            if (field != null && !Boolean.TRUE.equals(field.getSortable())) {
                errors.add(ErrorCode.QV_INVALID_SORT_FIELD);
            }
        }

        return errors;
    }

    /**
     * Checks plugin capability requirements for the view against the dataset's
     * datasource: aggregation support for measures, pagination support, and
     * date operator support when the view uses date filters.
     */
    private List<ErrorCode> validatePluginCapability(SavedQueryView view, QueryDataset dataset) {
        List<ErrorCode> errors = new ArrayList<>();
        Long datasourceId = dataset.getDatasourceId();
        if (datasourceId == null) {
            return errors;
        }

        // Check aggregation support for each measure
        if (view.getMeasures() != null) {
            for (ViewMeasure measure : view.getMeasures()) {
                if (measure.getAggregation() != null && !measure.getAggregation().isBlank()) {
                    if (!pluginCapabilityProvider.supportsAggregation(datasourceId, measure.getAggregation())) {
                        errors.add(ErrorCode.QV_PLUGIN_CAPABILITY_UNSUPPORTED);
                    }
                }
            }
        }

        // Check pagination support
        if (!pluginCapabilityProvider.supportsPagination(datasourceId)) {
            errors.add(ErrorCode.QV_PLUGIN_CAPABILITY_UNSUPPORTED);
        }

        return errors;
    }

    private static QueryDatasetField findField(List<QueryDatasetField> fields, String fieldId) {
        if (fields == null || fieldId == null) {
            return null;
        }
        return fields.stream()
                .filter(f -> fieldId.equals(f.getFieldId()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isTextType(String dataType) {
        if (dataType == null) {
            return false;
        }
        return TEXT_TYPES.stream().anyMatch(t -> t.equalsIgnoreCase(dataType));
    }

    private static boolean isSumOrAvg(String aggregation) {
        if (aggregation == null) {
            return false;
        }
        String upper = aggregation.toUpperCase();
        return "SUM".equals(upper) || "AVG".equals(upper);
    }
}