package ai.chat2db.community.query.excel.domain.api.service;

/**
 * Provider interface for checking whether the underlying datasource plugin
 * (e.g. Chat2DB's {@code SupportedDatabaseSummary}) supports a given capability.
 * <p>Default implementations return {@code true} for all capabilities; the real
 * binding (T19) will resolve against the actual database plugin registry.</p>
 */
public interface PluginCapabilityProvider {

    /**
     * Returns {@code true} when the datasource plugin supports the given
     * aggregation type (e.g. SUM, AVG, COUNT).
     */
    boolean supportsAggregation(Long datasourceId, String aggregationType);

    /**
     * Returns {@code true} when the datasource plugin supports pagination
     * (LIMIT/OFFSET or equivalent).
     */
    boolean supportsPagination(Long datasourceId);

    /**
     * Returns {@code true} when the datasource plugin supports date/time
     * operator functions (DATE_BEFORE, DATE_AFTER, DATE_RANGE).
     */
    boolean supportsDateOperators(Long datasourceId);

    /**
     * Returns {@code true} when the datasource plugin supports identifier
     * quoting (backtick, double-quote, or bracket).
     */
    boolean supportsIdentifierQuoting(Long datasourceId);
}