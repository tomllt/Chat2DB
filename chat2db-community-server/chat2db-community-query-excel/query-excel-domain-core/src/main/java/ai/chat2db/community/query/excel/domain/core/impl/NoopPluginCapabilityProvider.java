package ai.chat2db.community.query.excel.domain.core.impl;

import ai.chat2db.community.query.excel.domain.api.service.PluginCapabilityProvider;
import org.springframework.stereotype.Component;

/**
 * Default no-op {@link PluginCapabilityProvider}: every capability is reported
 * as supported. The real implementation binding to Chat2DB's
 * {@code SupportedDatabaseSummary} lands in the API integration wave (T19).
 */
@Component
public class NoopPluginCapabilityProvider implements PluginCapabilityProvider {

    @Override
    public boolean supportsAggregation(Long datasourceId, String aggregationType) {
        return true;
    }

    @Override
    public boolean supportsPagination(Long datasourceId) {
        return true;
    }

    @Override
    public boolean supportsDateOperators(Long datasourceId) {
        return true;
    }

    @Override
    public boolean supportsIdentifierQuoting(Long datasourceId) {
        return true;
    }
}