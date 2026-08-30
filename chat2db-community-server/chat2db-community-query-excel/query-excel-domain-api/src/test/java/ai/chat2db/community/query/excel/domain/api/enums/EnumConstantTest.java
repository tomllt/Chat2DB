package ai.chat2db.community.query.excel.domain.api.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.QueryExcelConstants;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies that all enum types, error codes, and constants are defined as expected.
 */
class EnumConstantTest {

    // ── SourceObjectType ────────────────────────────────────────

    @Test
    void sourceObjectType_shouldHaveExpectedValues() {
        SourceObjectType[] values = SourceObjectType.values();
        assertEquals(2, values.length);
        assertEquals(SourceObjectType.TABLE, SourceObjectType.valueOf("TABLE"));
        assertEquals(SourceObjectType.DATABASE_VIEW, SourceObjectType.valueOf("DATABASE_VIEW"));
    }

    // ── QueryDatasetStatus ─────────────────────────────────────

    @Test
    void queryDatasetStatus_shouldHaveExpectedValues() {
        assertEquals(3, QueryDatasetStatus.values().length);
        assertEquals(QueryDatasetStatus.DRAFT, QueryDatasetStatus.valueOf("DRAFT"));
        assertEquals(QueryDatasetStatus.PUBLISHED, QueryDatasetStatus.valueOf("PUBLISHED"));
        assertEquals(QueryDatasetStatus.DISABLED, QueryDatasetStatus.valueOf("DISABLED"));
    }

    // ── FieldRole ───────────────────────────────────────────────

    @Test
    void fieldRole_shouldHaveExpectedValues() {
        assertEquals(2, FieldRole.values().length);
        assertEquals(FieldRole.DIMENSION, FieldRole.valueOf("DIMENSION"));
        assertEquals(FieldRole.MEASURE, FieldRole.valueOf("MEASURE"));
    }

    // ── AggregationType ─────────────────────────────────────────

    @Test
    void aggregationType_shouldHaveExpectedValues() {
        assertEquals(5, AggregationType.values().length);
        assertEquals(AggregationType.COUNT, AggregationType.valueOf("COUNT"));
        assertEquals(AggregationType.SUM, AggregationType.valueOf("SUM"));
        assertEquals(AggregationType.AVG, AggregationType.valueOf("AVG"));
        assertEquals(AggregationType.MIN, AggregationType.valueOf("MIN"));
        assertEquals(AggregationType.MAX, AggregationType.valueOf("MAX"));
    }

    // ── FilterOperator ──────────────────────────────────────────

    @Test
    void filterOperator_shouldHaveExpectedValues() {
        assertEquals(12, FilterOperator.values().length);
        assertEquals(FilterOperator.EQ, FilterOperator.valueOf("EQ"));
        assertEquals(FilterOperator.NEQ, FilterOperator.valueOf("NEQ"));
        assertEquals(FilterOperator.GT, FilterOperator.valueOf("GT"));
        assertEquals(FilterOperator.GTE, FilterOperator.valueOf("GTE"));
        assertEquals(FilterOperator.LT, FilterOperator.valueOf("LT"));
        assertEquals(FilterOperator.LTE, FilterOperator.valueOf("LTE"));
        assertEquals(FilterOperator.BETWEEN, FilterOperator.valueOf("BETWEEN"));
        assertEquals(FilterOperator.IN, FilterOperator.valueOf("IN"));
        assertEquals(FilterOperator.CONTAINS, FilterOperator.valueOf("CONTAINS"));
        assertEquals(FilterOperator.DATE_BEFORE, FilterOperator.valueOf("DATE_BEFORE"));
        assertEquals(FilterOperator.DATE_AFTER, FilterOperator.valueOf("DATE_AFTER"));
        assertEquals(FilterOperator.DATE_RANGE, FilterOperator.valueOf("DATE_RANGE"));
    }

    // ── FilterType ──────────────────────────────────────────────

