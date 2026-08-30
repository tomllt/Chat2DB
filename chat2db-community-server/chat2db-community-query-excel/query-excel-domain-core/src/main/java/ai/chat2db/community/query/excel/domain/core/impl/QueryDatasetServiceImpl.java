package ai.chat2db.community.query.excel.domain.core.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import ai.chat2db.community.domain.api.converter.LocalStorageConverter;
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
import ai.chat2db.community.query.excel.domain.api.model.SqlRequest;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBSqlExecutor;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBMetadataProvider;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.IQueryValidationService;
import ai.chat2db.community.query.excel.storage.QueryDatasetStorage;
import org.springframework.stereotype.Service;

/**
 * File-backed {@link IQueryDatasetService} implementation (requirements §5.1/§5.4).
 * <p>Persistence delegates to {@link QueryDatasetStorage}; all business rules
 * from §5.4 are enforced before state changes reach storage.</p>
 */
@Service
public class QueryDatasetServiceImpl implements IQueryDatasetService {

    /**
     * Persistence delegate. Package-private so tests can substitute a mock.
     */
    QueryDatasetStorage storage = QueryDatasetStorage.INSTANCE;

    /**
     * Full publish validation (§5.4/§5.6), wired from {@link QueryValidationServiceImpl}
     * by default; injectable for tests.
     * <p>Non-final and {@code @Autowired(required = false)} so the Spring-managed
     * {@link QueryValidationServiceImpl} bean (which itself receives the
     * {@code @Primary JdbcChat2DBMetadataProvider}) overrides the constructor's
     * placeholder wiring in a real runtime.</p>
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private IQueryValidationService validationService;

    /**
     * Metadata provider used by {@link #checkSourceChanged(Long)} to re-read the
     * live source table structure and by publish to recompute the schema hash.
     * <p>Non-final and {@code @Autowired(required = false)} so the
     * {@code @Primary JdbcChat2DBMetadataProvider} overrides the constructor's
     * placeholder {@link LocalMetadataProvider} in a real runtime.</p>
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Chat2DBMetadataProvider metadataProvider;

    /**
     * SQL executor for dataset preview queries. Package-private so it can be
     * injected in the API integration layer (T19) or substituted in tests.
     * Autowired when a {@link Chat2DBSqlExecutor} bean is present (e.g.
     * {@link JdbcChat2DBSqlExecutor}); when {@code null},
     * {@link #preview(Long, int, int)} throws {@link UnsupportedOperationException}.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    Chat2DBSqlExecutor executor;

    /**
     * Default wiring: file storage, the full validation service backed by the
     * placeholder {@link LocalMetadataProvider}, and that same provider for hash
     * recomputation. The local provider reports no columns, so publishing against
     * a real datasource requires the API-layer wiring (T19) via
     * {@link #QueryDatasetServiceImpl(QueryDatasetStorage, IQueryValidationService, Chat2DBMetadataProvider)}.
     */
    public QueryDatasetServiceImpl() {
        this(QueryDatasetStorage.INSTANCE, new QueryValidationServiceImpl(new LocalMetadataProvider()), new LocalMetadataProvider());
    }

    /**
     * Injectable constructor for tests and for the API integration wave (T19).
     *
     * @param storage            persistence delegate
     * @param validationService  full publish validation
     * @param metadataProvider   source metadata access
     */
    QueryDatasetServiceImpl(QueryDatasetStorage storage,
                            IQueryValidationService validationService,
                            Chat2DBMetadataProvider metadataProvider) {
        this.storage = storage;
        this.validationService = validationService;
        this.metadataProvider = metadataProvider;
    }

    private static final List<String> TEXT_TYPES = List.of("VARCHAR", "TEXT", "CHAR", "STRING");

    @Override
    public PageResponse<QueryDataset> list(Long workspaceId, int pageNo, int pageSize, String searchKey) {
        int safePageNo = pageNo <= 0 ? 1 : pageNo;
        int safePageSize = pageSize <= 0 ? QueryExcelConstants.DEFAULT_PAGE_SIZE : pageSize;

        List<QueryDataset> filtered = storage.getDataList().stream()
                .filter(d -> workspaceId == null || Objects.equals(workspaceId, d.getWorkspaceId()))
                .filter(d -> isBlank(searchKey) || (d.getName() != null
                        && d.getName().toLowerCase(Locale.ROOT).contains(searchKey.toLowerCase(Locale.ROOT))))
                .collect(Collectors.toList());

        long total = filtered.size();
        int from = Math.min((safePageNo - 1) * safePageSize, filtered.size());
        int to = Math.min(from + safePageSize, filtered.size());
        return PageResponse.of(new ArrayList<>(filtered.subList(from, to)), total, safePageNo, safePageSize);
    }

