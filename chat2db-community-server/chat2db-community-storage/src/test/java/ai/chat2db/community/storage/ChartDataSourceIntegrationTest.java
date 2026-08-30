package ai.chat2db.community.storage;

import ai.chat2db.community.domain.api.enums.ChartDataSourceType;
import ai.chat2db.community.domain.api.model.chart.Chart;
import ai.chat2db.community.domain.api.model.result.Header;
import ai.chat2db.community.domain.api.service.dashboard.IChartSavedQueryViewAdapter;
import ai.chat2db.community.domain.api.service.dashboard.IDashboardService;
import ai.chat2db.community.domain.api.service.db.IDbConnectionContextService;
import ai.chat2db.community.domain.api.service.db.IDbDlTemplateService;
import ai.chat2db.community.domain.api.service.ops.IOpsSqlOperationLogService;
import ai.chat2db.community.storage.small.ChartStorage;
import ai.chat2db.community.tools.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the chart data source routing introduced in §7.2-7.3.
 * <p>
 * Verifies that {@link LocalDashboardService#getChartDetail} dispatches to the
 * correct execution path ({@code LEGACY_SQL} / {@code SAVED_QUERY_VIEW}) and
 * enforces mutual exclusion.
 */
class ChartDataSourceIntegrationTest {

    private IChartSavedQueryViewAdapter savedQueryViewAdapter;
    private IDashboardService dashboardService;

    @BeforeEach
    void setUp() {
        savedQueryViewAdapter = new IChartSavedQueryViewAdapter() {
            @Override
            public ChartQueryResult executeQuery(Long viewId) {
                if (viewId == null) return null;
                return new ChartQueryResult(List.of("col1", "col2"), List.of(List.of("a", "b"), List.of("c", "d")));
            }
        };

        dashboardService = new LocalDashboardService(
                proxy(IDbConnectionContextService.class),
                proxy(IDbDlTemplateService.class),
                proxy(IOpsSqlOperationLogService.class),
                savedQueryViewAdapter);
    }

    @Test
    void savedQueryViewType_executesViewQuery() {
        // Given a chart with SAVED_QUERY_VIEW data source type
        Long chartId = createSavedChart("svq-chart", ChartDataSourceType.SAVED_QUERY_VIEW.name(),
                42L, null, null);

        // When retrieving chart detail
        Chart result = dashboardService.getChartDetail(chartId, false);

        // Then metadata should contain the view query result
        assertNotNull(result);
        assertNotNull(result.getMetaData());
        @SuppressWarnings("unchecked")
        Map<String, Object> metaData = (Map<String, Object>) result.getMetaData();
        assertTrue(metaData.containsKey("headerList"));
        assertTrue(metaData.containsKey("dataList"));
        assertEquals(2, ((List<?>) metaData.get("dataList")).size());
    }

    @Test
    void legacySqlType_executesOldSqlPath() {
        // Given a chart with LEGACY_SQL type (ddl set, savedQueryViewId unset)
        Long chartId = createSavedChart("legacy-chart", ChartDataSourceType.LEGACY_SQL.name(),
                null, "SELECT 1", null);

        // When retrieving chart detail (refresh=false → no execution)
        Chart result = dashboardService.getChartDetail(chartId, false);

        // Then metadata is not set (refresh=false, so no execution occurs)
        assertNotNull(result);
        assertNull(result.getMetaData());
    }

    @Test
    void mutualExclusion_throwsWhenBothSourcesSet() {
        // Given a chart with both savedQueryViewId AND ddl set
        Long chartId = createSavedChart("conflict-chart", ChartDataSourceType.SAVED_QUERY_VIEW.name(),
                99L, "SELECT 1", null);

        // When/Then retrieving chart detail throws BusinessException
        BusinessException ex = assertThrows(BusinessException.class,
                () -> dashboardService.getChartDetail(chartId, false));
        assertEquals("common.businessError", ex.getCode());
        assertNotNull(ex.getArgs());
        assertTrue(ex.getArgs()[0].toString().contains("cannot have both"));
    }

    @Test
    void noDataSourceType_fallsBackToLegacySql() {
        // Given a chart with null dataSourceType (legacy default)
        Long chartId = createSavedChart("legacy-fallback", null,
                null, "SELECT 1", null);

        // When retrieving chart detail (refresh=false → no execution)
        Chart result = dashboardService.getChartDetail(chartId, false);

        // Then no error — falls back to LEGACY_SQL path
        assertNotNull(result);
        assertNull(result.getMetaData());
    }

    @Test
    void createChart_legacySqlWithDdl_persists() {
        // Given a LEGACY_SQL chart with ddl and no savedQueryViewId
        Chart chart = new Chart();
        chart.setName("legacy-create");
        chart.setDataSourceType(ChartDataSourceType.LEGACY_SQL.name());
        chart.setDdl("SELECT 1 FROM dual");

        // When creating
        Long chartId = dashboardService.createChart(chart);

        // Then the chart is persisted and retrievable via the legacy path
        Chart persisted = ChartStorage.INSTANCE.getById(chartId);
        assertNotNull(persisted);
        assertEquals("SELECT 1 FROM dual", persisted.getDdl());

        Chart detail = dashboardService.getChartDetail(chartId, false);
        assertNotNull(detail);
        assertNull(detail.getMetaData());
    }

    @Test
    void createChart_unsetSourceTypeWithDdl_persists() {
        // Given a chart with unset dataSourceType (legacy default) and ddl
        Chart chart = new Chart();
        chart.setName("unset-create");
        chart.setDdl("SELECT * FROM t");

        // When creating
        Long chartId = dashboardService.createChart(chart);

        // Then the chart is persisted with its ddl intact
        Chart persisted = ChartStorage.INSTANCE.getById(chartId);
        assertNotNull(persisted);
        assertEquals("SELECT * FROM t", persisted.getDdl());
    }

    @Test
    void updateChart_legacySqlWithDdl_persists() {
        // Given an existing legacy chart
        Long chartId = createSavedChart("legacy-update", ChartDataSourceType.LEGACY_SQL.name(),
                null, "SELECT 1", null);

        // When updating with a new ddl (still LEGACY_SQL, no savedQueryViewId)
        Chart update = new Chart();
        update.setId(chartId);
        update.setDataSourceType(ChartDataSourceType.LEGACY_SQL.name());
        update.setDdl("SELECT 2 FROM dual");
        dashboardService.updateChart(update);

        // Then the updated ddl is persisted
        Chart persisted = ChartStorage.INSTANCE.getById(chartId);
        assertNotNull(persisted);
        assertEquals("SELECT 2 FROM dual", persisted.getDdl());
    }

    @Test
    void createChart_savedQueryViewWithDdl_throws() {
        // Given a SAVED_QUERY_VIEW chart with both savedQueryViewId and ddl
        Chart chart = new Chart();
        chart.setName("invalid-create");
        chart.setDataSourceType(ChartDataSourceType.SAVED_QUERY_VIEW.name());
        chart.setSavedQueryViewId(42L);
        chart.setDdl("SELECT 1");

        // When/Then creating rejects the invalid combination
        BusinessException ex = assertThrows(BusinessException.class,
                () -> dashboardService.createChart(chart));
        assertEquals("common.businessError", ex.getCode());
        assertNotNull(ex.getArgs());
        assertTrue(ex.getArgs()[0].toString().contains("cannot have both"));

        // Then nothing was persisted
        assertNull(ChartStorage.INSTANCE.getById(chart.getId()));
    }

    @Test
    void updateChart_savedQueryViewWithDdl_throws() {
        // Given an existing chart
        Long chartId = createSavedChart("invalid-update", ChartDataSourceType.LEGACY_SQL.name(),
                null, "SELECT 1", null);

        // When updating to a SAVED_QUERY_VIEW chart with both ids set
        Chart update = new Chart();
        update.setId(chartId);
        update.setDataSourceType(ChartDataSourceType.SAVED_QUERY_VIEW.name());
        update.setSavedQueryViewId(7L);
        update.setDdl("SELECT 1");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> dashboardService.updateChart(update));

        // Then the invalid combination is rejected
        assertEquals("common.businessError", ex.getCode());
        assertNotNull(ex.getArgs());
        assertTrue(ex.getArgs()[0].toString().contains("cannot have both"));
    }

    @Test
    void createChart_savedQueryViewWithoutDdl_persists() {
        // Given a valid SAVED_QUERY_VIEW chart (no ddl)
        Chart chart = new Chart();
        chart.setName("valid-sqv-create");
        chart.setDataSourceType(ChartDataSourceType.SAVED_QUERY_VIEW.name());
        chart.setSavedQueryViewId(10L);

        // When creating
        Long chartId = dashboardService.createChart(chart);

        // Then the chart is persisted
        Chart persisted = ChartStorage.INSTANCE.getById(chartId);
        assertNotNull(persisted);
        assertEquals(10L, persisted.getSavedQueryViewId());
    }

    @Test
    void savedQueryViewWithNullViewId_returnsWithoutExecution() {
        // Given a chart with SAVED_QUERY_VIEW type but null savedQueryViewId
        Long chartId = createSavedChart("no-view-id", ChartDataSourceType.SAVED_QUERY_VIEW.name(),
                null, null, null);

        // When retrieving chart detail
        Chart result = dashboardService.getChartDetail(chartId, false);

        // Then no execution, no metadata
        assertNotNull(result);
        assertNull(result.getMetaData());
    }

    // --- helpers ---

    private Long createSavedChart(String name, String dataSourceType,
                                   Long savedQueryViewId, String ddl, Long queryDatasetId) {
        Chart chart = new Chart();
        chart.setName(name);
        chart.setDataSourceType(dataSourceType);
        chart.setSavedQueryViewId(savedQueryViewId);
        chart.setDdl(ddl);
        chart.setQueryDatasetId(queryDatasetId);
        return ChartStorage.INSTANCE.save(chart);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });
    }
}