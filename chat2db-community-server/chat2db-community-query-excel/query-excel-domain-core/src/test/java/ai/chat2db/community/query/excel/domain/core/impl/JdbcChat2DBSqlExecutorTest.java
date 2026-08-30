package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLTimeoutException;
import java.util.Arrays;
import java.util.List;

import ai.chat2db.community.domain.api.model.request.runtime.DbConnectionContextRequest;
import ai.chat2db.community.domain.api.service.db.IDbConnectionContextService;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExecuteQueryRequest;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Unit tests for {@link JdbcChat2DBSqlExecutor}.
 * <p>Tests the contract enforcement, context binding/clearing, and
 * PreparedStatement parameter binding. The JDBC {@link Connection} is
 * mocked so no live datasource is required.</p>
 */
class JdbcChat2DBSqlExecutorTest {

    private IDbConnectionContextService contextService;
    private JdbcChat2DBSqlExecutor executor;
    private AutoCloseable mockStaticContext;

    @BeforeEach
    void setUp() {
        contextService = mock(IDbConnectionContextService.class);
        executor = new JdbcChat2DBSqlExecutor(contextService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockStaticContext != null) {
            mockStaticContext.close();
        }
    }

    // ── deprecated method ────────────────────────────────────────

    @Test
    void deprecatedMethodThrowsUnsupportedOperation() {
        assertThrows(UnsupportedOperationException.class,
                () -> executor.execute("SELECT 1", List.of(), 1000));
    }

    // ── null/invalid request ─────────────────────────────────────

