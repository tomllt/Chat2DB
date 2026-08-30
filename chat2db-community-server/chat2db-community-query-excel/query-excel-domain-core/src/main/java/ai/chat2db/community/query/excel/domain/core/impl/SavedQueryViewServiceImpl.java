package ai.chat2db.community.query.excel.domain.core.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import ai.chat2db.community.domain.api.converter.LocalStorageConverter;
import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.QueryExcelConstants;
import ai.chat2db.community.query.excel.domain.api.enums.FilterOperator;
import ai.chat2db.community.query.excel.domain.api.enums.QueryDatasetStatus;
import ai.chat2db.community.query.excel.domain.api.enums.SavedQueryViewStatus;
import ai.chat2db.community.query.excel.domain.api.model.ExecuteQueryRequest;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.SqlRequest;
import ai.chat2db.community.query.excel.domain.api.model.ViewDimension;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.model.ViewMeasure;
import ai.chat2db.community.query.excel.domain.api.model.ViewSort;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBSqlExecutor;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBMetadataProvider;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.IQueryValidationService;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import ai.chat2db.community.query.excel.storage.SavedQueryViewStorage;
import org.springframework.stereotype.Service;

/**
 * File-backed {@link ISavedQueryViewService} implementation (requirements §6.1-6.4, 6.6).
 * <p>Persistence delegates to {@link SavedQueryViewStorage}; all business rules
 * from §6.4 are enforced before state changes reach storage.</p>
 */
@Service
public class SavedQueryViewServiceImpl implements ISavedQueryViewService {

    /**
     * Persistence delegate. Package-private so tests can substitute a mock.
     */
    SavedQueryViewStorage storage = SavedQueryViewStorage.INSTANCE;

    /**
     * Validation service for filter/sort field rules.
     * <p>Non-final and {@code @Autowired(required = false)} so the Spring-managed
     * {@link QueryValidationServiceImpl} bean (which itself receives the
     * {@code @Primary JdbcChat2DBMetadataProvider}) overrides the constructor's
     * placeholder wiring in a real runtime.</p>
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private IQueryValidationService validationService;

    /**
     * Metadata provider for field type resolution.
     * <p>Non-final and {@code @Autowired(required = false)} so the
     * {@code @Primary JdbcChat2DBMetadataProvider} overrides the constructor's
     * placeholder {@link LocalMetadataProvider} in a real runtime.</p>
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Chat2DBMetadataProvider metadataProvider;

    /**
     * Dataset service for checking dataset existence and status.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private IQueryDatasetService datasetService;

    /**
     * SQL executor for view preview/execute queries. Package-private so it can
     * be injected in the API integration layer or substituted in tests.
     * Autowired when a {@link Chat2DBSqlExecutor} bean is present (e.g.
     * {@link JdbcChat2DBSqlExecutor}); when {@code null},
     * {@link #preview(Long, int, int, List)} and
     * {@link #executeQuery(Long, List)} throw {@link UnsupportedOperationException}.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    Chat2DBSqlExecutor executor;

    private static final Set<String> TEXT_TYPES = Set.of("VARCHAR", "TEXT", "CHAR", "STRING");

    /**
     * Default wiring: file storage, the full validation service backed by the
     * placeholder {@link LocalMetadataProvider}, and that same provider for
     * metadata resolution. The local provider reports no columns, so publishing
     * against a real datasource requires the API-layer wiring (T19) via
     * {@link #SavedQueryViewServiceImpl(SavedQueryViewStorage, IQueryValidationService, Chat2DBMetadataProvider, IQueryDatasetService)}.
     */
    public SavedQueryViewServiceImpl() {
        this(SavedQueryViewStorage.INSTANCE,
                new QueryValidationServiceImpl(new LocalMetadataProvider()),
                new LocalMetadataProvider(),
                new QueryDatasetServiceImpl());
    }

