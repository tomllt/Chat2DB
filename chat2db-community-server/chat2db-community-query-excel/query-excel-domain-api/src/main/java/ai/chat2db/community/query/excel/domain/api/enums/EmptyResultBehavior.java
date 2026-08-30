package ai.chat2db.community.query.excel.domain.api.enums;

/**
 * Behavior when a query returns no data during export.
 */
public enum EmptyResultBehavior {
    EMPTY_SHEET,
    SKIP_SHEET,
    ERROR
}