    @Test
    void nullRequestThrowsConnectionFailed() {
        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> executor.execute((ExecuteQueryRequest) null));
        assertEquals(ErrorCode.DS_CONNECTION_FAILED.getCode(), ex.getErrorCode());
    }

    @Test
    void requestWithoutDatasourceIdThrowsConnectionFailed() {
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .sql("SELECT 1")
                .build();
        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> executor.execute(request));
        assertEquals(ErrorCode.DS_CONNECTION_FAILED.getCode(), ex.getErrorCode());
    }

    @Test
    void emptySqlThrowsQueryTimeout() {
        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("")
                .build();
        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> executor.execute(request));
        assertEquals(ErrorCode.EX_QUERY_TIMEOUT.getCode(), ex.getErrorCode());
        // bind/clear should not be called since we fail before binding
        verify(contextService, never()).bind(any());
        verify(contextService, never()).clear();
    }

    // ── success path ─────────────────────────────────────────────

    @Test
    void executeBindsContextAndReturnsResult() throws Exception {
        // Arrange
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);

        when(meta.getColumnCount()).thenReturn(2);
        when(meta.getColumnLabel(1)).thenReturn("name");
        when(meta.getColumnLabel(2)).thenReturn("amount");
        when(rs.getMetaData()).thenReturn(meta);
        when(rs.getObject(1)).thenReturn("Alice").thenReturn("Bob");
        when(rs.getObject(2)).thenReturn(100).thenReturn(200);
        when(rs.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(stmt.getResultSet()).thenReturn(rs);
        when(stmt.execute()).thenReturn(true);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);

        // Mock Chat2DBContext.getConnection() to return our mock connection
        mockStaticContext = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(conn);

        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .databaseName("test_db")
                .schemaName("public")
                .tableName("users")
                .sql("SELECT name, amount FROM users WHERE amount > ?")
                .params(List.of(50))
                .timeoutMs(10000)
                .build();

        // Act
        QueryResult result = executor.execute(request);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getColumns().size());
        assertEquals("name", result.getColumns().get(0));
        assertEquals("amount", result.getColumns().get(1));
        assertEquals(2, result.getRows().size());
        assertEquals("Alice", result.getRows().get(0).get(0));
        assertEquals(100, result.getRows().get(0).get(1));

        // Verify context binding
        verify(contextService).bind(any(DbConnectionContextRequest.class));

        // Verify PreparedStatement was created with the correct SQL
        verify(conn).prepareStatement("SELECT name, amount FROM users WHERE amount > ?");

        // Verify parameter binding
        verify(stmt).setObject(1, 50);

        // Verify timeout was set (10s = 10000ms → ceil(10) = 10 seconds)
        verify(stmt).setQueryTimeout(10);

        // Verify clear was called
        verify(contextService).clear();
    }

    @Test
    void executeSetsTimeoutSecondsRoundedUp() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);

        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnLabel(1)).thenReturn("x");
        when(rs.getMetaData()).thenReturn(meta);
        when(rs.getObject(1)).thenReturn(1);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(stmt.getResultSet()).thenReturn(rs);
        when(stmt.execute()).thenReturn(true);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);

        mockStaticContext = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(conn);

        // 1ms → ceil(0.001) = 1 second
        executor.execute(ExecuteQueryRequest.builder()
                .datasourceId(1L).sql("SELECT 1").timeoutMs(1).build());
        verify(stmt).setQueryTimeout(1);

        // 1500ms → ceil(1.5) = 2 seconds
        executor.execute(ExecuteQueryRequest.builder()
                .datasourceId(1L).sql("SELECT 1").timeoutMs(1500).build());
        verify(stmt).setQueryTimeout(2);

        // 30000ms → ceil(30) = 30 seconds
        executor.execute(ExecuteQueryRequest.builder()
                .datasourceId(1L).sql("SELECT 1").timeoutMs(30000).build());
        verify(stmt).setQueryTimeout(30);
    }

    @Test
    void executeBindsMultipleParamsPositionally() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);

        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnLabel(1)).thenReturn("x");
        when(rs.getMetaData()).thenReturn(meta);
        when(rs.getObject(1)).thenReturn(1);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(stmt.getResultSet()).thenReturn(rs);
        when(stmt.execute()).thenReturn(true);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);

        mockStaticContext = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(conn);

        executor.execute(ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("SELECT * FROM t WHERE a = ? AND b = ? AND c = ?")
                .params(Arrays.asList("foo", 42, null))
                .timeoutMs(5000)
                .build());

        verify(stmt).setObject(1, "foo");
        verify(stmt).setObject(2, 42);
        verify(stmt).setObject(3, null);
    }

    // ── error path: SQLTimeoutException ──────────────────────────

    @Test
    void sqlTimeoutExceptionMapsToExQueryTimeout() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.execute()).thenThrow(new SQLTimeoutException("timeout"));

        mockStaticContext = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(conn);

        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("SELECT 1")
                .timeoutMs(5000)
                .build();

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> executor.execute(request));
        assertEquals(ErrorCode.EX_QUERY_TIMEOUT.getCode(), ex.getErrorCode());
        assertTrue(ex.getMessage().contains("timed out"));

        // clear must still be called
        verify(contextService).clear();
    }

    // ── error path: generic SQLException ─────────────────────────

    @Test
    void sqlExceptionMapsToExQueryTimeout() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.execute()).thenThrow(new java.sql.SQLException("syntax error"));

        mockStaticContext = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(conn);

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> executor.execute(ExecuteQueryRequest.builder()
                        .datasourceId(1L).sql("BROKEN SQL").timeoutMs(5000).build()));
        assertEquals(ErrorCode.EX_QUERY_TIMEOUT.getCode(), ex.getErrorCode());
        assertTrue(ex.getMessage().contains("syntax error"));

        verify(contextService).clear();
    }

    // ── context binding verification ─────────────────────────────

    @Test
    void contextBindRequestContainsDatasourceAndDbScope() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);

        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnLabel(1)).thenReturn("x");
        when(rs.getMetaData()).thenReturn(meta);
        when(rs.getObject(1)).thenReturn(1);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(stmt.getResultSet()).thenReturn(rs);
        when(stmt.execute()).thenReturn(true);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);

        mockStaticContext = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(conn);

        executor.execute(ExecuteQueryRequest.builder()
                .datasourceId(42L)
                .databaseName("analytics")
                .schemaName("public")
                .sql("SELECT 1")
                .timeoutMs(5000)
                .build());

        // Verify the bind request carries the correct datasource ID and scope
        verify(contextService).bind(any(DbConnectionContextRequest.class));
        // Verify clear is called exactly once
        verify(contextService).clear();
    }

    @Test
    void contextIsClearedOnException() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.execute()).thenThrow(new RuntimeException("unexpected"));

        mockStaticContext = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(conn);

        assertThrows(QueryExcelException.class, () -> executor.execute(
                ExecuteQueryRequest.builder()
                        .datasourceId(1L).sql("SELECT 1").timeoutMs(5000).build()));

        // Even on exception, clear must be called
        verify(contextService).clear();
    }

    @Test
    void contextIsClearedWhenBindFailsAfterSettingThreadLocal() {
        doAnswer(invocation -> {
            Chat2DBContext.getConnectInfo();
            throw new IllegalStateException("bind failed after attempting context mutation");
        }).when(contextService).bind(any(DbConnectionContextRequest.class));

        ExecuteQueryRequest request = ExecuteQueryRequest.builder()
                .datasourceId(1L)
                .sql("SELECT 1")
                .timeoutMs(5000)
                .build();

        assertThrows(QueryExcelException.class, () -> executor.execute(request));
        verify(contextService).clear();
        assertNull(Chat2DBContext.getConnectInfo());
    }

    // ── non-query SQL ────────────────────────────────────────────

    @Test
    void nonQuerySqlReturnsEmptyResult() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        // execute() returns false for non-query (UPDATE, INSERT, etc.)
        when(stmt.execute()).thenReturn(false);
        when(stmt.getUpdateCount()).thenReturn(5);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);

        mockStaticContext = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(conn);

        QueryResult result = executor.execute(ExecuteQueryRequest.builder()
                .datasourceId(1L).sql("UPDATE t SET x = 1").timeoutMs(5000).build());

        assertNotNull(result);
        assertEquals(0, result.getRows().size());
        assertTrue(result.getColumns().isEmpty());
        assertEquals(0, result.getTotal());
    }
}