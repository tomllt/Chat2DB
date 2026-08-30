package ai.chat2db.community.query.excel.domain.core.adapter;

import ai.chat2db.community.domain.api.service.dashboard.IChartSavedQueryViewAdapter;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import org.springframework.stereotype.Component;

/**
 * Adapts {@link ISavedQueryViewService} to the chart dashboard seam
 * {@link IChartSavedQueryViewAdapter}. Lives in query-excel-domain-core so the
 * chart persistence layer never has to depend on the query-excel domain API.
 */
@Component
public class ChartSavedQueryViewAdapter implements IChartSavedQueryViewAdapter {

    private final ISavedQueryViewService savedQueryViewService;

    public ChartSavedQueryViewAdapter(ISavedQueryViewService savedQueryViewService) {
        this.savedQueryViewService = savedQueryViewService;
    }

    @Override
    public ChartQueryResult executeQuery(Long viewId) {
        if (viewId == null) {
            return null;
        }
        QueryResult queryResult = savedQueryViewService.executeQuery(viewId, null);
        if (queryResult == null) {
            return null;
        }
        return new ChartQueryResult(
                queryResult.getColumns(),
                queryResult.getRows() == null ? null : queryResult.getRows().stream()
                        .map(row -> row == null ? null
                                : row.stream().map(cell -> cell == null ? null : String.valueOf(cell)).toList())
                        .toList());
    }
}