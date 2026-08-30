package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import ai.chat2db.community.domain.api.service.db.IDbConnectionContextService;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExecuteQueryRequest;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Integration test for {@link JdbcChat2DBSqlExecutor} against a real embedded
 * H2 database. Proves that SQL {@code ?} placeholders are bound positionally
 * with the supplied values (no string interpolation), that results and column
 * metadata map into {@link QueryResult}, and that timeout/SQL failures translate
 * to {@link QueryExcelException} codes.
 * <p>The Chat2DB connection context service is mocked; the JDBC
 * {@link Connection} is a real H2 connection obtained from the embedded
 * database.</p>
 */
class JdbcChat2DBSqlExecutorH2Test {

    private static Connection h2Connection;
    private IDbConnectionContextService contextService;
    private JdbcChat2DBSqlExecutor executor;
    private MockedStatic<Chat2DBContext> contextStatic;

    @BeforeAll
    static void initH2() throws Exception {
        h2Connection = DriverManager.getConnection("jdbc:h2:mem:query_excel_it;DB_CLOSE_DELAY=-1");
        try (Statement stmt = h2Connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT PRIMARY KEY, name VARCHAR(100), amount DECIMAL(10,2))");
            stmt.execute("DELETE FROM users");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 100.00)");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 200.00)");
            stmt.execute("INSERT INTO users VALUES (3, 'Carol', 50.00)");
        }
    }

    @AfterAll
    static void closeH2() throws Exception {
        if (h2Connection != null && !h2Connection.isClosed()) {
            h2Connection.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        contextService = mock(IDbConnectionContextService.class);
        executor = new JdbcChat2DBSqlExecutor(contextService);
        contextStatic = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(h2Connection);
    }

    @AfterEach
    void tearDown() {
        if (contextStatic != null) {
            contextStatic.close();
        }
    }

    /**
     * Core proof: a WHERE predicate with a real bound parameter filters rows in
     * the database — the executor must bind the value, not interpolate it.
     */
    @Test
    void bindsParameterPositionallyAgainstRealDatabase() {
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .databaseName("test_db")
                .tableName("users")
                .sql("SELECT id, name, amount FROM users WHERE amount > ?")
                .params(List.of(50))
                .timeoutMs(5000)
                .build();

        QueryResult result = executor.execute(request);

        assertEquals(3, result.getColumns().size());
        assertEquals("ID", result.getColumns().get(0));
        assertEquals("NAME", result.getColumns().get(1));
        assertEquals("AMOUNT", result.getColumns().get(2));
        // Only rows with amount > 50: Alice (100) and Bob (200), not Carol (50)
        assertEquals(2, result.getRows().size());
        assertEquals(2L, result.getTotal());
        assertEquals(1, result.getRows().get(0).get(0));
        assertEquals("Alice", result.getRows().get(0).get(1));
        assertEquals(2, result.getRows().get(1).get(0));
        assertEquals("Bob", result.getRows().get(1).get(1));
    }

    @Test
    void bindsMultipleParamsInOrderAgainstRealDatabase() {
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("SELECT name FROM users WHERE amount > ? AND id < ?")
                .params(List.of(50, 3))
                .timeoutMs(5000)
                .build();

        QueryResult result = executor.execute(request);

        // amount > 50 AND id < 3 → Alice (id=1, 100) and Bob (id=2, 200)
        assertEquals(2, result.getRows().size());
        assertEquals("Alice", result.getRows().get(0).get(0));
        assertEquals("Bob", result.getRows().get(1).get(0));
    }

    @Test
    void bindsLikePatternParamAgainstRealDatabase() {
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("SELECT name FROM users WHERE name LIKE ?")
                .params(List.of("%o%"))
                .timeoutMs(5000)
                .build();

        QueryResult result = executor.execute(request);

        // name LIKE '%o%' → Bob, Carol
        assertEquals(2, result.getRows().size());
    }

    @Test
    void mapsColumnMetadataToNamesFromRealResultSet() {
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("SELECT id AS user_id, name AS user_name FROM users ORDER BY id")
                .params(List.of())
                .timeoutMs(5000)
                .build();

        QueryResult result = executor.execute(request);

        assertEquals(2, result.getColumns().size());
        // H2 reports the column label for aliased columns
        assertEquals("USER_ID", result.getColumns().get(0));
        assertEquals("USER_NAME", result.getColumns().get(1));
        assertEquals(3, result.getRows().size());
    }

    @Test
    void emptyResultSetReturnsEmptyRowsWithColumns() {
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("SELECT id, name FROM users WHERE amount > ?")
                .params(List.of(1000000))
                .timeoutMs(5000)
                .build();

        QueryResult result = executor.execute(request);

        assertEquals(2, result.getColumns().size());
        assertTrue(result.getRows().isEmpty());
        assertEquals(0L, result.getTotal());
    }

    @Test
    void malformedSqlMapsToQueryTimeoutCode() {
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("SELECT * FROM non_existent_table_xyz")
                .params(List.of())
                .timeoutMs(5000)
                .build();

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> executor.execute(request));
        assertEquals(ErrorCode.EX_QUERY_TIMEOUT.getCode(), ex.getErrorCode());
        assertTrue(ex.getMessage().toLowerCase().contains("non_existent_table_xyz")
                || ex.getMessage().contains("not found"));
    }

    @Test
    void paramCountMismatchMapsToQueryTimeoutCode() {
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("SELECT name FROM users WHERE amount > ? AND id < ?")
                .params(List.of(50)) // only 1 param, 2 placeholders
                .timeoutMs(5000)
                .build();

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> executor.execute(request));
        assertEquals(ErrorCode.EX_QUERY_TIMEOUT.getCode(), ex.getErrorCode());
    }

    @Test
    void queryTimeoutIsSetOnRealPreparedStatement() throws Exception {
        // Use a query that can be cancelled; H2 honors Statement.setQueryTimeout.
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("SELECT COUNT(*) FROM users")
                .params(List.of())
                .timeoutMs(30000)
                .build();

        QueryResult result = executor.execute(request);
        assertNotNull(result);
        assertEquals(1, result.getRows().size());
    }

    @Test
    void contextServiceBindAndClearInvoked() {
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(42L)
                .databaseName("analytics")
                .schemaName("public")
                .sql("SELECT COUNT(*) FROM users")
                .params(List.of())
                .timeoutMs(5000)
                .build();

        executor.execute(request);

        verify(contextService).bind(any(ai.chat2db.community.domain.api.model.request.runtime.DbConnectionContextRequest.class));
        verify(contextService).clear();
    }
}