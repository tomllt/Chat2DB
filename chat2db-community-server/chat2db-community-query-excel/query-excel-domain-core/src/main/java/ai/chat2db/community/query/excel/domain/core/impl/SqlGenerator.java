package ai.chat2db.community.query.excel.domain.core.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ai.chat2db.community.query.excel.domain.api.QueryExcelConstants;
import ai.chat2db.community.query.excel.domain.api.enums.FilterOperator;
import ai.chat2db.community.query.excel.domain.api.enums.FieldRole;
import ai.chat2db.community.query.excel.domain.api.model.DatasetFilter;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.SqlRequest;
import ai.chat2db.community.query.excel.domain.api.model.ViewDimension;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.model.ViewMeasure;
import ai.chat2db.community.query.excel.domain.api.model.ViewSort;

/**
 * Static utility that generates parameterized SQL from a {@link QueryDataset}
 * configuration (requirements §5.1/DS-008, §6.5).
 * <p>All field references are backtick-quoted; filter values are bound as
 * parameters to prevent SQL injection.</p>
 */
public final class SqlGenerator {

    private SqlGenerator() {
        // utility class
    }

    /**
     * Generates a parameterized preview SQL from a dataset definition.
     *
     * @param dataset  the dataset configuration (fields, filters, source table)
     * @param pageNo   page number (1-based)
     * @param pageSize page size, capped at {@link QueryExcelConstants#MAX_PAGE_SIZE}
     * @return a parameterized {@link SqlRequest} with ordered bind parameters
     */
    public static SqlRequest generatePreviewSql(QueryDataset dataset, int pageNo, int pageSize) {
        int safePageNo = pageNo <= 0 ? 1 : pageNo;
        int safePageSize = Math.min(pageSize <= 0 ? QueryExcelConstants.DEFAULT_PAGE_SIZE : pageSize,
                QueryExcelConstants.MAX_PAGE_SIZE);

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // Build a fieldId → sourceColumn lookup for WHERE clause resolution
        Map<String, String> fieldColumnMap = dataset.getFields().stream()
                .filter(f -> f != null)
                .collect(Collectors.toMap(
                        f -> f.getFieldId() == null ? "" : f.getFieldId(),
                        SqlGenerator::columnName));

        // ── SELECT clause ────────────────────────────────────────────
        sql.append("SELECT ");
        sql.append(dataset.getFields().stream()
                .map(SqlGenerator::selectExpression)
                .collect(Collectors.joining(", ")));

        // ── FROM clause ──────────────────────────────────────────────
        sql.append(" FROM ");
        sql.append(fromClause(dataset));

        // ── WHERE clause ─────────────────────────────────────────────
        List<DatasetFilter> filters = dataset.getBaseFilters();
        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(filters.stream()
                    .map(f -> whereClause(f, fieldColumnMap, params))
                    .collect(Collectors.joining(" AND ")));
        }

        // ── GROUP BY clause ──────────────────────────────────────────
        List<QueryDatasetField> dimensions = dataset.getFields().stream()
                .filter(f -> FieldRole.DIMENSION.name().equals(f.getRole()))
                .collect(Collectors.toList());
        if (!dimensions.isEmpty()) {
            sql.append(" GROUP BY ");
            sql.append(dimensions.stream()
                    .map(SqlGenerator::quotedColumnRef)
                    .collect(Collectors.joining(", ")));
        }

        // ── LIMIT / OFFSET ───────────────────────────────────────────
        sql.append(" LIMIT ? OFFSET ?");
        params.add(safePageSize);
        params.add((long) (safePageNo - 1) * safePageSize);