    @Test
    void filterType_shouldHaveExpectedValues() {
        assertEquals(4, FilterType.values().length);
        assertEquals(FilterType.TEXT, FilterType.valueOf("TEXT"));
        assertEquals(FilterType.NUMERIC, FilterType.valueOf("NUMERIC"));
        assertEquals(FilterType.DATE, FilterType.valueOf("DATE"));
        assertEquals(FilterType.BOOLEAN, FilterType.valueOf("BOOLEAN"));
    }

    // ── SortDirection ───────────────────────────────────────────

    @Test
    void sortDirection_shouldHaveExpectedValues() {
        assertEquals(3, SortDirection.values().length);
        assertEquals(SortDirection.ASC, SortDirection.valueOf("ASC"));
        assertEquals(SortDirection.DESC, SortDirection.valueOf("DESC"));
        assertEquals(SortDirection.NONE, SortDirection.valueOf("NONE"));
    }

    // ── ViewFieldRole ───────────────────────────────────────────

    @Test
    void viewFieldRole_shouldHaveExpectedValues() {
        assertEquals(2, ViewFieldRole.values().length);
        assertEquals(ViewFieldRole.ROW, ViewFieldRole.valueOf("ROW"));
        assertEquals(ViewFieldRole.COLUMN, ViewFieldRole.valueOf("COLUMN"));
    }

    // ── SavedQueryViewStatus ────────────────────────────────────

    @Test
    void savedQueryViewStatus_shouldHaveExpectedValues() {
        assertEquals(4, SavedQueryViewStatus.values().length);
        assertEquals(SavedQueryViewStatus.DRAFT, SavedQueryViewStatus.valueOf("DRAFT"));
        assertEquals(SavedQueryViewStatus.PUBLISHED, SavedQueryViewStatus.valueOf("PUBLISHED"));
        assertEquals(SavedQueryViewStatus.INVALID, SavedQueryViewStatus.valueOf("INVALID"));
        assertEquals(SavedQueryViewStatus.DISABLED, SavedQueryViewStatus.valueOf("DISABLED"));
    }

    // ── TemplateStatus ──────────────────────────────────────────

    @Test
    void templateStatus_shouldHaveExpectedValues() {
        assertEquals(3, TemplateStatus.values().length);
        assertEquals(TemplateStatus.VALID, TemplateStatus.valueOf("VALID"));
        assertEquals(TemplateStatus.INVALID, TemplateStatus.valueOf("INVALID"));
        assertEquals(TemplateStatus.DISABLED, TemplateStatus.valueOf("DISABLED"));
    }

    // ── ExportStatus ────────────────────────────────────────────

    @Test
    void exportStatus_shouldHaveExpectedValues() {
        assertEquals(4, ExportStatus.values().length);
        assertEquals(ExportStatus.PENDING, ExportStatus.valueOf("PENDING"));
        assertEquals(ExportStatus.RUNNING, ExportStatus.valueOf("RUNNING"));
        assertEquals(ExportStatus.SUCCESS, ExportStatus.valueOf("SUCCESS"));
        assertEquals(ExportStatus.FAILED, ExportStatus.valueOf("FAILED"));
    }

    // ── RowExpansionMode ────────────────────────────────────────

    @Test
    void rowExpansionMode_shouldHaveExpectedValues() {
        assertEquals(2, RowExpansionMode.values().length);
        assertEquals(RowExpansionMode.INSERT, RowExpansionMode.valueOf("INSERT"));
        assertEquals(RowExpansionMode.OVERWRITE, RowExpansionMode.valueOf("OVERWRITE"));
    }

    // ── EmptyResultBehavior ─────────────────────────────────────