    /**
     * Injectable constructor for tests and for the API integration wave (T19).
     *
     * @param storage            persistence delegate
     * @param validationService  filter/sort validation
     * @param metadataProvider   source metadata access
     * @param datasetService     dataset service for status checks
     */
    SavedQueryViewServiceImpl(SavedQueryViewStorage storage,
                              IQueryValidationService validationService,
                              Chat2DBMetadataProvider metadataProvider,
                              IQueryDatasetService datasetService) {
        this.storage = storage;
        this.validationService = validationService;
        this.metadataProvider = metadataProvider;
        this.datasetService = datasetService;
    }

    @Override
    public PageResponse<SavedQueryView> list(Long workspaceId, int pageNo, int pageSize, String searchKey) {
        int safePageNo = pageNo <= 0 ? 1 : pageNo;
        int safePageSize = pageSize <= 0 ? QueryExcelConstants.DEFAULT_PAGE_SIZE : pageSize;

        List<SavedQueryView> filtered = storage.getDataList().stream()
                .filter(v -> workspaceId == null || Objects.equals(workspaceId, v.getWorkspaceId()))
                .filter(v -> isBlank(searchKey) || (v.getName() != null
                        && v.getName().toLowerCase(Locale.ROOT).contains(searchKey.toLowerCase(Locale.ROOT))))
                .collect(Collectors.toList());

        long total = filtered.size();
        int from = Math.min((safePageNo - 1) * safePageSize, filtered.size());
        int to = Math.min(from + safePageSize, filtered.size());
        return PageResponse.of(new ArrayList<>(filtered.subList(from, to)), total, safePageNo, safePageSize);
    }

    @Override
    public SavedQueryView getById(Long id) {
        SavedQueryView view = storage.getById(id);
        if (view != null && SavedQueryViewStatus.PUBLISHED.name().equals(view.getStatus())) {
            checkCompatibility(id);
        }
        return view;
    }

    @Override
    public Long create(SavedQueryView view) {
        assertValidForSave(view);
        Date now = new Date();
        view.setVersion(1);
        view.setStatus(SavedQueryViewStatus.DRAFT.name());
        view.setGmtCreate(now);
        view.setGmtModified(now);
        return storage.save(view);
    }

    @Override
    public void update(SavedQueryView view) {
        SavedQueryView existing = requireById(view.getId());
        // Published views are immutable (§6.1)
        if (SavedQueryViewStatus.PUBLISHED.name().equals(existing.getStatus())) {
            throw new QueryExcelException(ErrorCode.QV_PUBLISH_FAILED.getCode(),
                    "Published views are immutable; create a new draft to edit");
        }
        // Optimistic locking (§12.3): reject writes based on a stale version.
        if (!Objects.equals(existing.getVersion(), view.getVersion())) {
            throw ex(ErrorCode.QV_VERSION_CONFLICT);
        }
        SavedQueryView merged = LocalStorageConverter.mergeNotNullProperties(existing, view);
        assertValidForSave(merged);
        merged.setGmtModified(new Date());
        storage.update(merged);
    }

    @Override
    public void delete(Long id) {
        requireById(id);
        storage.delete(id);
    }

    @Override
    public List<ErrorCode> validate(Long id) {
        SavedQueryView view = requireById(id);
        List<ErrorCode> errors = collectErrors(view);
        return errors.isEmpty() ? Collections.emptyList() : errors;
    }

    @Override
    public void publish(Long id) {
        SavedQueryView view = requireById(id);
        // Use collectErrors (which resolves the dataset via this service's own
        // datasetService) rather than validationService.validateView() — the
        // latter cannot resolve the dataset because QueryValidationServiceImpl's
        // datasetService is not autowired (avoids circular dependency with
        // QueryDatasetServiceImpl).
        List<ErrorCode> errors = collectErrors(view);
        if (!errors.isEmpty()) {
            throw new QueryExcelException(ErrorCode.QV_PUBLISH_FAILED.getCode(),
                    ErrorCode.QV_PUBLISH_FAILED.getMessage() + ": " + errors.get(0).getMessage());
        }
        view.setStatus(SavedQueryViewStatus.PUBLISHED.name());
        view.setVersion(view.getVersion() == null ? 1 : view.getVersion() + 1);
        view.setGmtModified(new Date());
        storage.save(view);
    }

