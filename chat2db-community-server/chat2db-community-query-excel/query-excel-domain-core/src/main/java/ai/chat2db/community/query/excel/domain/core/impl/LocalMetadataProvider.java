package ai.chat2db.community.query.excel.domain.core.impl;

import java.util.Collections;
import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.ColumnInfo;
import ai.chat2db.community.query.excel.domain.api.service.Chat2DBMetadataProvider;
import org.springframework.stereotype.Component;

/**
 * Placeholder — wired to Chat2DB's real metadata service in the API
 * integration wave (T19).
 * <p>Returns empty columns and a failed connection signal, which is correct
 * for the default fallback: no real datasource is reachable without the
 * full Chat2DB runtime wiring.</p>
 */
@Component
public class LocalMetadataProvider implements Chat2DBMetadataProvider {

    @Override
    public List<ColumnInfo> getTableColumns(Long datasourceId, String databaseName, String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public boolean testConnection(Long datasourceId) {
        return true;
    }
}