    @Test
    void emptyResultBehavior_shouldHaveExpectedValues() {
        assertEquals(3, EmptyResultBehavior.values().length);
        assertEquals(EmptyResultBehavior.EMPTY_SHEET, EmptyResultBehavior.valueOf("EMPTY_SHEET"));
        assertEquals(EmptyResultBehavior.SKIP_SHEET, EmptyResultBehavior.valueOf("SKIP_SHEET"));
        assertEquals(EmptyResultBehavior.ERROR, EmptyResultBehavior.valueOf("ERROR"));
    }

    // ── Alignment ───────────────────────────────────────────────

    @Test
    void alignment_shouldHaveExpectedValues() {
        assertEquals(3, Alignment.values().length);
        assertEquals(Alignment.LEFT, Alignment.valueOf("LEFT"));
        assertEquals(Alignment.CENTER, Alignment.valueOf("CENTER"));
        assertEquals(Alignment.RIGHT, Alignment.valueOf("RIGHT"));
    }

    // ── ChartDataSourceType ─────────────────────────────────────

    @Test
    void chartDataSourceType_shouldHaveExpectedValues() {
        assertEquals(2, ChartDataSourceType.values().length);
        assertEquals(ChartDataSourceType.LEGACY_SQL, ChartDataSourceType.valueOf("LEGACY_SQL"));
        assertEquals(ChartDataSourceType.SAVED_QUERY_VIEW, ChartDataSourceType.valueOf("SAVED_QUERY_VIEW"));
    }

    // ── ErrorCode ────────────────────────────────────────────────

    @Test
    void errorCode_shouldHaveAllEntries() {
        ErrorCode[] values = ErrorCode.values();
        assertTrue(values.length > 0);

        // Verify each entry has a non-null code and message
        for (ErrorCode ec : values) {
            assertNotNull(ec.getCode(), "Code must not be null for " + ec.name());
            assertNotNull(ec.getMessage(), "Message must not be null for " + ec.name());
        }

        // Verify codes are unique
        Set<String> codes = new HashSet<>();
        for (ErrorCode ec : values) {
            assertTrue(codes.add(ec.getCode()), "Duplicate code: " + ec.getCode());
        }
    }

    @Test
    void errorCode_shouldHaveExpectedSpecificEntries() {
        assertEquals("DS_001", ErrorCode.DS_NOT_FOUND.getCode());
        assertEquals("QueryDataset not found", ErrorCode.DS_NOT_FOUND.getMessage());

        assertEquals("QV_001", ErrorCode.QV_NOT_FOUND.getCode());
        assertEquals("SavedQueryView not found", ErrorCode.QV_NOT_FOUND.getMessage());

        assertEquals("EX_001", ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode());
        assertEquals("Excel template not found", ErrorCode.EX_TEMPLATE_NOT_FOUND.getMessage());

        assertEquals("PERM_001", ErrorCode.PERMISSION_DENIED.getCode());
        assertEquals("Access denied", ErrorCode.PERMISSION_DENIED.getMessage());
    }

    // ── QueryExcelConstants ─────────────────────────────────────

    @Test
    void queryExcelConstants_shouldHaveExpectedValues() {
        assertEquals(1000, QueryExcelConstants.MAX_PREVIEW_ROWS);
        assertEquals(100, QueryExcelConstants.DEFAULT_PAGE_SIZE);
        assertEquals(1000, QueryExcelConstants.MAX_PAGE_SIZE);
        assertEquals(30000L, QueryExcelConstants.QUERY_TIMEOUT_MS);
        assertEquals(10000, QueryExcelConstants.MAX_EXPORT_ROWS);
        assertEquals(20 * 1024 * 1024L, QueryExcelConstants.MAX_FILE_SIZE_BYTES);
        assertEquals(10, QueryExcelConstants.MAX_SHEETS);
        assertEquals(10000, QueryExcelConstants.MAX_ROWS_PER_SHEET);
        assertEquals(100, QueryExcelConstants.MAX_BINDINGS_PER_SHEET);
        assertEquals(60, QueryExcelConstants.DOWNLOAD_TOKEN_EXPIRY_MINUTES);
    }
}