    @Override
    public QueryDataset getById(Long id) {
        return storage.getById(id);
    }

    @Override
    public Long create(QueryDataset dataset) {
        assertValid(dataset);
        Date now = new Date();
        dataset.setVersion(1);
        dataset.setStatus(QueryDatasetStatus.DRAFT.name());
        // TODO(T6): resolve owner via QueryExcelPermissionChecker once multi-user mode lands.
        dataset.setOwnerId(null);
        dataset.setGmtCreate(now);
        dataset.setGmtModified(now);
        return storage.save(dataset);
    }

    @Override
    public void update(QueryDataset dataset) {
        QueryDataset existing = requireById(dataset.getId());
        // Published datasets are immutable (§5.5) — editing must create a new
        // DRAFT with version+1. The UI flow for "edit published → new draft"
        // lands with the frontend wave; for now reject the write outright.
        if (QueryDatasetStatus.PUBLISHED.name().equals(existing.getStatus())) {
            throw new QueryExcelException(ErrorCode.DS_PUBLISH_FAILED.getCode(),
                    "Published datasets are immutable; create a new draft to edit");
        }
        // Optimistic locking (§12.3): reject writes based on a stale version.
        if (!Objects.equals(existing.getVersion(), dataset.getVersion())) {
            throw ex(ErrorCode.DS_VERSION_CONFLICT);
        }
        QueryDataset merged = LocalStorageConverter.mergeNotNullProperties(existing, dataset);
        assertValid(merged);
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
        QueryDataset dataset = requireById(id);
        List<ErrorCode> errors = collectErrors(dataset);
        return errors.isEmpty() ? Collections.emptyList() : errors;
    }

    @Override
    public void publish(Long id) {
        QueryDataset dataset = requireById(id);
        // Full validation (§5.4/§5.6) via the injected validation service.
        // This checks field rules, filter integrity, and source structure.
        List<ErrorCode> errors = validationService.validateDatasetForPublish(dataset);
        if (!errors.isEmpty()) {
            throw new QueryExcelException(ErrorCode.DS_PUBLISH_FAILED.getCode(),
                    ErrorCode.DS_PUBLISH_FAILED.getMessage() + ": " + errors.get(0).getMessage());
        }
        dataset.setStatus(QueryDatasetStatus.PUBLISHED.name());
        dataset.setVersion(dataset.getVersion() == null ? 1 : dataset.getVersion() + 1);
        dataset.setSourceSchemaHash(computeSourceSchemaHash(dataset));
        dataset.setGmtModified(new Date());
        storage.save(dataset);
    }

    @Override
    public void disable(Long id) {
        QueryDataset dataset = requireById(id);
        dataset.setStatus(QueryDatasetStatus.DISABLED.name());
        dataset.setGmtModified(new Date());
        storage.save(dataset);
    }

    @Override
    public boolean checkSourceChanged(Long datasetId) {
        QueryDataset dataset = requireById(datasetId);
        String freshHash = computeSourceSchemaHashFromColumns(
                dataset.getDatasourceId(), dataset.getDatabaseName(),
                dataset.getSchemaName(), dataset.getTableName(), dataset);
        String storedHash = dataset.getSourceSchemaHash();
        boolean changed = !Objects.equals(freshHash, storedHash);
        if (changed) {
            // Persist the fresh hash so subsequent checks stay stable; downstream
            // views must be re-validated against the new structure (per §5.6).
            dataset.setSourceSchemaHash(freshHash);
            storage.update(dataset);
        }
        return changed;
    }

    @Override
    public Long copy(Long id, String newName) {
        QueryDataset original = requireById(id);
        QueryDataset copy = deepCopy(original);
        copy.setId(null);
        copy.setName(isBlank(newName) ? "Copy of " + original.getName() : newName);
        copy.setStatus(QueryDatasetStatus.DRAFT.name());
        copy.setVersion(1);
        copy.setSourceSchemaHash(null);
        copy.setGmtCreate(new Date());
        copy.setGmtModified(new Date());
        return storage.save(copy);
    }

