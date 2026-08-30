package ai.chat2db.community.query.excel.domain.core.impl;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import ai.chat2db.community.domain.api.model.request.runtime.DbConnectionContextRequest;
import ai.chat2db.community.domain.api.service.db.IDbConnectionContextService;
import ai.chat2db.community.domain.api.service.db.IDbDataSourceService;
import ai.chat2db.community.query.excel.domain.api.model.ColumnInfo;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBMetadataProvider;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * JDBC-backed {@link Chat2DBMetadataProvider} that uses the Chat2DB connection
 * context infrastructure to query real datasource metadata (requirements §5.6).
 * <p>Wired as {@code @Primary @Component} so it overrides the default
 * {@link LocalMetadataProvider} in a real Community runtime. Uses
 * {@link IDbConnectionContextService} to bind the datasource context and
 * {@link IDbDataSourceService} for datasource lifecycle operations.</p>
 */
@Slf4j
@Primary
@Component
public class JdbcChat2DBMetadataProvider implements Chat2DBMetadataProvider {

    private final IDbConnectionContextService contextService;
    private final IDbDataSourceService dataSourceService;

    @Autowired
    public JdbcChat2DBMetadataProvider(IDbConnectionContextService contextService,
                                       IDbDataSourceService dataSourceService) {
        this.contextService = contextService;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public boolean testConnection(Long datasourceId) {
        if (datasourceId == null) {
            return false;
        }
        DbConnectionContextRequest ctx = new DbConnectionContextRequest();
        ctx.setDataSourceId(datasourceId);
        try {
            contextService.bind(ctx);
            Connection connection = Chat2DBContext.getConnection();
            if (connection == null) {
                return false;
            }
            return connection.isValid(5);
        } catch (Exception e) {
            log.warn("Connection test failed for datasource {}", datasourceId, e);
            return false;
        } finally {
            contextService.clear();
        }
    }

    @Override
    public List<ColumnInfo> getTableColumns(Long datasourceId, String databaseName,
                                            String schemaName, String tableName) {
        if (datasourceId == null || tableName == null || tableName.isBlank()) {
            return List.of();
        }
        DbConnectionContextRequest ctx = new DbConnectionContextRequest();
        ctx.setDataSourceId(datasourceId);
        ctx.setDatabaseName(databaseName);
        ctx.setSchemaName(schemaName);

        try {
            contextService.bind(ctx);
            Connection connection = Chat2DBContext.getConnection();
            if (connection == null) {
                return List.of();
            }

            DatabaseMetaData metaData = connection.getMetaData();

            // Try catalog = databaseName, schema = schemaName first (MySQL pattern)
            List<ColumnInfo> columns = fetchColumns(metaData, databaseName, schemaName, tableName);
            if (!columns.isEmpty()) {
                return columns;
            }

            // Try catalog = null, schema = databaseName (PostgreSQL pattern)
            columns = fetchColumns(metaData, null, databaseName, tableName);
            if (!columns.isEmpty()) {
                return columns;
            }

            // Try catalog = databaseName, schema = null (MySQL without schema)
            columns = fetchColumns(metaData, databaseName, null, tableName);
            if (!columns.isEmpty()) {
                return columns;
            }

            // Try catalog = null, schema = schemaName (some databases)
            columns = fetchColumns(metaData, null, schemaName, tableName);
            return columns;
        } catch (Exception e) {
            log.warn("Failed to get table columns for {}.{}.{} on datasource {}",
                    databaseName, schemaName, tableName, datasourceId, e);
            return List.of();
        } finally {
            contextService.clear();
        }
    }

    /**
     * Fetches columns from {@link DatabaseMetaData#getColumns(String, String, String, String)}
     * using the given catalog/schema pattern and maps the result set to {@link ColumnInfo} entries.
     */
    private static List<ColumnInfo> fetchColumns(DatabaseMetaData metaData, String catalog,
                                                  String schemaPattern, String tableName)
            throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(catalog, schemaPattern, tableName, "%")) {
            while (rs.next()) {
                ColumnInfo info = new ColumnInfo();
                info.setColumnName(rs.getString("COLUMN_NAME"));
                info.setDataType(rs.getString("TYPE_NAME"));
                info.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                columns.add(info);
            }
        }
        return columns;
    }
}