    @Override
    public void disable(Long id) {
        SavedQueryView view = requireById(id);
        view.setStatus(SavedQueryViewStatus.DISABLED.name());
        view.setGmtModified(new Date());
        storage.save(view);
    }

    @Override
    public boolean checkCompatibility(Long viewId) {
        SavedQueryView view = requireById(viewId);
        QueryDataset dataset = datasetService.getById(view.getDatasetId());
        if (dataset == null) {
            // Dataset no longer exists → view is invalid
            view.setStatus(SavedQueryViewStatus.INVALID.name());
            storage.save(view);
            return false;
        }
        boolean compatible = validationService.validateViewCompatibility(view, dataset);
        if (!compatible) {
            view.setStatus(SavedQueryViewStatus.INVALID.name());
            storage.save(view);
            return false;
        }
        return true;
    }

    @Override
    public Long copy(Long id, String newName) {
        SavedQueryView original = requireById(id);
        SavedQueryView copy = deepCopy(original);
        copy.setId(null);
        copy.setName(isBlank(newName) ? "Copy of " + original.getName() : newName);
        copy.setStatus(SavedQueryViewStatus.DRAFT.name());
        copy.setVersion(1);
        copy.setGmtCreate(new Date());
        copy.setGmtModified(new Date());
        return storage.save(copy);
    }

    @Override
    public PreviewResult preview(Long id, int pageNo, int pageSize, List<ViewFilter> filterOverrides) {
        if (executor == null) {
            throw new UnsupportedOperationException(
                    "SQL executor not configured; wire in API integration layer");
        }
        SavedQueryView view = requireById(id);
        // Silent compatibility check for published views (§5.6)
        if (SavedQueryViewStatus.PUBLISHED.name().equals(view.getStatus())) {
            checkCompatibility(id);
        }
        QueryDataset dataset = resolveDataset(view);
        assertValidForPreview(view, dataset);

        SqlRequest request = generateViewSql(view, dataset, pageNo, pageSize, filterOverrides);
        QueryResult result;
        try {
            result = executor.execute(ExecuteQueryRequest.builder()
                    .datasourceId(dataset.getDatasourceId())
                    .databaseName(dataset.getDatabaseName())
                    .schemaName(dataset.getSchemaName())
                    .tableName(dataset.getTableName())
                    .sql(request.getSql())
                    .params(request.getParams())
                    .timeoutMs(QueryExcelConstants.QUERY_TIMEOUT_MS)
                    .build());
        } catch (QueryExcelException e) {
            throw e;
        } catch (Exception e) {
            throw new QueryExcelException(ErrorCode.EX_QUERY_TIMEOUT.getCode(),
                    "Query execution failed: " + e.getMessage());
        }

        List<String> columnNames = previewColumnNames(view, dataset);
        List<Map<String, Object>> rowMaps = result.getRows().stream()
                .map(row -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (int i = 0; i < columnNames.size() && i < row.size(); i++) {
                        map.put(columnNames.get(i), row.get(i));
                    }
                    return map;
                })
                .collect(Collectors.toList());