    @Override
    public PreviewResult preview(Long id, int pageNo, int pageSize) {
        if (executor == null) {
            throw new UnsupportedOperationException(
                    "SQL executor not configured; wire in API integration layer");
        }
        QueryDataset dataset = requireById(id);
        if (dataset.getFields() == null || dataset.getFields().isEmpty()) {
            throw ex(ErrorCode.DS_NO_FIELDS);
        }

        SqlRequest request = SqlGenerator.generatePreviewSql(dataset, pageNo, pageSize);
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
            // Re-throw query-level errors (e.g. EX_QUERY_TIMEOUT) as-is
            throw e;
        } catch (Exception e) {
            throw new QueryExcelException(ErrorCode.EX_QUERY_TIMEOUT.getCode(),
                    "Query execution failed: " + e.getMessage());
        }

        List<String> columnNames = dataset.getFields().stream()
                .map(QueryDatasetServiceImpl::fieldColumnName)
                .collect(Collectors.toList());
        List<Map<String, Object>> rowMaps = result.getRows().stream()
                .map(row -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (int i = 0; i < columnNames.size() && i < row.size(); i++) {
                        map.put(columnNames.get(i), row.get(i));
                    }
                    return map;
                })
                .collect(Collectors.toList());

        return PreviewResult.builder()
                .rows(rowMaps)
                .total(result.getTotal())
                .pageNo(pageNo <= 0 ? 1 : pageNo)
                .pageSize(pageSize <= 0 ? QueryExcelConstants.DEFAULT_PAGE_SIZE
                        : Math.min(pageSize, QueryExcelConstants.MAX_PAGE_SIZE))
                .columns(columnNames)
                .build();
    }

    // ── validation (§5.4) ────────────────────────────────────────

    private void assertValid(QueryDataset dataset) {
        List<ErrorCode> errors = collectErrors(dataset);
        if (!errors.isEmpty()) {
            throw ex(errors.get(0));
        }
    }

    /**
     * Collects every §5.4 violation for a dataset (create/update throw the first
     * one; {@link #validate(Long)} reports them all).
     */
    private List<ErrorCode> collectErrors(QueryDataset dataset) {
        List<ErrorCode> errors = new ArrayList<>();
        if (dataset == null || dataset.getFields() == null || dataset.getFields().isEmpty()) {
            errors.add(ErrorCode.DS_NO_FIELDS);
            return errors;
        }
        for (QueryDatasetField field : dataset.getFields()) {
            boolean measure = FieldRole.MEASURE.name().equals(field.getRole());
            if (measure && isBlank(field.getAggregation())) {
                errors.add(ErrorCode.DS_INVALID_AGGREGATION);
            }
            if (isTextType(field.getDataType()) && isSumOrAvg(field.getAggregation())) {
                errors.add(ErrorCode.DS_TEXT_AGGREGATION);
            }
        }
        // NOTE: sort references live on SavedQueryView (ViewSort) and are validated
        // in T5; the dataset model itself carries no sort configuration, so the
        // DS_SORT_FIELD_NOT_SORTABLE rule is enforced at view level.
        if (dataset.getBaseFilters() != null) {
            for (DatasetFilter filter : dataset.getBaseFilters()) {
                QueryDatasetField field = findField(dataset, filter.getFieldId());
                if (field == null || !Boolean.TRUE.equals(field.getFilterable())) {
                    errors.add(ErrorCode.DS_FILTER_FIELD_NOT_FILTERABLE);
                }
            }
        }
        return errors;
    }

    private QueryDatasetField findField(QueryDataset dataset, String fieldId) {
        if (dataset.getFields() == null || fieldId == null) {
            return null;
        }
        return dataset.getFields().stream()
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
        return "SUM".equalsIgnoreCase(aggregation) || "AVG".equalsIgnoreCase(aggregation);
    }

    // ── helpers ──────────────────────────────────────────────────

    private QueryDataset requireById(Long id) {
        QueryDataset dataset = storage.getById(id);
        if (dataset == null) {
            throw ex(ErrorCode.DS_NOT_FOUND);
        }
        return dataset;
    }

    private static QueryExcelException ex(ErrorCode errorCode) {
        return new QueryExcelException(errorCode.getCode(), errorCode.getMessage());
    }

    private static String fieldColumnName(QueryDatasetField field) {
        if (field.getDisplayName() != null && !field.getDisplayName().isBlank()) {
            return field.getDisplayName();
        }
        if (field.getSourceColumn() != null && !field.getSourceColumn().isBlank()) {
            return field.getSourceColumn();
        }
        return field.getFieldId();
    }

    /**
     * Deep copy preserving every property except {@code id}.
     */
    private static QueryDataset deepCopy(QueryDataset src) {
        QueryDataset copy = new QueryDataset();
        copy.setWorkspaceId(src.getWorkspaceId());
        copy.setName(src.getName());
        copy.setDescription(src.getDescription());
        copy.setDatasourceId(src.getDatasourceId());
        copy.setDatabaseName(src.getDatabaseName());
        copy.setSchemaName(src.getSchemaName());
        copy.setTableName(src.getTableName());
        copy.setSourceObjectType(src.getSourceObjectType());
        copy.setStatus(src.getStatus());
        copy.setVersion(src.getVersion());
        copy.setSourceSchemaHash(src.getSourceSchemaHash());
        copy.setOwnerId(src.getOwnerId());
        copy.setGmtCreate(src.getGmtCreate());
        copy.setGmtModified(src.getGmtModified());
        copy.setFields(src.getFields() == null ? null
                : src.getFields().stream().map(QueryDatasetServiceImpl::copyField).collect(Collectors.toList()));
        copy.setBaseFilters(src.getBaseFilters() == null ? null
                : src.getBaseFilters().stream().map(QueryDatasetServiceImpl::copyFilter).collect(Collectors.toList()));
        return copy;
    }

    private static QueryDatasetField copyField(QueryDatasetField f) {
        QueryDatasetField c = new QueryDatasetField();
        c.setFieldId(f.getFieldId());
        c.setSourceColumn(f.getSourceColumn());
        c.setDisplayName(f.getDisplayName());
        c.setDataType(f.getDataType());
        c.setRole(f.getRole());
        c.setAggregation(f.getAggregation());
        c.setFilterable(f.getFilterable());
        c.setSortable(f.getSortable());
        c.setVisible(f.getVisible());
        c.setNumberFormat(f.getNumberFormat());
        c.setNullDisplay(f.getNullDisplay());
        return c;
    }

    private static DatasetFilter copyFilter(DatasetFilter f) {
        DatasetFilter c = new DatasetFilter();
        c.setFieldId(f.getFieldId());
        c.setOperator(f.getOperator());
        c.setValue(f.getValue());
        c.setValues(f.getValues() == null ? null : new ArrayList<>(f.getValues()));
        return c;
    }

    /**
     * SHA-256 over the sorted {@code tableName|sourceColumn:dataType} pairs of
     * the dataset schema (spec §5.2, line 437) — the change fingerprint guarding
     * published datasets.
     */
    static String computeSourceSchemaHash(QueryDataset dataset) {
        if (dataset == null) {
            return null;
        }
        String tablePrefix = dataset.getTableName() == null ? "" : dataset.getTableName();
        if (dataset.getFields() == null || dataset.getFields().isEmpty()) {
            return null;
        }
        String payload = dataset.getFields().stream()
                .map(f -> tablePrefix + "|" + fieldName(f) + ":"
                        + (f.getDataType() == null ? "" : f.getDataType()))
                .sorted()
                .collect(Collectors.joining("\n"));
        return sha256Hex(payload);
    }

    /**
     * Recomputes the source schema hash from the live table columns reported by
     * the {@link Chat2DBMetadataProvider}. Only columns referenced by the current
     * field set contribute, keyed by source column name so the hash stays
     * comparable to {@link #computeSourceSchemaHash(QueryDataset)}.
     */
    private String computeSourceSchemaHashFromColumns(Long datasourceId, String databaseName,
                                                       String schemaName, String tableName,
                                                       QueryDataset dataset) {
        if (dataset.getFields() == null || dataset.getFields().isEmpty()) {
            return null;
        }
        List<ColumnInfo> columns = metadataProvider.getTableColumns(
                datasourceId, databaseName, schemaName, tableName);
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        List<ColumnInfo> referenced = new ArrayList<>();
        for (QueryDatasetField field : dataset.getFields()) {
            String column = fieldName(field);
            ColumnInfo match = columns.stream()
                    .filter(c -> column.equalsIgnoreCase(c.getColumnName()))
                    .findFirst()
                    .orElse(null);
            if (match != null) {
                referenced.add(match);
            }
        }
        String tablePrefix = tableName == null ? "" : tableName;
        String payload = referenced.stream()
                .map(c -> tablePrefix + "|" + c.getColumnName() + ":"
                        + (c.getDataType() == null ? "" : c.getDataType()))
                .sorted()
                .collect(Collectors.joining("\n"));
        return sha256Hex(payload);
    }

    private static String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String fieldName(QueryDatasetField field) {
        if (field.getSourceColumn() != null && !field.getSourceColumn().isBlank()) {
            return field.getSourceColumn();
        }
        return field.getFieldId() == null ? "" : field.getFieldId();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}