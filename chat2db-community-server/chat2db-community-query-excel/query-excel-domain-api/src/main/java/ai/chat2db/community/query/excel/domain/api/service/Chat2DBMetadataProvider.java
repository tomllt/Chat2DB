package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.ColumnInfo;

/**
 * Abstraction over Chat2DB's datasource metadata layer (requirements §5.6).
 * <p>Decouples the validation service from the actual data-source connection code,
 * making tests fast and the integration point explicit (wired in the API layer, T19).</p>
 */
public interface Chat2DBMetadataProvider {

    /**
     * Returns the column metadata for the given table, or an empty list when the
     * table does not exist (or the connection is broken).
     */
    List<ColumnInfo> getTableColumns(Long datasourceId, String databaseName, String schemaName, String tableName);

    /**
     * Returns {@code true} when the datasource connection is healthy.
     */
    boolean testConnection(Long datasourceId);
}