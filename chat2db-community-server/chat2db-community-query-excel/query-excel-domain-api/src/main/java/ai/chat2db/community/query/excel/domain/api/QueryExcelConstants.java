package ai.chat2db.community.query.excel.domain.api;

/**
 * Shared constants for the query-excel module.
 */
public final class QueryExcelConstants {

    private QueryExcelConstants() {
        // utility class
    }

    /** Maximum rows returned by a preview query. */
    public static final int MAX_PREVIEW_ROWS = 1000;

    /** Default page size for paginated queries. */
    public static final int DEFAULT_PAGE_SIZE = 100;

    /** Maximum page size allowed. */
    public static final int MAX_PAGE_SIZE = 1000;

    /** Query timeout in milliseconds. */
    public static final long QUERY_TIMEOUT_MS = 30000;

    /** Maximum rows that can be exported. */
    public static final int MAX_EXPORT_ROWS = 10000;

    /** Maximum export file size in bytes (20 MiB). */
    public static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;

    /** Maximum number of sheets per export. */
    public static final int MAX_SHEETS = 10;

    /** Maximum rows per sheet during export. */
    public static final int MAX_ROWS_PER_SHEET = 10000;

    /** Maximum column bindings per sheet. */
    public static final int MAX_BINDINGS_PER_SHEET = 100;

    /** Download token expiry in minutes. */
    public static final int DOWNLOAD_TOKEN_EXPIRY_MINUTES = 60;
}