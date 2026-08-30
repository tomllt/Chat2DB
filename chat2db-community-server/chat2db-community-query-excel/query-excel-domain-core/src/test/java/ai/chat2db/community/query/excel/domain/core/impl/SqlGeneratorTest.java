package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.chat2db.community.query.excel.domain.api.QueryExcelConstants;
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
import org.junit.jupiter.api.Test;

/**
 * T8: SQL generation from {@link QueryDataset} configuration.
 */
class SqlGeneratorTest {

    // ── simple SELECT ───────────────────────────────────────────

    @Test
    void simpleSelectWithoutFilters() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region"), dimension("f2", "product")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().startsWith("SELECT `region`, `product` FROM"));
        assertTrue(request.getSql().contains("`test_db`.`sales`"));
        assertFalse(request.getSql().contains("WHERE"));
        assertTrue(request.getSql().contains("GROUP BY"));
        assertTrue(request.getSql().contains("LIMIT ? OFFSET ?"));
        assertEquals(2, request.getParams().size());
        assertEquals(10, request.getParams().get(0));
        assertEquals(0L, request.getParams().get(1));
    }

    // ── SELECT with measure + aggregation ───────────────────────

    @Test
    void selectWithMeasureAggregation() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(
                measure("f1", "amount", "SUM"),
                dimension("f2", "region")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);
        String sql = request.getSql();

        assertTrue(sql.contains("SUM(`amount`) AS `amount`"), "Should wrap measure with SUM()");
        assertTrue(sql.contains("`region`"), "Should include dimension");
    }

    @Test
    void selectWithAvgAggregation() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(
                measure("f1", "score", "AVG"),
                dimension("f2", "category")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("AVG(`score`) AS `score`"));
    }

    @Test
    void selectWithCountAggregation() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(
                measure("f1", "id", "COUNT"),
                dimension("f2", "status")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("COUNT(`id`) AS `id`"));
    }

    // ── GROUP BY ────────────────────────────────────────────────

    @Test
    void selectWithGroupBy() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(
                measure("f1", "amount", "SUM"),
                dimension("f2", "region")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("GROUP BY `region`"),
                "Should have GROUP BY for dimension fields");
    }

    @Test
    void selectNoGroupByWhenNoDimensions() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(
                measure("f1", "amount", "SUM"),
                measure("f2", "count", "COUNT")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertFalse(request.getSql().contains("GROUP BY"),
                "No GROUP BY when all fields are measures");
    }

    // ── filters ─────────────────────────────────────────────────

    @Test
    void selectWithEqFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region"), dimension("f2", "product")));
        ds.setBaseFilters(List.of(filter("f1", "EQ", "EU")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("WHERE `region` = ?"));
        assertEquals(3, request.getParams().size());
        assertEquals("EU", request.getParams().get(0));
    }

    @Test
    void selectWithNeqFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region")));
        ds.setBaseFilters(List.of(filter("f1", "NEQ", "EU")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`region` != ?"));
        assertEquals("EU", request.getParams().get(0));
    }

    @Test
    void selectWithGtFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(measure("f1", "amount", null)));
        ds.setBaseFilters(List.of(filter("f1", "GT", "100")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`amount` > ?"));
        assertEquals("100", request.getParams().get(0));
    }

    @Test
    void selectWithGteFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(measure("f1", "amount", null)));
        ds.setBaseFilters(List.of(filter("f1", "GTE", "100")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`amount` >= ?"));
    }

    @Test
    void selectWithLtFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(measure("f1", "amount", null)));
        ds.setBaseFilters(List.of(filter("f1", "LT", "50")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`amount` < ?"));
    }

    @Test
    void selectWithLteFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(measure("f1", "amount", null)));
        ds.setBaseFilters(List.of(filter("f1", "LTE", "50")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`amount` <= ?"));
    }

    @Test
    void selectWithBetweenFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(measure("f1", "amount", null)));
        DatasetFilter f = new DatasetFilter();
        f.setFieldId("f1");
        f.setOperator("BETWEEN");
        f.setValue("100");
        f.setValues(Arrays.asList("100", "200"));
        ds.setBaseFilters(List.of(f));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`amount` BETWEEN ? AND ?"));
        assertEquals(4, request.getParams().size()); // BETWEEN values + LIMIT + OFFSET
        assertEquals("100", request.getParams().get(0));
        assertEquals("200", request.getParams().get(1));
    }

    @Test
    void selectWithInFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region")));
        DatasetFilter f = new DatasetFilter();
        f.setFieldId("f1");
        f.setOperator("IN");
        f.setValues(Arrays.asList("EU", "US", "APAC"));
        ds.setBaseFilters(List.of(f));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`region` IN (?, ?, ?)"));
        assertEquals(5, request.getParams().size()); // 3 IN values + LIMIT + OFFSET
        assertEquals("EU", request.getParams().get(0));
        assertEquals("US", request.getParams().get(1));
        assertEquals("APAC", request.getParams().get(2));
    }

    @Test
    void selectWithContainsFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "product")));
        ds.setBaseFilters(List.of(filter("f1", "CONTAINS", "widget")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`product` LIKE ?"));
        assertEquals("%widget%", request.getParams().get(0));
    }

    @Test
    void selectWithDateBeforeFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "order_date")));
        ds.setBaseFilters(List.of(filter("f1", "DATE_BEFORE", "2026-01-01")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`order_date` < ?"));
        assertEquals("2026-01-01", request.getParams().get(0));
    }

    @Test
    void selectWithDateAfterFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "order_date")));
        ds.setBaseFilters(List.of(filter("f1", "DATE_AFTER", "2026-01-01")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`order_date` > ?"));
    }

    @Test
    void selectWithDateRangeFilter() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "order_date")));
        DatasetFilter f = new DatasetFilter();
        f.setFieldId("f1");
        f.setOperator("DATE_RANGE");
        f.setValue("2026-01-01");
        f.setValues(Arrays.asList("2026-01-01", "2026-06-30"));
        ds.setBaseFilters(List.of(f));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`order_date` BETWEEN ? AND ?"));
        assertEquals("2026-01-01", request.getParams().get(0));
        assertEquals("2026-06-30", request.getParams().get(1));
    }

    // ── LIMIT / OFFSET ──────────────────────────────────────────

    @Test
    void selectWithPaginationPage2() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 2, 10);

        assertTrue(request.getSql().contains("LIMIT ? OFFSET ?"));
        assertEquals(2, request.getParams().size());
        assertEquals(10, request.getParams().get(0));
        assertEquals(10L, request.getParams().get(1));
    }

    @Test
    void selectWithPaginationPage3() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 3, 20);

        assertEquals(20, request.getParams().get(0));
        assertEquals(40L, request.getParams().get(1));
    }

    // ── MAX_PAGE_SIZE enforcement ───────────────────────────────

    @Test
    void pageSizeCappedAtMax() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, QueryExcelConstants.MAX_PAGE_SIZE + 500);

        assertEquals(QueryExcelConstants.MAX_PAGE_SIZE, request.getParams().get(0));
    }

    @Test
    void pageSizeZeroDefaultsToDefault() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 0);

        assertEquals(QueryExcelConstants.DEFAULT_PAGE_SIZE, request.getParams().get(0));
    }

    @Test
    void pageNoZeroDefaultsToOne() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region")));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 0, 10);

        assertEquals(0L, request.getParams().get(1));
    }

    // ── parameter count consistency ─────────────────────────────

    @Test
    void paramCountMatchesPlaceholderCount() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(
                measure("f1", "amount", "SUM"),
                dimension("f2", "region")));
        DatasetFilter f = new DatasetFilter();
        f.setFieldId("f1");
        f.setOperator("BETWEEN");
        f.setValue("100");
        f.setValues(Arrays.asList("100", "500"));
        ds.setBaseFilters(List.of(f));

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 2, 25);
        String sql = request.getSql();

        long placeholderCount = sql.chars().filter(c -> c == '?').count();
        assertEquals((long) request.getParams().size(), placeholderCount,
                "Number of ? placeholders must match params size");
    }

    // ── empty filters ───────────────────────────────────────────

    @Test
    void noWhereClauseWhenFiltersEmpty() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region")));
        ds.setBaseFilters(null);

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertFalse(request.getSql().contains("WHERE"));
    }

    @Test
    void noWhereClauseWhenFiltersEmptyList() {
        QueryDataset ds = dataset();
        ds.setFields(Arrays.asList(dimension("f1", "region")));
        ds.setBaseFilters(List.of());

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertFalse(request.getSql().contains("WHERE"));
    }

    // ── schema handling ─────────────────────────────────────────

    @Test
    void fromClauseIncludesSchema() {
        QueryDataset ds = dataset();
        ds.setSchemaName("public");

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`test_db`.`public`.`sales`"));
    }

    @Test
    void fromClauseNoSchema() {
        QueryDataset ds = dataset();
        ds.setSchemaName(null);

        SqlRequest request = SqlGenerator.generatePreviewSql(ds, 1, 10);

        assertTrue(request.getSql().contains("`test_db`.`sales`"));
    }

    // ── generateViewSql ─────────────────────────────────────────

    @Test
    void viewSqlWithDimensionsAndMeasures() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        view.setMeasures(List.of(meas("amount", "SUM")));
        QueryDataset ds = dataset();

        SqlRequest request = SqlGenerator.generateViewSql(view, ds, null);

        String sql = request.getSql();
        assertTrue(sql.startsWith("SELECT `city`, SUM(`amount`) AS `amount` FROM"));
        assertTrue(sql.contains("`test_db`.`sales`"));
        assertFalse(sql.contains("WHERE"));
        assertTrue(sql.contains("GROUP BY"));
        assertFalse(sql.contains("LIMIT"));
    }

    @Test
    void viewSqlWithGroupByClause() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city"), dim("category")));
        view.setMeasures(List.of(meas("amount", "SUM")));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null);

        assertTrue(request.getSql().contains("GROUP BY `city`, `category`"));
    }

    @Test
    void viewSqlWithOrderBySort() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        view.setMeasures(List.of(meas("amount", "SUM")));
        view.setSort(List.of(sort("city", "ASC"), sort("amount", "DESC")));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null);

        String sql = request.getSql();
        assertTrue(sql.contains("ORDER BY `city` ASC, `amount` DESC"));
    }

    @Test
    void viewSqlWithViewFilters() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        view.setFilters(List.of(vf("status", "EQ", "ACTIVE")));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null);

        assertTrue(request.getSql().contains("WHERE `status` = ?"));
        assertEquals(1, request.getParams().size());
        assertEquals("ACTIVE", request.getParams().get(0));
    }

    @Test
    void viewSqlWithBaseFilters() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        QueryDataset ds = dataset();
        ds.setBaseFilters(List.of(filter("f1", "EQ", "EU")));

        SqlRequest request = SqlGenerator.generateViewSql(view, ds, null);

        assertTrue(request.getSql().contains("WHERE `region` = ?"));
        assertEquals(1, request.getParams().size());
        assertEquals("EU", request.getParams().get(0));
    }

    @Test
    void viewSqlWithFilterOverrides() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        view.setFilters(List.of(vf("status", "EQ", "ACTIVE")));
        List<ViewFilter> overrides = List.of(vf("amount", "GT", "100"));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), overrides);

        String sql = request.getSql();
        assertTrue(sql.contains("`status` = ?"));
        assertTrue(sql.contains("`amount` > ?"));
        assertEquals(2, request.getParams().size());
    }

    @Test
    void viewSqlWithBetweenFilter() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        ViewFilter f = new ViewFilter();
        f.setFieldId("amount");
        f.setOperator("BETWEEN");
        f.setValue("100");
        f.setValues(List.of("100", "500"));
        view.setFilters(List.of(f));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null);

        assertTrue(request.getSql().contains("`amount` BETWEEN ? AND ?"));
        assertEquals(2, request.getParams().size());
        assertEquals("100", request.getParams().get(0));
        assertEquals("500", request.getParams().get(1));
    }

    @Test
    void viewSqlWithInFilter() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        ViewFilter f = new ViewFilter();
        f.setFieldId("region");
        f.setOperator("IN");
        f.setValues(List.of("EU", "US", "APAC"));
        view.setFilters(List.of(f));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null);

        assertTrue(request.getSql().contains("`region` IN (?, ?, ?)"));
        assertEquals(3, request.getParams().size());
    }

    @Test
    void viewSqlWithContainsFilter() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        view.setFilters(List.of(vf("product", "CONTAINS", "widget")));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null);

        assertTrue(request.getSql().contains("`product` LIKE ?"));
        assertEquals("%widget%", request.getParams().get(0));
    }

    @Test
    void viewSqlNoDimensionsNoGroupBy() {
        SavedQueryView view = view();
        view.setDimensions(List.of());
        view.setMeasures(List.of(meas("amount", "SUM")));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null);

        assertFalse(request.getSql().contains("GROUP BY"));
    }

    @Test
    void viewSqlNoMeasuresNoAggregation() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        view.setMeasures(List.of(meas("amount", null)));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null);

        String sql = request.getSql();
        assertTrue(sql.contains("`city`"));
        assertTrue(sql.contains("`amount`"));
        assertFalse(sql.contains("SUM("));
        assertFalse(sql.contains(" AS "));
    }

    @Test
    void viewSqlWithPagination() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null, 2, 25);

        assertTrue(request.getSql().contains("LIMIT ? OFFSET ?"));
        assertEquals(2, request.getParams().size());
        assertEquals(25, request.getParams().get(0));
        assertEquals(25L, request.getParams().get(1));
    }

    @Test
    void viewSqlWithoutPagination() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null, -1, -1);

        assertFalse(request.getSql().contains("LIMIT"));
    }

    @Test
    void viewSqlEmptyFilterOverrides() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        view.setFilters(List.of(vf("status", "EQ", "ACTIVE")));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), List.of());

        assertTrue(request.getSql().contains("WHERE `status` = ?"));
        assertEquals(1, request.getParams().size());
    }

    @Test
    void viewSqlBacktickQuotingOnAllIdentifiers() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        view.setMeasures(List.of(meas("amount", "SUM")));
        view.setSort(List.of(sort("amount", "DESC")));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null, 1, 10);

        String sql = request.getSql();
        assertTrue(sql.contains("`city`"));
        assertTrue(sql.contains("`amount`"));
        assertTrue(sql.contains("`test_db`"));
        assertTrue(sql.contains("`sales`"));
    }

    @Test
    void viewSqlParamCountMatchesPlaceholderCount() {
        SavedQueryView view = view();
        view.setDimensions(List.of(dim("city")));
        view.setMeasures(List.of(meas("amount", "SUM")));
        view.setFilters(List.of(
                vf("status", "EQ", "ACTIVE"),
                vf("region", "IN", null)
        ));
        view.getFilters().get(1).setValues(List.of("EU", "US"));
        view.setSort(List.of(sort("amount", "DESC")));

        SqlRequest request = SqlGenerator.generateViewSql(view, dataset(), null, 1, 10);

        String sql = request.getSql();
        long placeholderCount = sql.chars().filter(c -> c == '?').count();
        assertEquals((long) request.getParams().size(), placeholderCount,
                "Number of ? placeholders must match params size");
    }

    // ── helpers ─────────────────────────────────────────────────

    private static QueryDataset dataset() {
        QueryDataset ds = new QueryDataset();
        ds.setDatabaseName("test_db");
        ds.setTableName("sales");
        ds.setFields(new ArrayList<>(List.of(dimension("f1", "region"))));
        return ds;
    }

    private static QueryDatasetField dimension(String fieldId, String sourceColumn) {
        QueryDatasetField f = new QueryDatasetField();
        f.setFieldId(fieldId);
        f.setSourceColumn(sourceColumn);
        f.setRole(FieldRole.DIMENSION.name());
        return f;
    }

    private static QueryDatasetField measure(String fieldId, String sourceColumn, String aggregation) {
        QueryDatasetField f = new QueryDatasetField();
        f.setFieldId(fieldId);
        f.setSourceColumn(sourceColumn);
        f.setRole(FieldRole.MEASURE.name());
        f.setAggregation(aggregation);
        return f;
    }

    private static DatasetFilter filter(String fieldId, String operator, String value) {
        DatasetFilter f = new DatasetFilter();
        f.setFieldId(fieldId);
        f.setOperator(operator);
        f.setValue(value);
        return f;
    }

    private static SavedQueryView view() {
        SavedQueryView v = new SavedQueryView();
        v.setDimensions(new ArrayList<>());
        v.setMeasures(new ArrayList<>());
        return v;
    }

    private static ViewDimension dim(String fieldId) {
        ViewDimension d = new ViewDimension();
        d.setFieldId(fieldId);
        return d;
    }

    private static ViewMeasure meas(String fieldId, String aggregation) {
        ViewMeasure m = new ViewMeasure();
        m.setFieldId(fieldId);
        m.setAggregation(aggregation);
        return m;
    }

    private static ViewFilter vf(String fieldId, String operator, String value) {
        ViewFilter f = new ViewFilter();
        f.setFieldId(fieldId);
        f.setOperator(operator);
        f.setValue(value);
        return f;
    }

    private static ViewSort sort(String fieldId, String direction) {
        ViewSort s = new ViewSort();
        s.setFieldId(fieldId);
        s.setDirection(direction);
        return s;
    }
}