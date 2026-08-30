package ai.chat2db.community.domain.api.service.dashboard;

import java.util.List;

/**
 * Chart data-source adapter for the {@code SAVED_QUERY_VIEW} source type.
 * <p>
 * Keeps chart persistence (storage) decoupled from the query-excel domain API:
 * {@link IDashboardService} implementations depend on this neutral seam,
 * while the concrete query-excel adapter lives in query-excel-domain-core and
 * translates between this contract and {@code ISavedQueryViewService}.
 *
 * @see ai.chat2db.community.domain.api.enums.ChartDataSourceType#SAVED_QUERY_VIEW
 */
public interface IChartSavedQueryViewAdapter {

    /**
     * Executes the saved query view identified by {@code viewId} and returns
     * its full result set as column names plus rows of string cell values.
     *
     * @param viewId saved query view id; must not be {@code null}
     * @return executed result, or {@code null} when the view does not exist
     */
    ChartQueryResult executeQuery(Long viewId);

    /**
     * Result of a saved query view execution, decoupled from any query-excel
     * model so chart persistence stays on the shared domain API.
     */
    record ChartQueryResult(List<String> columns, List<List<String>> rows) {
    }
}