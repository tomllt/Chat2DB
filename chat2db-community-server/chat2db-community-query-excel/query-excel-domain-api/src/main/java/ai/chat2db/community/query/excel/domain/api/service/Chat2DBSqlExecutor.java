package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.ExecuteQueryRequest;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;

/**
 * Abstraction for executing parameterized SQL queries against a Chat2DB
 * datasource (requirements §6.5). Decouples the query-excel extension from
 * Chat2DB's existing {@code SqlExecutionManager}, allowing the real wiring
 * to be done in the API integration layer.
 * <p>Implementations must handle connection management, timeout enforcement,
 * and result mapping.</p>
 */
public interface Chat2DBSqlExecutor {

    /**
     * Executes a parameterized SQL query and returns the result.
     *
     * @param sql       parameterized SQL string with {@code ?} placeholders
     * @param params    ordered bind parameters
     * @param timeoutMs query timeout in milliseconds
     * @return the query result
     * @deprecated superseded by {@link #execute(ExecuteQueryRequest)}; kept for
     * callers/tests that have no datasource context (no-op local executors).
     */
    @Deprecated
    QueryResult execute(String sql, List<Object> params, int timeoutMs);

    /**
     * Executes a parameterized query scoped to a datasource and returns the result.
     * <p>The request carries the datasource context, so implementations must not
     * rely on a pre-bound ThreadLocal; if a datasource id is present they bind the
     * connection context themselves and always clear it afterwards.</p>
     *
     * @param request self-contained execution request
     * @return the query result
     */
    default QueryResult execute(ExecuteQueryRequest request) {
        return execute(request.getSql(), request.getParams(), (int) request.getTimeoutMs());
    }
}