package ai.chat2db.community.query.excel.domain.api.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainModelTest {

    @Test
    void testQueryDataset() {
        QueryDataset obj = new QueryDataset();
        obj.setId(1L);
        obj.setWorkspaceId(10L);
        obj.setName("test-dataset");
        obj.setDescription("A test dataset");
        obj.setDatasourceId(100L);
        obj.setDatabaseName("test_db");
        obj.setSchemaName("public");
        obj.setTableName("users");
        obj.setSourceObjectType("TABLE");
        obj.setStatus("DRAFT");
        obj.setVersion(1);
        obj.setSourceSchemaHash("abc123");
        obj.setOwnerId(42L);
        Date now = new Date();
        obj.setGmtCreate(now);
        obj.setGmtModified(now);

        assertEquals(1L, obj.getId());
        assertEquals(10L, obj.getWorkspaceId());
        assertEquals("test-dataset", obj.getName());
        assertEquals("A test dataset", obj.getDescription());
        assertEquals(100L, obj.getDatasourceId());
        assertEquals("test_db", obj.getDatabaseName());
        assertEquals("public", obj.getSchemaName());
        assertEquals("users", obj.getTableName());
        assertEquals("TABLE", obj.getSourceObjectType());
        assertEquals("DRAFT", obj.getStatus());
        assertEquals(1, obj.getVersion());
        assertEquals("abc123", obj.getSourceSchemaHash());
        assertEquals(42L, obj.getOwnerId());
        assertEquals(now, obj.getGmtCreate());
        assertEquals(now, obj.getGmtModified());
        assertNotNull(obj.toString());
    }

    @Test
    void testQueryDatasetField() {
        QueryDatasetField obj = new QueryDatasetField();
        obj.setFieldId("f1");
        obj.setSourceColumn("col1");
        obj.setDisplayName("Column 1");
        obj.setDataType("VARCHAR");
        obj.setRole("DIMENSION");
        obj.setAggregation("COUNT");
        obj.setFilterable(true);
        obj.setSortable(false);
        obj.setVisible(true);
        obj.setNumberFormat("#,##0");
        obj.setNullDisplay("-");

        assertEquals("f1", obj.getFieldId());
        assertEquals("col1", obj.getSourceColumn());
        assertEquals("Column 1", obj.getDisplayName());
        assertEquals("VARCHAR", obj.getDataType());
        assertEquals("DIMENSION", obj.getRole());
        assertEquals("COUNT", obj.getAggregation());
        assertTrue(obj.getFilterable());
        assertFalse(obj.getSortable());
        assertTrue(obj.getVisible());
        assertEquals("#,##0", obj.getNumberFormat());
        assertEquals("-", obj.getNullDisplay());
        assertNotNull(obj.toString());
    }

    @Test
    void testSavedQueryView() {
        SavedQueryView obj = new SavedQueryView();
        obj.setId(1L);
        obj.setWorkspaceId(10L);
        obj.setDatasetId(100L);
        obj.setDatasetVersion(2);
        obj.setName("my-view");
        obj.setDescription("A saved view");
        obj.setRowFields(Arrays.asList("r1", "r2"));
        obj.setColumnFields(Arrays.asList("c1"));
        obj.setPageSize(50);
        obj.setStatus("PUBLISHED");
        obj.setVersion(3);
        obj.setOwnerId(42L);
        Date now = new Date();
        obj.setGmtCreate(now);
        obj.setGmtModified(now);

        assertEquals(1L, obj.getId());
        assertEquals(10L, obj.getWorkspaceId());
        assertEquals(100L, obj.getDatasetId());
        assertEquals(2, obj.getDatasetVersion());
        assertEquals("my-view", obj.getName());
        assertEquals("A saved view", obj.getDescription());
        assertEquals(Arrays.asList("r1", "r2"), obj.getRowFields());
        assertEquals(Arrays.asList("c1"), obj.getColumnFields());
        assertEquals(50, obj.getPageSize());
        assertEquals("PUBLISHED", obj.getStatus());
        assertEquals(3, obj.getVersion());
        assertEquals(42L, obj.getOwnerId());
        assertEquals(now, obj.getGmtCreate());
        assertEquals(now, obj.getGmtModified());
        assertNotNull(obj.toString());
    }

    @Test
    void testExcelReportTemplate() {
        ExcelReportTemplate obj = new ExcelReportTemplate();
        obj.setId(1L);
        obj.setWorkspaceId(10L);
        obj.setName("report-template");
        obj.setDescription("Excel report");
        obj.setTemplateFile("/templates/report.xlsx");
        obj.setFileHash("def456");
        obj.setTemplateVersion(1);
        obj.setQueryViewId(100L);
        obj.setStatus("VALID");
        obj.setOwnerId(42L);
        Date now = new Date();
        obj.setGmtCreate(now);
        obj.setGmtModified(now);

        assertEquals(1L, obj.getId());
        assertEquals(10L, obj.getWorkspaceId());
        assertEquals("report-template", obj.getName());
        assertEquals("Excel report", obj.getDescription());
        assertEquals("/templates/report.xlsx", obj.getTemplateFile());
        assertEquals("def456", obj.getFileHash());
        assertEquals(1, obj.getTemplateVersion());
        assertEquals(100L, obj.getQueryViewId());
        assertEquals("VALID", obj.getStatus());
        assertEquals(42L, obj.getOwnerId());
        assertEquals(now, obj.getGmtCreate());
        assertEquals(now, obj.getGmtModified());
        assertNotNull(obj.toString());
    }

    @Test
    void testSheetConfig() {
        SheetConfig obj = new SheetConfig();
        obj.setSheetName("Sheet1");
        obj.setDataStartRow(2);
        obj.setDataStartColumn(1);
        obj.setHeaderMapping("JSON_MAP");
        obj.setRowExpansionMode("INSERT");
        obj.setFreezeRows(1);
        obj.setFreezeColumns(0);
        obj.setAutoWidth(true);
        obj.setEmptyResultBehavior("EMPTY_SHEET");

        assertEquals("Sheet1", obj.getSheetName());
        assertEquals(2, obj.getDataStartRow());
        assertEquals(1, obj.getDataStartColumn());
        assertEquals("JSON_MAP", obj.getHeaderMapping());
        assertEquals("INSERT", obj.getRowExpansionMode());
        assertEquals(1, obj.getFreezeRows());
        assertEquals(0, obj.getFreezeColumns());
        assertTrue(obj.getAutoWidth());
        assertEquals("EMPTY_SHEET", obj.getEmptyResultBehavior());
        assertNotNull(obj.toString());
    }

    @Test
    void testExcelColumnBinding() {
        ExcelColumnBinding obj = new ExcelColumnBinding();
        obj.setQueryFieldId("f1");
        obj.setTargetColumn("A");
        obj.setDisplayName("Name");
        obj.setNumberFormat("@");
        obj.setNullDisplay("N/A");
        obj.setAlignment("LEFT");
        obj.setExportEnabled(true);

        assertEquals("f1", obj.getQueryFieldId());
        assertEquals("A", obj.getTargetColumn());
        assertEquals("Name", obj.getDisplayName());
        assertEquals("@", obj.getNumberFormat());
        assertEquals("N/A", obj.getNullDisplay());
        assertEquals("LEFT", obj.getAlignment());
        assertTrue(obj.getExportEnabled());
        assertNotNull(obj.toString());
    }

    @Test
    void testReportBundleContract() {
        ReportBundle obj = new ReportBundle();
        obj.setId(1L);
        obj.setWorkspaceId(10L);
        obj.setName("sales-report");
        obj.setDescription("Sales report bundle");
        obj.setQueryViewId(100L);
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("f1");
        obj.setBoundFields(Arrays.asList(binding));
        ViewFilter filter = new ViewFilter();
        filter.setFieldId("f1");
        filter.setOperator("EQ");
        obj.setPresetRowFilters(Arrays.asList(filter));
        obj.setActiveVersionId(200L);
        obj.setOwnerId(42L);

        assertEquals(10L, obj.getWorkspaceId());
        assertEquals("sales-report", obj.getName());
        assertEquals(100L, obj.getQueryViewId());
        assertEquals(Arrays.asList(binding), obj.getBoundFields());
        assertEquals(Arrays.asList(filter), obj.getPresetRowFilters());
        assertEquals(200L, obj.getActiveVersionId());
        assertEquals(42L, obj.getOwnerId());
        assertNotNull(obj.toString());
    }

    @Test
    void testReportBundleVersionContract() {
        ReportBundleVersion obj = new ReportBundleVersion();
        obj.setId(200L);
        obj.setWorkspaceId(10L);
        obj.setBundleId(1L);
        obj.setVersionName("January export");
        obj.setVersionNo(2);
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("f1");
        obj.setBoundFieldsSnapshot(Arrays.asList(binding));
        ViewFilter preset = new ViewFilter();
        preset.setFieldId("f1");
        preset.setOperator("EQ");
        obj.setPresetRowFiltersSnapshot(Arrays.asList(preset));
        ViewFilter runtime = new ViewFilter();
        runtime.setFieldId("f1");
        runtime.setOperator("CONTAINS");
        obj.setRowFilter(Arrays.asList(runtime));
        obj.setSelectedRowKeys(Arrays.asList("row-1", "row-2"));

        assertEquals(10L, obj.getWorkspaceId());
        assertEquals(1L, obj.getBundleId());
        assertEquals("January export", obj.getVersionName());
        assertEquals(2, obj.getVersionNo());
        assertEquals(Arrays.asList(binding), obj.getBoundFieldsSnapshot());
        assertEquals(Arrays.asList(preset), obj.getPresetRowFiltersSnapshot());
        assertEquals(Arrays.asList(runtime), obj.getRowFilter());
        assertEquals(Arrays.asList("row-1", "row-2"), obj.getSelectedRowKeys());
        assertNotNull(obj.toString());
    }

    @Test
    void testDatasetFilter() {
        DatasetFilter obj = new DatasetFilter();
        obj.setFieldId("f1");
        obj.setOperator("EQ");
        obj.setValue("val");
        obj.setValues(Arrays.asList("a", "b"));

        assertEquals("f1", obj.getFieldId());
        assertEquals("EQ", obj.getOperator());
        assertEquals("val", obj.getValue());
        assertEquals(Arrays.asList("a", "b"), obj.getValues());
        assertNotNull(obj.toString());
    }

    @Test
    void testViewDimension() {
        ViewDimension obj = new ViewDimension();
        obj.setFieldId("f1");
        obj.setRole("ROW");
        obj.setSortDirection("ASC");

        assertEquals("f1", obj.getFieldId());
        assertEquals("ROW", obj.getRole());
        assertEquals("ASC", obj.getSortDirection());
        assertNotNull(obj.toString());
    }

    @Test
    void testViewMeasure() {
        ViewMeasure obj = new ViewMeasure();
        obj.setFieldId("f1");
        obj.setAggregation("SUM");

        assertEquals("f1", obj.getFieldId());
        assertEquals("SUM", obj.getAggregation());
        assertNotNull(obj.toString());
    }

    @Test
    void testViewFilter() {
        ViewFilter obj = new ViewFilter();
        obj.setFieldId("f1");
        obj.setFilterType("TEXT");
        obj.setOperator("CONTAINS");
        obj.setValue("search");
        obj.setValues(Arrays.asList("s1", "s2"));

        assertEquals("f1", obj.getFieldId());
        assertEquals("TEXT", obj.getFilterType());
        assertEquals("CONTAINS", obj.getOperator());
        assertEquals("search", obj.getValue());
        assertEquals(Arrays.asList("s1", "s2"), obj.getValues());
        assertNotNull(obj.toString());
    }

    @Test
    void testViewSort() {
        ViewSort obj = new ViewSort();
        obj.setFieldId("f1");
        obj.setDirection("DESC");

        assertEquals("f1", obj.getFieldId());
        assertEquals("DESC", obj.getDirection());
        assertNotNull(obj.toString());
    }

    @Test
    void testMergeRange() {
        MergeRange obj = new MergeRange();
        obj.setStartRow(0);
        obj.setEndRow(2);
        obj.setStartColumn(0);
        obj.setEndColumn(3);

        assertEquals(0, obj.getStartRow());
        assertEquals(2, obj.getEndRow());
        assertEquals(0, obj.getStartColumn());
        assertEquals(3, obj.getEndColumn());
        assertNotNull(obj.toString());
    }

    @Test
    void testExcelExportRecord() {
        ExcelExportRecord obj = new ExcelExportRecord();
        obj.setId(1L);
        obj.setWorkspaceId(10L);
        obj.setQueryId("uuid-123");
        obj.setTemplateId(100L);
        obj.setQueryViewId(200L);
        obj.setDatasetId(300L);
        obj.setDatasetVersion(1);
        obj.setQueryViewVersion(2);
        obj.setTemplateVersion(3);
        obj.setUserId(42L);
        obj.setStatus("SUCCESS");
        obj.setQueryMs(1500L);
        obj.setRowCount(500);
        obj.setFileSize(1024L);
        obj.setErrorCode(null);
        obj.setPermissionResult("GRANTED");
        obj.setDownloadToken("token-abc");
        Date now = new Date();
        obj.setDownloadTokenExpiresAt(now);
        obj.setExportedAt(now);
        obj.setGmtCreate(now);
        obj.setGmtModified(now);

        assertEquals(1L, obj.getId());
        assertEquals(10L, obj.getWorkspaceId());
        assertEquals("uuid-123", obj.getQueryId());
        assertEquals(100L, obj.getTemplateId());
        assertEquals(200L, obj.getQueryViewId());
        assertEquals(300L, obj.getDatasetId());
        assertEquals(1, obj.getDatasetVersion());
        assertEquals(2, obj.getQueryViewVersion());
        assertEquals(3, obj.getTemplateVersion());
        assertEquals(42L, obj.getUserId());
        assertEquals("SUCCESS", obj.getStatus());
        assertEquals(1500L, obj.getQueryMs());
        assertEquals(500, obj.getRowCount());
        assertEquals(1024L, obj.getFileSize());
        assertNull(obj.getErrorCode());
        assertEquals("GRANTED", obj.getPermissionResult());
        assertEquals("token-abc", obj.getDownloadToken());
        assertEquals(now, obj.getDownloadTokenExpiresAt());
        assertEquals(now, obj.getExportedAt());
        assertEquals(now, obj.getGmtCreate());
        assertEquals(now, obj.getGmtModified());
        assertNotNull(obj.toString());
    }

    @Test
    void testQueryExcelException() {
        QueryExcelException ex = new QueryExcelException("ERR_001", "Something went wrong");
        assertEquals("ERR_001", ex.getErrorCode());
        assertEquals("Something went wrong", ex.getMessage());

        QueryExcelException ex2 = QueryExcelException.of("ERR_002", "Another error");
        assertEquals("ERR_002", ex2.getErrorCode());
        assertEquals("Another error", ex2.getMessage());
    }

    @Test
    void testLombokEqualsAndHashCode() {
        QueryDatasetField a = new QueryDatasetField();
        a.setFieldId("f1");
        a.setSourceColumn("col1");

        QueryDatasetField b = new QueryDatasetField();
        b.setFieldId("f1");
        b.setSourceColumn("col1");

        QueryDatasetField c = new QueryDatasetField();
        c.setFieldId("f2");
        c.setSourceColumn("col2");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void testNestedObjectRelations() {
        QueryDataset dataset = new QueryDataset();
        dataset.setId(1L);

        QueryDatasetField field = new QueryDatasetField();
        field.setFieldId("f1");
        field.setDisplayName("Amount");

        dataset.setFields(Arrays.asList(field));

        DatasetFilter filter = new DatasetFilter();
        filter.setFieldId("f1");
        filter.setOperator("GT");
        filter.setValue("100");

        dataset.setBaseFilters(Arrays.asList(filter));

        assertEquals(1, dataset.getFields().size());
        assertEquals("Amount", dataset.getFields().get(0).getDisplayName());
        assertEquals(1, dataset.getBaseFilters().size());
        assertEquals("GT", dataset.getBaseFilters().get(0).getOperator());
    }
}