        return SqlRequest.builder()
                .sql(sql.toString())
                .params(params)
                .build();
    }

    /**
     * Generates a parameterized SQL from a {@link SavedQueryView} configuration
     * (requirements §6.3/§6.5) without pagination.
     *
     * @param view            the saved query view (dimensions, measures, filters, sort)
     * @param dataset         the underlying dataset (fields, base filters, source table)
     * @param filterOverrides runtime filter overrides, combined with view filters
     * @return a parameterized {@link SqlRequest} with ordered bind parameters
     */
    public static SqlRequest generateViewSql(SavedQueryView view, QueryDataset dataset,
                                             List<ViewFilter> filterOverrides) {
        return generateViewSql(view, dataset, filterOverrides, -1, -1);
    }

    /**
     * Generates a parameterized SQL from a {@link SavedQueryView} configuration
     * (requirements §6.3/§6.5) with pagination.
     *
     * @param view            the saved query view (dimensions, measures, filters, sort)
     * @param dataset         the underlying dataset (fields, base filters, source table)
     * @param filterOverrides runtime filter overrides, combined with view filters
     * @param pageNo          page number (1-based); ignored when {@code <= 0}
     * @param pageSize        page size, capped at {@link QueryExcelConstants#MAX_PAGE_SIZE};
     *                        ignored when {@code <= 0}
     * @return a parameterized {@link SqlRequest} with ordered bind parameters
     */
    public static SqlRequest generateViewSql(SavedQueryView view, QueryDataset dataset,
                                             List<ViewFilter> filterOverrides,
                                             int pageNo, int pageSize) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        Map<String, String> fieldColumnMap = dataset.getFields().stream()
                .filter(f -> f != null)
                .collect(Collectors.toMap(
                        f -> f.getFieldId() == null ? "" : f.getFieldId(),
                        SqlGenerator::columnName,
                        (a, b) -> a));

        // ── SELECT clause ────────────────────────────────────────────
        sql.append("SELECT ");
        List<String> selectParts = new ArrayList<>();
        for (ViewDimension dim : view.getDimensions()) {
            selectParts.add(quotedColumnRef(dim.getFieldId()));
        }
        for (ViewMeasure measure : view.getMeasures()) {
            String ref = quotedColumnRef(measure.getFieldId());
            if (measure.getAggregation() != null && !measure.getAggregation().isBlank()) {
                selectParts.add(measure.getAggregation().toUpperCase() + "(" + ref + ") AS " + ref);
            } else {
                selectParts.add(ref);
            }
        }
        sql.append(String.join(", ", selectParts));

        // ── FROM clause ──────────────────────────────────────────────
        sql.append(" FROM ").append(fromClause(dataset));

        // ── WHERE clause ─────────────────────────────────────────────
        List<String> whereParts = new ArrayList<>();
        if (dataset.getBaseFilters() != null) {
            for (DatasetFilter f : dataset.getBaseFilters()) {
                String fieldId = f.getFieldId() == null ? "" : f.getFieldId();
                String column = fieldColumnMap.getOrDefault(fieldId, fieldId);
                whereParts.add(filterCondition(column, f.getOperator(), f.getValue(), f.getValues(), params));
            }
        }
        if (view.getFilters() != null) {
            for (ViewFilter f : view.getFilters()) {
                whereParts.add(filterCondition(f.getFieldId(), f.getOperator(), f.getValue(), f.getValues(), params));
            }
        }
        if (filterOverrides != null) {
            for (ViewFilter f : filterOverrides) {
                whereParts.add(filterCondition(f.getFieldId(), f.getOperator(), f.getValue(), f.getValues(), params));
            }
        }
        whereParts.removeIf(String::isBlank);
        if (!whereParts.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereParts));
        }

        // ── GROUP BY clause ──────────────────────────────────────────
        if (view.getDimensions() != null && !view.getDimensions().isEmpty()) {
            sql.append(" GROUP BY ");
            sql.append(view.getDimensions().stream()
                    .map(d -> quotedColumnRef(d.getFieldId()))
                    .collect(Collectors.joining(", ")));
        }

        // ── ORDER BY clause ──────────────────────────────────────────
        if (view.getSort() != null && !view.getSort().isEmpty()) {
            List<String> orderParts = new ArrayList<>();
            for (ViewSort s : view.getSort()) {
                if (s.getFieldId() == null || s.getFieldId().isBlank()) {
                    continue;
                }
                String direction = s.getDirection() != null && "DESC".equalsIgnoreCase(s.getDirection())
                        ? "DESC" : "ASC";
                orderParts.add(quotedColumnRef(s.getFieldId()) + " " + direction);
            }
            if (!orderParts.isEmpty()) {
                sql.append(" ORDER BY ").append(String.join(", ", orderParts));
            }
        }

        // ── LIMIT / OFFSET ───────────────────────────────────────────
        if (pageNo > 0 && pageSize > 0) {
            int safePageSize = Math.min(pageSize, QueryExcelConstants.MAX_PAGE_SIZE);
            sql.append(" LIMIT ? OFFSET ?");
            params.add(safePageSize);
            params.add((long) (pageNo - 1) * safePageSize);
        }

        return SqlRequest.builder()
                .sql(sql.toString())
                .params(params)
                .build();
    }

    // ── SELECT helpers ───────────────────────────────────────────

    /**
     * Returns the SELECT expression for a field.
     * <ul>
     *   <li>Measure with aggregation: {@code AGG(`col`) AS `col`}</li>
     *   <li>Dimension or measure without aggregation: {@code `col`}</li>
     * </ul>
     */
    private static String selectExpression(QueryDatasetField field) {
        String ref = quotedColumnRef(field);
        String aggregation = field.getAggregation();
        if (aggregation != null && !aggregation.isBlank()
                && FieldRole.MEASURE.name().equals(field.getRole())) {
            return aggregation.toUpperCase() + "(" + ref + ") AS " + ref;
        }
        return ref;
    }

    // ── FROM helper ──────────────────────────────────────────────

    /**
     * Builds the FROM clause: {@code `database`.`schema`.`table`} or
     * {@code `database`.`table`} when schema is absent.
     */
    private static String fromClause(QueryDataset dataset) {
        StringBuilder sb = new StringBuilder();
        sb.append(quote(dataset.getDatabaseName())).append('.');
        if (dataset.getSchemaName() != null && !dataset.getSchemaName().isBlank()) {
            sb.append(quote(dataset.getSchemaName())).append('.');
        }
        sb.append(quote(dataset.getTableName()));
        return sb.toString();
    }

    // ── WHERE helpers ────────────────────────────────────────────

    /**
     * Builds a single filter condition and appends its parameter values to
     * {@code params}. The identifier (a fieldId or resolved source column) is
     * backtick-quoted before binding.
     */
    private static String filterCondition(String identifier, String operator, String value,
                                          List<String> values, List<Object> params) {
        String column = quotedColumnRef(identifier == null ? "" : identifier);

        if (operator == null) {
            params.add(value);
            return column + " = ?";
        }

        FilterOperator op;
        try {
            op = FilterOperator.valueOf(operator);
        } catch (IllegalArgumentException e) {
            params.add(value);
            return column + " = ?";
        }

        switch (op) {
            case EQ:
                params.add(value);
                return column + " = ?";
            case NEQ:
                params.add(value);
                return column + " != ?";
            case GT:
                params.add(value);
                return column + " > ?";
            case GTE:
                params.add(value);
                return column + " >= ?";
            case LT:
                params.add(value);
                return column + " < ?";
            case LTE:
                params.add(value);
                return column + " <= ?";
            case BETWEEN:
            case DATE_RANGE:
                params.add(value);
                params.add(values != null && values.size() > 1 ? values.get(1) : null);
                return column + " BETWEEN ? AND ?";
            case IN: {
                if (values == null || values.isEmpty()) {
                    params.add(value);
                    return column + " = ?";
                }
                String placeholders = values.stream()
                        .peek(params::add)
                        .map(v -> "?")
                        .collect(Collectors.joining(", "));
                return column + " IN (" + placeholders + ")";
            }
            case CONTAINS:
                params.add("%" + (value == null ? "" : value) + "%");
                return column + " LIKE ?";
            case DATE_BEFORE:
                params.add(value);
                return column + " < ?";
            case DATE_AFTER:
                params.add(value);
                return column + " > ?";
            default:
                params.add(value);
                return column + " = ?";
        }
    }

    /**
     * Builds a single filter condition and appends its parameter values to
     * {@code params}. The filter fieldId is resolved to its source column via
     * {@code fieldColumnMap}.
     */
    private static String whereClause(DatasetFilter filter, Map<String, String> fieldColumnMap,
                                      List<Object> params) {
        String column = quote(fieldColumnMap.getOrDefault(filter.getFieldId(),
                filter.getFieldId() == null ? "" : filter.getFieldId()));
        String operator = filter.getOperator();

        if (operator == null) {
            // Unknown operator — default to EQ
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
                    // Fallback to single value
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

    // ── quoting helpers ──────────────────────────────────────────

    /**
     * Returns the column reference for a field: the source column name if
     * present, otherwise the field id, all backtick-quoted.
     */
    private static String quotedColumnRef(QueryDatasetField field) {
        return quote(columnName(field));
    }

    /**
     * Returns a backtick-quoted column reference for a field id (used in
     * WHERE clauses where the filter references a field by its logical id).
     */
    private static String quotedColumnRef(String fieldId) {
        return quote(fieldId);
    }

    /**
     * Resolves the SQL column name for a field: sourceColumn if present,
     * otherwise fieldId.
     */
    private static String columnName(QueryDatasetField field) {
        if (field.getSourceColumn() != null && !field.getSourceColumn().isBlank()) {
            return field.getSourceColumn();
        }
        return field.getFieldId() == null ? "" : field.getFieldId();
    }

    /**
     * Wraps an identifier in backticks.
     */
    private static String quote(String identifier) {
        if (identifier == null) {
            return "``";
        }
        return "`" + identifier + "`";
    }
}