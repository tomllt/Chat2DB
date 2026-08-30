package ai.chat2db.community.storage;

import ai.chat2db.community.domain.api.enums.operation.SqlOperationLogSourceEnum;
import ai.chat2db.community.domain.api.enums.ChartDataSourceType;
import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.domain.api.model.chart.Chart;
import ai.chat2db.community.domain.api.model.chart.Dashboard;
import ai.chat2db.community.domain.api.model.request.db.DbDlExecuteRequest;
import ai.chat2db.community.domain.api.model.request.operation.OpsSqlOperationLogListResultRequest;
import ai.chat2db.community.domain.api.model.request.runtime.DbConnectionContextRequest;
import ai.chat2db.community.domain.api.model.result.ExecuteResponse;
import ai.chat2db.community.domain.api.model.result.Header;
import ai.chat2db.community.domain.api.model.result.ResultCell;
import ai.chat2db.community.domain.api.service.dashboard.IChartSavedQueryViewAdapter;
import ai.chat2db.community.domain.api.service.dashboard.IDashboardService;
import ai.chat2db.community.domain.api.service.db.IDbConnectionContextService;
import ai.chat2db.community.domain.api.service.db.IDbDlTemplateService;
import ai.chat2db.community.domain.api.service.ops.IOpsSqlOperationLogService;
import ai.chat2db.community.storage.small.ChartStorage;
import ai.chat2db.community.storage.small.DashboardStorage;
import ai.chat2db.community.tools.annotation.LocalPersistenceRuntimeOnly;
import ai.chat2db.community.tools.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@LocalPersistenceRuntimeOnly
public class LocalDashboardService implements IDashboardService {

    private static final int DEFAULT_CHART_REFRESH_PAGE_SIZE = 200;

    private final IDbConnectionContextService connectionContextService;
    private final IDbDlTemplateService dlTemplateService;
    private final IOpsSqlOperationLogService sqlOperationLogService;
    private final IChartSavedQueryViewAdapter savedQueryViewAdapter;

    public LocalDashboardService(IDbConnectionContextService connectionContextService,
            IDbDlTemplateService dlTemplateService,
            IOpsSqlOperationLogService sqlOperationLogService,
            IChartSavedQueryViewAdapter savedQueryViewAdapter) {
        this.connectionContextService = connectionContextService;
        this.dlTemplateService = dlTemplateService;
        this.sqlOperationLogService = sqlOperationLogService;
        this.savedQueryViewAdapter = savedQueryViewAdapter;
    }

    @Override
    public PageResponse<Dashboard> listDashboards(Integer pageNo, Integer pageSize, String searchKey) {
        int normalizedPageNo = Math.max(1, pageNo == null ? 1 : pageNo);
        int normalizedPageSize = Math.max(1, pageSize == null ? 20 : pageSize);
        List<Dashboard> dashboards = filterDashboards(DashboardStorage.INSTANCE.getDataList(), searchKey);
        int fromIndex = Math.min((normalizedPageNo - 1) * normalizedPageSize, dashboards.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, dashboards.size());
        return PageResponse.of(dashboards.subList(fromIndex, toIndex), (long) dashboards.size(),
                normalizedPageNo, normalizedPageSize);
    }

    @Override
    public Dashboard getDashboard(Long id) {
        return DashboardStorage.INSTANCE.getById(id);
    }

    @Override
    public Long createDashboard(Dashboard dashboard) {
        Date now = new Date();
        dashboard.setGmtCreate(now);
        dashboard.setGmtModified(now);
        if (dashboard.getChartIds() == null) {
            dashboard.setChartIds(new ArrayList<>());
        }
        return DashboardStorage.INSTANCE.save(dashboard);
    }

    @Override
    public void updateDashboard(Dashboard dashboard) {
        dashboard.setGmtModified(new Date());
        DashboardStorage.INSTANCE.update(dashboard);
    }

