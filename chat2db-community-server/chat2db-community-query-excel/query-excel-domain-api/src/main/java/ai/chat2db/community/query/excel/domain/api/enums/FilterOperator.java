package ai.chat2db.community.query.excel.domain.api.enums;

/**
 * Filter operators for field-level filtering.
 */
public enum FilterOperator {
    EQ,
    NEQ,
    GT,
    GTE,
    LT,
    LTE,
    BETWEEN,
    IN,
    CONTAINS,
    DATE_BEFORE,
    DATE_AFTER,
    DATE_RANGE
}