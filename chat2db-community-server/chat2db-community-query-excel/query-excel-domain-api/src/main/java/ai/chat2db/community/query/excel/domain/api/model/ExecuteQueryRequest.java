package ai.chat2db.community.query.excel.domain.api.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A self-contained parameterized SQL execution request (requirements §6.5).
 * <p>Carries everything the executor needs to run a query against a Chat2DB
 * datasource without relying on a hidden ThreadLocal context that the caller
 * must pre-bind: the datasource identity, the database/schema scoping, the
 * parameterized SQL with {@code ?} placeholders, the ordered bind values, and
 * the query timeout in milliseconds.</p>
 */
public class ExecuteQueryRequest {

    /** Target Chat2DB datasource id. */
    private Long datasourceId;

    /** Database name scope; may be {@code null} for server-level datasets. */
    private String databaseName;

    /** Schema name scope; may be {@code null} for databases without schemas. */
    private String schemaName;

    /** Table name scope, used for diagnostics; execution targets {@link #sql}. */
    private String tableName;

    /** Parameterized SQL string with {@code ?} placeholders. */
    private String sql;

    /** Ordered bind parameter values matching every {@code ?} in {@link #sql}. */
    private List<Object> params = new ArrayList<>();

    /** Query timeout in milliseconds. */
    private long timeoutMs;

    public static Builder builder() {
        return new Builder();
    }

    public Long getDatasourceId() {
        return datasourceId;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParams() {
        return params;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public static final class Builder {
        private final ExecuteQueryRequest request = new ExecuteQueryRequest();

        public Builder datasourceId(Long datasourceId) {
            request.datasourceId = datasourceId;
            return this;
        }

        public Builder databaseName(String databaseName) {
            request.databaseName = databaseName;
            return this;
        }

        public Builder schemaName(String schemaName) {
            request.schemaName = schemaName;
            return this;
        }

        public Builder tableName(String tableName) {
            request.tableName = tableName;
            return this;
        }

        public Builder sql(String sql) {
            request.sql = sql;
            return this;
        }

        public Builder params(List<Object> params) {
            request.params = params == null ? new ArrayList<>() : new ArrayList<>(params);
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            request.timeoutMs = timeoutMs;
            return this;
        }

        public ExecuteQueryRequest build() {
            return request;
        }
    }
}