    @Override
    public void deleteDashboard(Long id) {
        Dashboard dashboard = DashboardStorage.INSTANCE.getById(id);
        if (dashboard != null && dashboard.getChartIds() != null) {
            dashboard.getChartIds().forEach(ChartStorage.INSTANCE::delete);
        }
        DashboardStorage.INSTANCE.delete(id);
    }

    @Override
    public Chart getChart(Long id) {
        return ChartStorage.INSTANCE.getById(id);
    }

    @Override
    public Chart getChartDetail(Long chartId, Boolean refresh) {
        Chart chart = ChartStorage.INSTANCE.getById(chartId);
        if (chart == null) {
            return null;
        }
        Chart response = JSON.parseObject(JSON.toJSONString(chart), Chart.class);

        String dataSourceType = chart.getDataSourceType();
        if (ChartDataSourceType.SAVED_QUERY_VIEW.name().equals(dataSourceType)) {
            // Mutual exclusion: cannot have both SAVED_QUERY_VIEW and LEGACY_SQL sources (§7.2)
            validateChartSource(chart);
            executeChartViaSavedQueryView(chart, response);
        } else {
            // LEGACY_SQL or unset — existing execution path
            if (Boolean.TRUE.equals(refresh)) {
                refreshChartMetaData(response);
            }
        }
        return response;
    }

    @Override
    public Long createChart(Chart chart) {
        validateChartSource(chart);
        Date now = new Date();
        chart.setGmtCreate(now);
        chart.setGmtModified(now);
        if (StringUtils.isBlank(chart.getName())) {
            chart.setName(resolveChartTitle(chart));
        }
        return ChartStorage.INSTANCE.save(chart);
    }

    @Override
    public void updateChart(Chart chart) {
        validateChartSource(chart);
        chart.setGmtModified(new Date());
        if (StringUtils.isBlank(chart.getName())) {
            chart.setName(resolveChartTitle(chart));
        }
        ChartStorage.INSTANCE.update(chart);
    }

    @Override
    public void deleteChart(Long id) {
        ChartStorage.INSTANCE.delete(id);
    }

    /**
     * §7.2 mutual exclusion: SAVED_QUERY_VIEW charts must not also carry a ddl.
     */
    private static void validateChartSource(Chart chart) {
        if (chart == null) {
            return;
        }
        String dataSourceType = chart.getDataSourceType();
        if (ChartDataSourceType.SAVED_QUERY_VIEW.name().equals(dataSourceType)) {
            if (chart.getSavedQueryViewId() != null && StringUtils.isNotBlank(chart.getDdl())) {
                throw new BusinessException("common.businessError",
                        new Object[]{"Chart cannot have both LEGACY_SQL and SAVED_QUERY_VIEW sources"});
            }
        }
    }

    private List<Dashboard> filterDashboards(List<Dashboard> dashboards, String searchKey) {
        if (StringUtils.isBlank(searchKey)) {
            return dashboards;
        }
        String normalizedSearchKey = searchKey.toLowerCase(Locale.ROOT);
        return dashboards.stream()
                .filter(dashboard -> contains(dashboard.getName(), normalizedSearchKey)
                        || contains(dashboard.getDescription(), normalizedSearchKey))
                .toList();
    }

