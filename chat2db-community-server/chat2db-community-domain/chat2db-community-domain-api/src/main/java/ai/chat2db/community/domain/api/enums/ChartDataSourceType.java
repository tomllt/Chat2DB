package ai.chat2db.community.domain.api.enums;

/**
 * Data source type for a Chart.
 * <p>
 * {@link #LEGACY_SQL} — the chart executes its stored {@code ddl} SQL directly
 * (the existing behaviour).<br>
 * {@link #SAVED_QUERY_VIEW} — the chart executes a {@link
 * ai.chat2db.community.query.excel.domain.api.model.SavedQueryView} by its
 * {@code savedQueryViewId}.
 * <p>
 * The two sources are mutually exclusive; see §7.2.
 */
public enum ChartDataSourceType {

    LEGACY_SQL,
    SAVED_QUERY_VIEW,

    ;

}