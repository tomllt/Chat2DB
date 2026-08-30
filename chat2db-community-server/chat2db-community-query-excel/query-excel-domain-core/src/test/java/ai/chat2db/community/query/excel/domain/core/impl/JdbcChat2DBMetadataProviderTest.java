package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import ai.chat2db.community.domain.api.service.db.IDbConnectionContextService;
import ai.chat2db.community.domain.api.service.db.IDbDataSourceService;
import ai.chat2db.community.query.excel.domain.api.model.ColumnInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * H2-based integration test for {@link JdbcChat2DBMetadataProvider}.
 * <p>Proves that {@link #testConnection(Long)} returns true for a live H2
 * connection, false for a null/unknown datasource, and that
 * {@link #getTableColumns(Long, String, String, String)} returns real column
 * metadata from the embedded database.</p>
 */
class JdbcChat2DBMetadataProviderTest {

    private static Connection h2Connection;
    private IDbConnectionContextService contextService;
    private IDbDataSourceService dataSourceService;
    private JdbcChat2DBMetadataProvider provider;
    private MockedStatic<Chat2DBContext> contextStatic;

    @BeforeAll
    static void initH2() throws Exception {
        h2Connection = DriverManager.getConnection("jdbc:h2:mem:metadata_it;DB_CLOSE_DELAY=-1");
        try (Statement stmt = h2Connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS products (" +
                    "id INT PRIMARY KEY, name VARCHAR(200), price DECIMAL(12,2), created_at TIMESTAMP)");
            stmt.execute("DELETE FROM products");
            stmt.execute("INSERT INTO products VALUES (1, 'Widget', 29.99, '2024-01-15')");
            stmt.execute("INSERT INTO products VALUES (2, 'Gadget', 49.99, '2024-02-20')");
        }
    }

    @AfterAll
    static void closeH2() throws Exception {
        if (h2Connection != null && !h2Connection.isClosed()) {
            h2Connection.close();
        }
    }

    @BeforeEach
    void setUp() {
        contextService = mock(IDbConnectionContextService.class);
        dataSourceService = mock(IDbDataSourceService.class);
        provider = new JdbcChat2DBMetadataProvider(contextService, dataSourceService);
        contextStatic = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(h2Connection);
    }

    @AfterEach
    void tearDown() {
        if (contextStatic != null) {
            contextStatic.close();
        }
    }

    @Test
    void testConnection_returnsTrue_forLiveConnection() {
        boolean result = provider.testConnection(1L);
        assertTrue(result, "Connection should be valid against live H2");
    }

    @Test
    void testConnection_returnsFalse_forNullDatasourceId() {
        assertFalse(provider.testConnection(null));
    }

    @Test
    void testConnection_returnsFalse_whenConnectionIsNull() {
        contextStatic.close();
        contextStatic = mockStatic(Chat2DBContext.class);
        when(Chat2DBContext.getConnection()).thenReturn(null);

        assertFalse(provider.testConnection(1L));
    }

    @Test
    void testConnection_invokesBindAndClear() {
        provider.testConnection(42L);
        verify(contextService).bind(any());
        verify(contextService).clear();
    }

    @Test
    void getTableColumns_returnsColumns_forExistingTable() {
        List<ColumnInfo> columns = provider.getTableColumns(1L, null, null, "PRODUCTS");

        // H2 metadata: ID, NAME, PRICE, CREATED_AT
        assertFalse(columns.isEmpty(), "Should find columns for PRODUCTS table");
        assertEquals(4, columns.size(), "PRODUCTS should have 4 columns");

        ColumnInfo idCol = columns.stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase("ID"))
                .findFirst()
                .orElseThrow();
        assertFalse(idCol.getNullable(), "Primary key ID should not be nullable");

        ColumnInfo nameCol = columns.stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase("NAME"))
                .findFirst()
                .orElseThrow();
        assertTrue(nameCol.getColumnName().equalsIgnoreCase("NAME"));
    }

    @Test
    void getTableColumns_returnsEmpty_forNonExistentTable() {
        List<ColumnInfo> columns = provider.getTableColumns(1L, null, null, "NON_EXISTENT");
        assertTrue(columns.isEmpty(), "Non-existent table should return empty list");
    }

    @Test
    void getTableColumns_returnsEmpty_forNullDatasourceId() {
        List<ColumnInfo> columns = provider.getTableColumns(null, null, null, "products");
        assertTrue(columns.isEmpty());
    }

    @Test
    void getTableColumns_returnsEmpty_forBlankTableName() {
        List<ColumnInfo> columns = provider.getTableColumns(1L, null, null, "");
        assertTrue(columns.isEmpty());
    }

    @Test
    void getTableColumns_returnsEmpty_forNullTableName() {
        List<ColumnInfo> columns = provider.getTableColumns(1L, null, null, null);
        assertTrue(columns.isEmpty());
    }

    @Test
    void getTableColumns_invokesBindAndClear() {
        provider.getTableColumns(42L, "test_db", "public", "products");
        verify(contextService).bind(any());
        verify(contextService).clear();
    }
}