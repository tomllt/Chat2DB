package ai.chat2db.community.query.excel.domain.api;

/**
 * Domain error codes for the query-excel module.
 * <p>Each entry carries a {@code code} (unique identifier) and a
 * {@code message} (human-readable description).</p>
 */
public enum ErrorCode {

    // ── Dataset errors ──────────────────────────────────────────
    DS_NOT_FOUND("DS_001", "QueryDataset not found"),
    DS_NO_FIELDS("DS_002", "At least one field is required"),
    DS_FIELD_NOT_IN_WHITELIST("DS_003", "Field not in source whitelist"),
    DS_INVALID_AGGREGATION("DS_004", "Invalid aggregation for field"),
    DS_TEXT_AGGREGATION("DS_005", "Text fields cannot use SUM or AVG"),
    DS_FILTER_FIELD_NOT_FILTERABLE("DS_006", "Field is not filterable"),
    DS_SORT_FIELD_NOT_SORTABLE("DS_007", "Field is not sortable"),
    DS_SOURCE_TABLE_DELETED("DS_008", "Source table no longer exists"),
    DS_SOURCE_FIELD_DELETED("DS_009", "A source field no longer exists"),
    DS_CONNECTION_FAILED("DS_010", "Could not connect to datasource"),
    DS_PUBLISH_FAILED("DS_011", "Dataset could not be published"),
    DS_VERSION_CONFLICT("DS_012", "Dataset was modified by another user"),

    // ── Query View errors ───────────────────────────────────────
    QV_NOT_FOUND("QV_001", "SavedQueryView not found"),
    QV_DATASET_NOT_PUBLISHED("QV_002", "Referenced dataset is not published"),
    QV_DATASET_VERSION_MISMATCH("QV_003", "Dataset version has changed"),
    QV_FIELD_NOT_FOUND("QV_004", "Referenced field not found in dataset"),
    QV_INVALID_FILTER_FIELD("QV_005", "Filter operator incompatible with field type"),
    QV_INVALID_SORT_FIELD("QV_006", "Field is not sortable"),
    QV_NO_DIMENSION_OR_MEASURE("QV_007", "At least one dimension or measure is required"),
    QV_NO_ROW_OR_COLUMN_FIELD("QV_008", "At least one row or column field is required"),
    QV_VERSION_CONFLICT("QV_009", "View was modified by another user"),
    QV_PAGE_SIZE_EXCEEDED("QV_010", "Page size exceeds the maximum allowed"),
    QV_PLUGIN_CAPABILITY_UNSUPPORTED("QV_011", "Datasource plugin does not support the requested capability"),
    QV_PUBLISH_FAILED("QV_012", "Saved query view could not be published"),

    // ── Export / Template errors ────────────────────────────────
    EX_TEMPLATE_NOT_FOUND("EX_001", "Excel template not found"),
    EX_INVALID_FILE_FORMAT("EX_002", "Only .xlsx files are accepted"),
    EX_CORRUPTED_TEMPLATE("EX_003", "Template file is corrupted"),
    EX_SHEET_NOT_FOUND("EX_004", "Referenced sheet not found in template"),
    EX_BINDING_FIELD_NOT_FOUND("EX_005", "Bound field not found in query view"),
    EX_BINDING_FIELD_DELETED("EX_006", "A bound field was deleted from the view"),
    EX_MERGE_OVERLAP("EX_007", "Merge ranges overlap"),
    EX_MERGE_DATA_OVERLAP("EX_008", "Merge range overlaps data fill region"),
    EX_FIELD_TYPE_INCOMPATIBLE("EX_009", "Field type incompatible with target cell format"),
    EX_QUERY_TIMEOUT("EX_010", "Query timed out"),
    EX_NO_DATA("EX_011", "Query returned no data"),
    EX_NO_PERMISSION("EX_012", "No permission for this operation"),
    EX_FILE_SIZE_EXCEEDED("EX_013", "Export file size exceeds limit"),
    EX_ROW_LIMIT_EXCEEDED("EX_014", "Export row count exceeds limit"),
    EX_SENSITIVE_FIELD_NO_PERMISSION("EX_015", "No permission for sensitive field"),
    EX_FONT_FALLBACK("EX_016", "Font not found, using fallback"),
    EX_CHART_SOURCE_CONFLICT("EX_017", "Chart cannot have both LEGACY_SQL and SAVED_QUERY_VIEW sources"),
    EX_REPORT_BUNDLE_NOT_FOUND("EX_018", "Report bundle not found"),
    EX_REPORT_VERSION_INVALID("EX_019", "Report version is invalid"),
    EX_REPORT_VERSION_DUPLICATE("EX_020", "Report version name already exists"),
    EX_INVALID_FILTER_OVERRIDES("EX_021", "filterOverrides must be valid JSON"),

    // ── General permission ──────────────────────────────────────
    PERMISSION_DENIED("PERM_001", "Access denied");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return code + ": " + message;
    }
}