    private boolean contains(String text, String normalizedSearchKey) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(normalizedSearchKey);
    }

    private void refreshChartMetaData(Chart chart) {
        JSONObject databaseInfo = toJsonObject(chart.getDatabaseInfo());
        Long dataSourceId = longValue(databaseInfo, "dataSourceId");
        String sql = databaseInfo.getString("sql");
        if (dataSourceId == null || StringUtils.isBlank(sql)) {
            return;
        }

        Long consoleId = Objects.requireNonNullElse(longValue(databaseInfo, "consoleId"), System.currentTimeMillis());
        String databaseName = databaseInfo.getString("databaseName");
        String schemaName = databaseInfo.getString("schemaName");

        DbConnectionContextRequest contextRequest = new DbConnectionContextRequest();
        contextRequest.setDataSourceId(dataSourceId);
        contextRequest.setConsoleId(consoleId);
        contextRequest.setDatabaseName(databaseName);
        contextRequest.setSchemaName(schemaName);

        DbDlExecuteRequest executeRequest = new DbDlExecuteRequest();
        executeRequest.setSql(sql);
        executeRequest.setDataSourceId(dataSourceId);
        executeRequest.setConsoleId(consoleId);
        executeRequest.setDatabaseName(databaseName);
        executeRequest.setSchemaName(schemaName);
        executeRequest.setPageNo(1);
        executeRequest.setPageSize(DEFAULT_CHART_REFRESH_PAGE_SIZE);
        executeRequest.setPageSizeAll(false);
        executeRequest.setSingle(true);
        executeRequest.setErrorContinue(false);

        try {
            connectionContextService.bind(contextRequest);
            List<ExecuteResponse> results = dlTemplateService.execute(executeRequest);
            sqlOperationLogService.recordListResultAsync(OpsSqlOperationLogListResultRequest.of(
                    sql, executeSuccess(results), executeErrorMessage(results), results,
                    SqlOperationLogSourceEnum.CHART.name()));
            attachMetaData(chart, results);
        } catch (RuntimeException e) {
            sqlOperationLogService.recordFailureAsync(sql, SqlOperationLogSourceEnum.CHART.name(), e.getMessage());
            throw e;
        } finally {
            connectionContextService.clear();
        }
    }

    /**
     * Executes a chart whose data source is a saved query view and attaches the
     * result as metadata on the response chart.
     */
    private void executeChartViaSavedQueryView(Chart chart, Chart response) {
        Long viewId = chart.getSavedQueryViewId();
        if (viewId == null) {
            return;
        }
        IChartSavedQueryViewAdapter.ChartQueryResult queryResult = savedQueryViewAdapter.executeQuery(viewId);
        if (queryResult == null) {
            return;
        }
        Map<String, Object> metaData = new LinkedHashMap<>();
        if (queryResult.columns() != null) {
            List<Header> headerList = queryResult.columns().stream()
                    .map(name -> {
                        Header h = new Header();
                        h.setName(name);
                        return h;
                    })
                    .collect(Collectors.toList());
            metaData.put("headerList", headerList);
        }
        if (queryResult.rows() != null) {
            metaData.put("dataList", queryResult.rows());
        }
        response.setMetaData(metaData);
    }

    private void attachMetaData(Chart chart, List<ExecuteResponse> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        ExecuteResponse firstResult = results.get(0);
        if (firstResult == null) {
            return;
        }
        if (!Boolean.TRUE.equals(firstResult.getSuccess())) {
            throw new BusinessException(firstResult.getMessage());
        }
        Map<String, Object> metaData = new LinkedHashMap<>();
        metaData.put("dataList", firstResult.getDisplayDataList());
        metaData.put("headerList", firstResult.getHeaderList());
        chart.setMetaData(metaData);
    }

    private Boolean executeSuccess(List<ExecuteResponse> results) {
        return results != null && results.stream()
                .allMatch(result -> result != null && Boolean.TRUE.equals(result.getSuccess()));
    }

    private String executeErrorMessage(List<ExecuteResponse> results) {
        if (results == null) {
            return null;
        }
        return results.stream()
                .filter(result -> result != null && !Boolean.TRUE.equals(result.getSuccess()))
                .map(ExecuteResponse::getMessage)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private String resolveChartTitle(Chart chart) {
        JSONObject chartSchema = toJsonObject(chart.getChartSchema());
        String title = chartSchema.getString("title");
        if (StringUtils.isBlank(title)) {
            title = chartSchema.getString("summary");
        }
        return title;
    }

    private JSONObject toJsonObject(Object value) {
        if (value == null) {
            return new JSONObject();
        }
        if (value instanceof JSONObject jsonObject) {
            return jsonObject;
        }
        return JSON.parseObject(JSON.toJSONString(value));
    }

    private Long longValue(JSONObject jsonObject, String key) {
        Object value = jsonObject.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
