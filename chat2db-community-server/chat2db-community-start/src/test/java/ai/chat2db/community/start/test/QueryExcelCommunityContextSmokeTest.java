package ai.chat2db.community.start.test;

import ai.chat2db.community.domain.api.service.dashboard.IChartSavedQueryViewAdapter;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.enums.FieldRole;
import ai.chat2db.community.query.excel.domain.api.enums.QueryDatasetStatus;
import ai.chat2db.community.query.excel.domain.api.enums.SavedQueryViewStatus;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.ViewDimension;
import ai.chat2db.community.query.excel.domain.api.model.ViewMeasure;
import ai.chat2db.community.query.excel.domain.api.service.IExcelExportService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelReportTemplateService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelRenderService;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import ai.chat2db.community.query.excel.storage.QueryDatasetStorage;
import ai.chat2db.community.query.excel.storage.SavedQueryViewStorage;
import ai.chat2db.community.query.excel.web.api.controller.ExcelExportController;
import ai.chat2db.community.query.excel.web.api.controller.ExcelReportTemplateController;
import ai.chat2db.community.query.excel.web.api.controller.QueryDatasetController;
import ai.chat2db.community.query.excel.web.api.controller.SavedQueryViewController;
import ai.chat2db.community.storage.LocalDashboardService;
import ai.chat2db.community.start.Application;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real production Spring context smoke test for the query-excel wiring.
 * <p>Boots the full {@link Application} configuration (component scan over
 * {@code ai.chat2db.community}) on a throwaway user.home with an inline
 * encryption key — no {@link ISavedQueryViewService} proxy or other manual
 * registration. Asserts the query-excel services/controllers and
 * {@link LocalDashboardService} are resolvable from the real context.</p>
 */
@SpringBootTest(classes = {Application.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueryExcelCommunityContextSmokeTest {

    @BeforeAll
    static void isolateUserHome() {
        try {
            Path home = Files.createTempDirectory("c2d-query-excel-smoke");
            System.setProperty("user.home", home.toString());
            System.setProperty("chat2db.runtime.mode", "community");
            System.setProperty("chat2db.network.status", "OFFLINE");
            System.setProperty("chat2db.community.encryption-key",
                    Base64.getEncoder().encodeToString(
                            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException("unable to isolate user.home", e);
        }
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void queryExcelServicesAreBeans() {
        assertNotNull(context.getBean(IQueryDatasetService.class));
        assertNotNull(context.getBean(ISavedQueryViewService.class));
        assertNotNull(context.getBean(IExcelReportTemplateService.class));
        assertNotNull(context.getBean(IExcelRenderService.class));
        assertNotNull(context.getBean(IExcelExportService.class));
    }

    @Test
    void queryExcelControllersAreBeans() {
        assertNotNull(context.getBean(QueryDatasetController.class));
        assertNotNull(context.getBean(SavedQueryViewController.class));
        assertNotNull(context.getBean(ExcelReportTemplateController.class));
        assertNotNull(context.getBean(ExcelExportController.class));
    }

    @Test
    void localDashboardServiceResolvesSavedQueryViewDependency() {
        LocalDashboardService localDashboardService = context.getBean(LocalDashboardService.class);
        assertNotNull(localDashboardService);
    }

    /**
     * Runtime regression for the chart → saved-query-view → JDBC executor chain:
     * the chart adapter's {@code ISavedQueryViewService} must resolve to a
     * {@code SavedQueryViewServiceImpl} whose {@code Chat2DBSqlExecutor} field is
     * wired to the real {@code JdbcChat2DBSqlExecutor} bean by Spring (no
     * reflection needed). A {@code SAVED_QUERY_VIEW} chart execution against a
     * non-existent datasource must therefore surface a mapped
     * {@link QueryExcelException} — not the {@link UnsupportedOperationException}
     * thrown when the executor field is left {@code null}.
     */
    @Test
    void chartExecution_withMissingDatasource_mapsToQueryExcelException() {
        // Given a published dataset referencing a datasource that cannot exist
        // in the isolated user.home, and a published view referencing it
        QueryDataset dataset = new QueryDataset();
        dataset.setName("chart-it-ds");
        dataset.setDatasourceId(9_000_001L);
        dataset.setDatabaseName("missing_db");
        dataset.setSchemaName("public");
        dataset.setTableName("missing_table");
        dataset.setStatus(QueryDatasetStatus.PUBLISHED.name());
        dataset.setVersion(1);
        dataset.setFields(List.of(field("f1", "amount", FieldRole.MEASURE.name())));
        Long datasetId = QueryDatasetStorage.INSTANCE.save(dataset);

        SavedQueryView view = new SavedQueryView();
        view.setName("chart-it-view");
        view.setDatasetId(datasetId);
        view.setDatasetVersion(1);
        ViewMeasure measure = new ViewMeasure();
        measure.setFieldId("f1");
        measure.setAggregation("SUM");
        view.setMeasures(List.of(measure));
        view.setStatus(SavedQueryViewStatus.PUBLISHED.name());
        view.setVersion(1);
        Long viewId = SavedQueryViewStorage.INSTANCE.save(view);

        // When executing the chart through the real Spring-wired adapter chain
        IChartSavedQueryViewAdapter adapter = context.getBean(IChartSavedQueryViewAdapter.class);
        QueryExcelException ex = assertThrows(QueryExcelException.class, () -> adapter.executeQuery(viewId));

        // Then the failure is a mapped connection/query error, not a null-executor
        // UnsupportedOperationException, and the executor reached the JDBC path
        assertTrue(ErrorCode.DS_CONNECTION_FAILED.getCode().equals(ex.getErrorCode())
                        || ErrorCode.EX_QUERY_TIMEOUT.getCode().equals(ex.getErrorCode()),
                "expected mapped connection/query error but got " + ex.getErrorCode() + ": " + ex.getMessage());
    }

    private static QueryDatasetField field(String fieldId, String sourceColumn, String role) {
        QueryDatasetField field = new QueryDatasetField();
        field.setFieldId(fieldId);
        field.setSourceColumn(sourceColumn);
        field.setRole(role);
        field.setDataType("BIGINT");
        field.setAggregation("SUM");
        field.setFilterable(false);
        field.setSortable(true);
        return field;
    }
}