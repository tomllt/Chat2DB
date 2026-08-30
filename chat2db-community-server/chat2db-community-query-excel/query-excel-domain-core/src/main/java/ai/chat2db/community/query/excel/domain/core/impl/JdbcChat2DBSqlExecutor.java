package ai.chat2db.community.query.excel.domain.core.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import ai.chat2db.community.domain.api.model.request.runtime.DbConnectionContextRequest;
import ai.chat2db.community.domain.api.service.db.IDbConnectionContextService;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExecuteQueryRequest;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBSqlExecutor;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * JDBC-backed {@link Chat2DBSqlExecutor} that uses the Chat2DB connection
 * context infrastructure (requirements §6.5). Binds the datasource context
 * via {@link IDbConnectionContextService}, executes the parameterized query
 * via {@link PreparedStatement}, maps {@link ResultSet} columns and rows into
 * {@link QueryResult}, and always clears the context in a {@code finally}
 * block.
 * <p>This is a Spring {@code @Service} so it can be injected into
 * {@link QueryDatasetServiceImpl} and {@link SavedQueryViewServiceImpl}
 * in a configured Community runtime.</p>
 */
@Slf4j
@Service
public class JdbcChat2DBSqlExecutor implements Chat2DBSqlExecutor {

    private final IDbConnectionContextService contextService;

    @Autowired
    public JdbcChat2DBSqlExecutor(IDbConnectionContextService contextService) {
        this.contextService = contextService;
    }

    /**
     * Deprecated no-context variant — throws {@link UnsupportedOperationException}
     * because this executor requires a datasource context. Use
     * {@link #execute(ExecuteQueryRequest)} instead.
     */
    @Override
    @Deprecated
    public QueryResult execute(String sql, List<Object> params, int timeoutMs) {
        throw new UnsupportedOperationException(
                "JdbcChat2DBSqlExecutor requires a datasource context; "
                        + "use execute(ExecuteQueryRequest) instead");
    }

    /**
     * Executes the parameterized query using the Chat2DB connection context
     * infrastructure. Binds the datasource, creates a {@link PreparedStatement},
     * sets the query timeout (rounded up to seconds), binds every {@code ?}
     * parameter positionally, maps column metadata and rows into
     * {@link QueryResult}, and always cleans up resources and clears the
     * connection context.
     *
     * @param request self-contained execution request with datasource context
     * @return the query result
     * @throws QueryExcelException with {@link ErrorCode#EX_QUERY_TIMEOUT} on
     *                             timeout or SQL failure, or
     *                             {@link ErrorCode#EX_TEMPLATE_NOT_FOUND} on
     *                             missing datasource/profile
     */
    @Override
    public QueryResult execute(ExecuteQueryRequest request) {
        if (request == null || request.getDatasourceId() == null) {
            throw new QueryExcelException(ErrorCode.DS_CONNECTION_FAILED.getCode(),
                    "datasourceId is required");
        }
        String sql = request.getSql();
        if (sql == null || sql.isBlank()) {
            throw new QueryExcelException(ErrorCode.EX_QUERY_TIMEOUT.getCode(),
                    "SQL must not be empty");
        }

        DbConnectionContextRequest ctx = new DbConnectionContextRequest();
        ctx.setDataSourceId(request.getDatasourceId());
        ctx.setDatabaseName(request.getDatabaseName());
        ctx.setSchemaName(request.getSchemaName());

        Connection connection = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            // 1. Bind the connection context
            contextService.bind(ctx);

            // 2. Get the JDBC connection from the bound context
            connection = Chat2DBContext.getConnection();
            if (connection == null) {
                throw new QueryExcelException(ErrorCode.DS_CONNECTION_FAILED.getCode(),
                        "Could not obtain JDBC connection for datasource " + request.getDatasourceId());
            }

            // 3. Prepare the parameterized statement
            stmt = connection.prepareStatement(sql);

            // 4. Set query timeout (convert ms to seconds, ceil)
            int timeoutSeconds = (int) Math.ceil((double) request.getTimeoutMs() / 1000.0);
            stmt.setQueryTimeout(timeoutSeconds);

            // 5. Bind parameters positionally
            List<Object> params = request.getParams();
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
            }

            // 6. Execute the query
            log.debug("Executing query on datasource {}: {}", request.getDatasourceId(), sql);
            boolean isResultSet = stmt.execute();

            // 7. Map result
            if (isResultSet) {
                rs = stmt.getResultSet();
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                // 7a. Column names
                List<String> columns = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }

                // 7b. Rows
                List<List<Object>> rows = new ArrayList<>();
                long total = 0;
                while (rs.next()) {
                    total++;
                    List<Object> row = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                }

                return QueryResult.builder()
                        .columns(columns)
                        .rows(rows)
                        .total(total)
                        .build();
            } else {
                // Non-query (UPDATE, INSERT, etc.) — return empty result
                int updateCount = stmt.getUpdateCount();
                log.warn("Non-query SQL executed on datasource {}; update count={}",
                        request.getDatasourceId(), updateCount);
                return QueryResult.builder()
                        .columns(List.of())
                        .rows(List.of())
                        .total(0)
                        .build();
            }
        } catch (SQLTimeoutException e) {
            String msg = "Query timed out after " + request.getTimeoutMs() + "ms on datasource "
                    + request.getDatasourceId();
            log.warn(msg, e);
            throw new QueryExcelException(ErrorCode.EX_QUERY_TIMEOUT.getCode(), msg);
        } catch (SQLException e) {
            String msg = "SQL execution failed on datasource " + request.getDatasourceId()
                    + ": " + e.getMessage();
            log.error(msg, e);
            throw new QueryExcelException(ErrorCode.EX_QUERY_TIMEOUT.getCode(), msg);
        } catch (QueryExcelException e) {
            throw e;
        } catch (Exception e) {
            String msg = "Query execution failed on datasource " + request.getDatasourceId()
                    + ": " + e.getMessage();
            log.error(msg, e);
            throw new QueryExcelException(ErrorCode.EX_QUERY_TIMEOUT.getCode(), msg);
        } finally {
            // 8. Close resources
            closeQuietly(rs);
            closeQuietly(stmt);
            // 9. Clear the connection context (thread-local)
            contextService.clear();
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("Error closing resource", e);
            }
        }
    }
}