        int safePageSize = pageSize <= 0 ? QueryExcelConstants.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, QueryExcelConstants.MAX_PAGE_SIZE);

        return PreviewResult.builder()
                .rows(rowMaps)
                .total(result.getTotal())
                .pageNo(pageNo <= 0 ? 1 : pageNo)
                .pageSize(safePageSize)
                .columns(columnNames)
                .build();
    }

    @Override
    public QueryResult executeQuery(Long id, List<ViewFilter> filterOverrides) {
        if (executor == null) {
            throw new UnsupportedOperationException(
                    "SQL executor not configured; wire in API integration layer");
        }
        SavedQueryView view = requireById(id);
        QueryDataset dataset = resolveDataset(view);
        assertValidForPreview(view, dataset);

        // ExecuteQuery: no pagination — return all rows
        SqlRequest request = generateViewSql(view, dataset, 1, Integer.MAX_VALUE, filterOverrides);
        QueryResult result;
        try {
            result = executor.execute(ExecuteQueryRequest.builder()
                    .datasourceId(dataset.getDatasourceId())
                    .databaseName(dataset.getDatabaseName())
                    .schemaName(dataset.getSchemaName())
                    .tableName(dataset.getTableName())
                    .sql(request.getSql())
                    .params(request.getParams())
                    .timeoutMs(QueryExcelConstants.QUERY_TIMEOUT_MS)
                    .build());
        } catch (QueryExcelException e) {
            throw e;
        } catch (Exception e) {
            throw new QueryExcelException(ErrorCode.EX_QUERY_TIMEOUT.getCode(),
                    "Query execution failed: " + e.getMessage());
        }
        List<String> columnNames = previewColumnNames(view, dataset);
        List<Map<String, Object>> rows = result.getRows().stream()
                .map(row -> {
                    Map<String, Object> mappedRow = new LinkedHashMap<>();
                    for (int i = 0; i < columnNames.size() && i < row.size(); i++) {
                        mappedRow.put(columnNames.get(i), row.get(i));
                    }
                    return mappedRow;
                })
                .collect(Collectors.toList());
        return QueryResult.builder()
                .columns(columnNames)
                .rows(rows.stream().map(row -> columnNames.stream()
                        .map(row::get).collect(Collectors.toList())).collect(Collectors.toList()))
                .total(result.getTotal())
                .build();
    }

    // ── validation (§6.4) ────────────────────────────────────────

    private void assertValidForSave(SavedQueryView view) {
        List<ErrorCode> errors = collectErrors(view);
        if (!errors.isEmpty()) {
            throw ex(errors.get(0));
        }
    }

    private void assertValidForPreview(SavedQueryView view, QueryDataset dataset) {
        List<ErrorCode> errors = collectPreviewErrors(view, dataset);
        if (!errors.isEmpty()) {
            throw ex(errors.get(0));
        }
    }

    /**
     * Collects every §6.4 violation for a view (create/update/publish/validate).
     * <p>Save-time rules: at least one row or column field, the referenced
     * dataset must be published, every referenced field must exist, filter
     * operators must match field types, and sort fields must be sortable.</p>
     */
    private List<ErrorCode> collectErrors(SavedQueryView view) {
        List<ErrorCode> errors = new ArrayList<>();

        // §6.4: rowFields.length > 0 OR columnFields.length > 0
        boolean hasRowFields = view.getRowFields() != null && !view.getRowFields().isEmpty();
        boolean hasColumnFields = view.getColumnFields() != null && !view.getColumnFields().isEmpty();
        if (!hasRowFields && !hasColumnFields) {
            errors.add(ErrorCode.QV_NO_ROW_OR_COLUMN_FIELD);
        }

        // §6.4: Dataset must be PUBLISHED
        QueryDataset dataset = resolveDatasetOrNull(view);
        if (dataset == null || !QueryDatasetStatus.PUBLISHED.name().equals(dataset.getStatus())) {
            errors.add(ErrorCode.QV_DATASET_NOT_PUBLISHED);
            return errors;
        }

        errors.addAll(collectFieldErrors(view, dataset));
        return errors;
    }

    /**
     * Collects preview-time violations: at least one dimension or measure, the
     * referenced dataset must be published, every referenced field must exist,
     * filter operators must match field types, and sort fields must be sortable.
     */
    private List<ErrorCode> collectPreviewErrors(SavedQueryView view, QueryDataset dataset) {
        List<ErrorCode> errors = new ArrayList<>();

        // §6.4: dimensions.length > 0 OR measures.length > 0
        boolean hasDimensions = view.getDimensions() != null && !view.getDimensions().isEmpty();
        boolean hasMeasures = view.getMeasures() != null && !view.getMeasures().isEmpty();
        if (!hasDimensions && !hasMeasures) {
            errors.add(ErrorCode.QV_NO_DIMENSION_OR_MEASURE);
        }

        // §6.4: Dataset must be PUBLISHED
        if (dataset == null || !QueryDatasetStatus.PUBLISHED.name().equals(dataset.getStatus())) {
            errors.add(ErrorCode.QV_DATASET_NOT_PUBLISHED);
            return errors;
        }

        errors.addAll(collectFieldErrors(view, dataset));
        return errors;
    }

    /**
     * Field-level rules shared by save and preview validation: every referenced
     * field must exist in the dataset, filter operators must match field types,
     * and sort fields must be sortable.
     */
    private List<ErrorCode> collectFieldErrors(SavedQueryView view, QueryDataset dataset) {
        List<ErrorCode> errors = new ArrayList<>();
        List<QueryDatasetField> datasetFields = dataset.getFields();

        // §6.4: All fieldIds must exist in the dataset's fields
        Set<String> referencedFieldIds = new HashSet<>();
        if (view.getDimensions() != null) {
            referencedFieldIds.addAll(view.getDimensions().stream()
                    .map(ViewDimension::getFieldId).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        if (view.getMeasures() != null) {
            referencedFieldIds.addAll(view.getMeasures().stream()
                    .map(ViewMeasure::getFieldId).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        if (view.getFilters() != null) {
            referencedFieldIds.addAll(view.getFilters().stream()
                    .map(ViewFilter::getFieldId).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        if (view.getSort() != null) {
            referencedFieldIds.addAll(view.getSort().stream()
                    .map(ViewSort::getFieldId).filter(Objects::nonNull).collect(Collectors.toSet()));
        }

        Set<String> availableFieldIds = datasetFields == null ? Collections.emptySet()
                : datasetFields.stream()
                        .map(QueryDatasetField::getFieldId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        for (String refId : referencedFieldIds) {
            if (!availableFieldIds.contains(refId)) {
                errors.add(ErrorCode.QV_FIELD_NOT_FOUND);
            }
        }

        // §6.4: Filter operators must match field type per §6.4.4 table
        if (view.getFilters() != null && datasetFields != null) {
            for (ViewFilter filter : view.getFilters()) {
                if (filter.getFieldId() == null || filter.getOperator() == null) {
                    continue;
                }
                QueryDatasetField field = datasetFields.stream()
                        .filter(f -> filter.getFieldId().equals(f.getFieldId()))
                        .findFirst().orElse(null);
                if (field == null) {
                    continue; // already reported as QV_FIELD_NOT_FOUND
                }
                if (!isOperatorCompatible(filter.getOperator(), field.getDataType())) {
                    errors.add(ErrorCode.QV_INVALID_FILTER_FIELD);
                }
            }
        }

        // §6.4: Sort fields must be sortable
        if (view.getSort() != null && datasetFields != null) {
            for (ViewSort sort : view.getSort()) {
                if (sort.getFieldId() == null) {
                    continue;
                }
                QueryDatasetField field = datasetFields.stream()
                        .filter(f -> sort.getFieldId().equals(f.getFieldId()))
                        .findFirst().orElse(null);
                if (field == null) {
                    continue; // already reported as QV_FIELD_NOT_FOUND
                }
                if (!Boolean.TRUE.equals(field.getSortable())) {
                    errors.add(ErrorCode.QV_INVALID_SORT_FIELD);
                }
            }
        }

        return errors;
    }

    /**
     * Checks whether a filter operator is compatible with a field data type
     * per §6.4.4.
     */
    private static boolean isOperatorCompatible(String operator, String dataType) {
        if (operator == null) {
            return true;
        }
        FilterOperator op;
        try {
            op = FilterOperator.valueOf(operator);
        } catch (IllegalArgumentException e) {
            return false;
        }

        boolean isText = dataType != null && TEXT_TYPES.stream()
                .anyMatch(t -> t.equalsIgnoreCase(dataType));

        // Text fields: only EQ, NEQ, CONTAINS, IN
        if (isText) {
            return op == FilterOperator.EQ
                    || op == FilterOperator.NEQ
                    || op == FilterOperator.CONTAINS
                    || op == FilterOperator.IN;
        }

        // Numeric/date fields: all operators
        return true;
    }

    // ── SQL generation for view preview ───────────────────────────

    /**
     * Generates a parameterized SQL query for a saved query view preview/execute.
     * SELECT dimensions + measures, GROUP BY dimensions, ORDER BY sort,
     * WHERE viewFilters + filterOverrides.
     */
    private SqlRequest generateViewSql(SavedQueryView view, QueryDataset dataset,
                                       int pageNo, int pageSize, List<ViewFilter> filterOverrides) {
        int safePageNo = pageNo <= 0 ? 1 : pageNo;
        int safePageSize = Math.min(pageSize <= 0 ? QueryExcelConstants.DEFAULT_PAGE_SIZE : pageSize,
                QueryExcelConstants.MAX_PAGE_SIZE);

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // Build a fieldId → sourceColumn lookup for WHERE clause resolution
        Map<String, String> fieldColumnMap = new LinkedHashMap<>();
        if (dataset.getFields() != null) {
            for (QueryDatasetField f : dataset.getFields()) {
                String col = (f.getSourceColumn() != null && !f.getSourceColumn().isBlank())
                        ? f.getSourceColumn() : f.getFieldId();
                fieldColumnMap.put(f.getFieldId(), col);
            }
        }

        // ── SELECT clause ────────────────────────────────────────────
        List<String> selectParts = new ArrayList<>();

        // Dimensions
        if (view.getDimensions() != null) {
            for (ViewDimension dim : view.getDimensions()) {
                String col = quote(fieldColumnMap.getOrDefault(dim.getFieldId(), dim.getFieldId()));
                selectParts.add(col);
            }
        }
        // Measures
        if (view.getMeasures() != null) {
            for (ViewMeasure measure : view.getMeasures()) {
                String col = quote(fieldColumnMap.getOrDefault(measure.getFieldId(), measure.getFieldId()));
                String agg = measure.getAggregation();
                if (agg != null && !agg.isBlank()) {
                    selectParts.add(agg.toUpperCase() + "(" + col + ") AS " + col);
                } else {
                    selectParts.add(col);
                }
            }
        }

        if (selectParts.isEmpty()) {
            sql.append("SELECT 1");
        } else {
            sql.append("SELECT ");
            sql.append(String.join(", ", selectParts));
        }

        // ── FROM clause ──────────────────────────────────────────────
        sql.append(" FROM ");
        sql.append(fromClause(dataset));

        // ── WHERE clause ─────────────────────────────────────────────
        List<String> whereParts = new ArrayList<>();

        // View filters
        if (view.getFilters() != null) {
            for (ViewFilter filter : view.getFilters()) {
                String clause = whereClause(filter, fieldColumnMap, params);
                if (clause != null) {
                    whereParts.add(clause);
                }
            }
        }

        // Filter overrides (from API call)
        if (filterOverrides != null) {
            for (ViewFilter filter : filterOverrides) {
                String clause = whereClause(filter, fieldColumnMap, params);
                if (clause != null) {
                    whereParts.add(clause);
                }
            }
        }

        if (!whereParts.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(String.join(" AND ", whereParts));
        }

        // ── GROUP BY clause ──────────────────────────────────────────
        if (view.getDimensions() != null && !view.getDimensions().isEmpty()) {
            sql.append(" GROUP BY ");
            sql.append(view.getDimensions().stream()
                    .map(d -> quote(fieldColumnMap.getOrDefault(d.getFieldId(), d.getFieldId())))
                    .collect(Collectors.joining(", ")));
        }

        // ── ORDER BY clause ──────────────────────────────────────────
        if (view.getSort() != null && !view.getSort().isEmpty()) {
            sql.append(" ORDER BY ");
            sql.append(view.getSort().stream()
                    .map(s -> {
                        String col = quote(fieldColumnMap.getOrDefault(s.getFieldId(), s.getFieldId()));
                        String dir = s.getDirection();
                        if ("DESC".equalsIgnoreCase(dir)) {
                            return col + " DESC";
                        }
                        return col + " ASC";
                    })
                    .collect(Collectors.joining(", ")));
        }

        // ── LIMIT / OFFSET (only for preview, not executeQuery) ──────
        if (pageSize < Integer.MAX_VALUE) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(safePageSize);
            params.add((long) (safePageNo - 1) * safePageSize);
        }

        return SqlRequest.builder()
                .sql(sql.toString())
                .params(params)
                .build();
    }

    // ── WHERE clause helpers ──────────────────────────────────────

    private static String whereClause(ViewFilter filter, Map<String, String> fieldColumnMap,
                                      List<Object> params) {
        String column = quote(fieldColumnMap.getOrDefault(filter.getFieldId(),
                filter.getFieldId() == null ? "" : filter.getFieldId()));
        String operator = filter.getOperator();

        if (operator == null) {
            params.add(filter.getValue());
            return column + " = ?";
        }

        FilterOperator op;
        try {
            op = FilterOperator.valueOf(operator);
        } catch (IllegalArgumentException e) {
            params.add(filter.getValue());
            return column + " = ?";
        }

        switch (op) {
            case EQ:
                params.add(filter.getValue());
                return column + " = ?";
            case NEQ:
                params.add(filter.getValue());
                return column + " != ?";
            case GT:
                params.add(filter.getValue());
                return column + " > ?";
            case GTE:
                params.add(filter.getValue());
                return column + " >= ?";
            case LT:
                params.add(filter.getValue());
                return column + " < ?";
            case LTE:
                params.add(filter.getValue());
                return column + " <= ?";
            case BETWEEN:
                params.add(filter.getValue());
                params.add(filter.getValues() != null && filter.getValues().size() > 1
                        ? filter.getValues().get(1) : null);
                return column + " BETWEEN ? AND ?";
            case DATE_RANGE:
                params.add(filter.getValue());
                params.add(filter.getValues() != null && filter.getValues().size() > 1
                        ? filter.getValues().get(1) : null);
                return column + " BETWEEN ? AND ?";
            case IN: {
                List<String> values = filter.getValues();
                if (values == null || values.isEmpty()) {
                    params.add(filter.getValue());
                    return column + " = ?";
                }
                String placeholders = values.stream()
                        .peek(params::add)
                        .map(v -> "?")
                        .collect(Collectors.joining(", "));
                return column + " IN (" + placeholders + ")";
            }
            case CONTAINS:
                params.add("%" + (filter.getValue() == null ? "" : filter.getValue()) + "%");
                return column + " LIKE ?";
            case DATE_BEFORE:
                params.add(filter.getValue());
                return column + " < ?";
            case DATE_AFTER:
                params.add(filter.getValue());
                return column + " > ?";
            default:
                params.add(filter.getValue());
                return column + " = ?";
        }
    }

    // ── helpers ──────────────────────────────────────────────────

    private QueryDataset resolveDataset(SavedQueryView view) {
        QueryDataset dataset = resolveDatasetOrNull(view);
        if (dataset == null) {
            throw ex(ErrorCode.QV_DATASET_NOT_PUBLISHED);
        }
        return dataset;
    }

    private QueryDataset resolveDatasetOrNull(SavedQueryView view) {
        if (view.getDatasetId() == null) {
            return null;
        }
        return datasetService.getById(view.getDatasetId());
    }

    private SavedQueryView requireById(Long id) {
        SavedQueryView view = storage.getById(id);
        if (view == null) {
            throw ex(ErrorCode.QV_NOT_FOUND);
        }
        return view;
    }

    private static QueryExcelException ex(ErrorCode errorCode) {
        return new QueryExcelException(errorCode.getCode(), errorCode.getMessage());
    }

    private static List<String> previewColumnNames(SavedQueryView view, QueryDataset dataset) {
        List<String> columnNames = new ArrayList<>();
        if (view.getDimensions() != null) {
            columnNames.addAll(view.getDimensions().stream()
                    .map(d -> resolveColumnName(dataset, d.getFieldId()))
                    .collect(Collectors.toList()));
        }
        if (view.getMeasures() != null) {
            columnNames.addAll(view.getMeasures().stream()
                    .map(m -> resolveColumnName(dataset, m.getFieldId()))
                    .collect(Collectors.toList()));
        }
        return columnNames;
    }

    private static String resolveColumnName(QueryDataset dataset, String fieldId) {
        if (dataset.getFields() != null) {
            for (QueryDatasetField f : dataset.getFields()) {
                if (fieldId.equals(f.getFieldId())) {
                    if (f.getDisplayName() != null && !f.getDisplayName().isBlank()) {
                        return f.getDisplayName();
                    }
                    if (f.getSourceColumn() != null && !f.getSourceColumn().isBlank()) {
                        return f.getSourceColumn();
                    }
                    return f.getFieldId();
                }
            }
        }
        return fieldId;
    }

    private static String fromClause(QueryDataset dataset) {
        StringBuilder sb = new StringBuilder();
        sb.append(quote(dataset.getDatabaseName())).append('.');
        if (dataset.getSchemaName() != null && !dataset.getSchemaName().isBlank()) {
            sb.append(quote(dataset.getSchemaName())).append('.');
        }
        sb.append(quote(dataset.getTableName()));
        return sb.toString();
    }

    private static String quote(String identifier) {
        if (identifier == null) {
            return "``";
        }
        return "`" + identifier + "`";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Deep copy preserving every property except {@code id}.
     */
    private static SavedQueryView deepCopy(SavedQueryView src) {
        SavedQueryView copy = new SavedQueryView();
        copy.setWorkspaceId(src.getWorkspaceId());
        copy.setDatasetId(src.getDatasetId());
        copy.setDatasetVersion(src.getDatasetVersion());
        copy.setName(src.getName());
        copy.setDescription(src.getDescription());
        copy.setRowFields(src.getRowFields() == null ? null : new ArrayList<>(src.getRowFields()));
        copy.setColumnFields(src.getColumnFields() == null ? null : new ArrayList<>(src.getColumnFields()));
        copy.setDimensions(src.getDimensions() == null ? null
                : src.getDimensions().stream().map(SavedQueryViewServiceImpl::copyDimension).collect(Collectors.toList()));
        copy.setMeasures(src.getMeasures() == null ? null
                : src.getMeasures().stream().map(SavedQueryViewServiceImpl::copyMeasure).collect(Collectors.toList()));
        copy.setFilters(src.getFilters() == null ? null
                : src.getFilters().stream().map(SavedQueryViewServiceImpl::copyFilter).collect(Collectors.toList()));
        copy.setSort(src.getSort() == null ? null
                : src.getSort().stream().map(SavedQueryViewServiceImpl::copySort).collect(Collectors.toList()));
        copy.setPageSize(src.getPageSize());
        copy.setStatus(src.getStatus());
        copy.setVersion(src.getVersion());
        copy.setOwnerId(src.getOwnerId());
        copy.setGmtCreate(src.getGmtCreate());
        copy.setGmtModified(src.getGmtModified());
        return copy;
    }

    private static ViewDimension copyDimension(ViewDimension d) {
        ViewDimension c = new ViewDimension();
        c.setFieldId(d.getFieldId());
        c.setRole(d.getRole());
        c.setSortDirection(d.getSortDirection());
        return c;
    }

    private static ViewMeasure copyMeasure(ViewMeasure m) {
        ViewMeasure c = new ViewMeasure();
        c.setFieldId(m.getFieldId());
        c.setAggregation(m.getAggregation());
        return c;
    }

    private static ViewFilter copyFilter(ViewFilter f) {
        ViewFilter c = new ViewFilter();
        c.setFieldId(f.getFieldId());
        c.setFilterType(f.getFilterType());
        c.setOperator(f.getOperator());
        c.setValue(f.getValue());
        c.setValues(f.getValues() == null ? null : new ArrayList<>(f.getValues()));
        return c;
    }

    private static ViewSort copySort(ViewSort s) {
        ViewSort c = new ViewSort();
        c.setFieldId(s.getFieldId());
        c.setDirection(s.getDirection());
        return c;
    }
}