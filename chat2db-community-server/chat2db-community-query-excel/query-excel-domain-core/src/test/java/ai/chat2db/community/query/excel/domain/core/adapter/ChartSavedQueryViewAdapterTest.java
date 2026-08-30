package ai.chat2db.community.query.excel.domain.core.adapter;

import ai.chat2db.community.domain.api.service.dashboard.IChartSavedQueryViewAdapter.ChartQueryResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link ChartSavedQueryViewAdapter}.
 */
class ChartSavedQueryViewAdapterTest {

    private ISavedQueryViewService delegate;
    private ChartSavedQueryViewAdapter adapter;

    @BeforeEach
    void setUp() {
        delegate = new ISavedQueryViewService() {
            @Override public QueryResult executeQuery(Long id, List filters) {
                if (id == null || id == 0L) return null;
                return QueryResult.builder()
                        .columns(List.of("col1", "col2"))
                        .rows(List.of(List.of("a", 1), List.of("c", "d")))
                        .total(2L)
                        .build();
            }
            @Override public ai.chat2db.community.domain.api.model.PageResponse list(
                    Long workspaceId, int pageNo, int pageSize, String searchKey) { return null; }
            @Override public ai.chat2db.community.query.excel.domain.api.model.SavedQueryView getById(Long id) { return null; }
            @Override public Long create(ai.chat2db.community.query.excel.domain.api.model.SavedQueryView view) { return null; }
            @Override public void update(ai.chat2db.community.query.excel.domain.api.model.SavedQueryView view) { }
            @Override public void delete(Long id) { }
            @Override public List<ai.chat2db.community.query.excel.domain.api.ErrorCode> validate(Long id) { return List.of(); }
            @Override public void publish(Long id) { }
            @Override public void disable(Long id) { }
            @Override public boolean checkCompatibility(Long viewId) { return true; }
            @Override public Long copy(Long id, String newName) { return null; }
            @Override public ai.chat2db.community.query.excel.domain.api.model.PreviewResult preview(
                    Long id, int pageNo, int pageSize, List filters) { return null; }
        };
        adapter = new ChartSavedQueryViewAdapter(delegate);
    }

    @Test
    void executeQuery_returnsDecoupledResult() {
        ChartQueryResult result = adapter.executeQuery(42L);
        assertNotNull(result);
        assertEquals(List.of("col1", "col2"), result.columns());
        assertEquals(2, result.rows().size());
        assertEquals(List.of("a", "1"), result.rows().get(0));
        assertEquals(List.of("c", "d"), result.rows().get(1));
    }

    @Test
    void executeQuery_nullViewId_returnsNull() {
        assertNull(adapter.executeQuery(null));
    }

    @Test
    void executeQuery_delegateReturnsNull_returnsNull() {
        assertNull(adapter.executeQuery(0L));
    }

    @Test
    void executeQuery_handlesNullRows() {
        delegate = new ISavedQueryViewService() {
            @Override public QueryResult executeQuery(Long id, List filters) {
                return QueryResult.builder().columns(List.of("x")).rows(null).total(0L).build();
            }
            // Unused stubs
            @Override public ai.chat2db.community.domain.api.model.PageResponse list(
                    Long workspaceId, int pageNo, int pageSize, String searchKey) { return null; }
            @Override public ai.chat2db.community.query.excel.domain.api.model.SavedQueryView getById(Long id) { return null; }
            @Override public Long create(ai.chat2db.community.query.excel.domain.api.model.SavedQueryView view) { return null; }
            @Override public void update(ai.chat2db.community.query.excel.domain.api.model.SavedQueryView view) { }
            @Override public void delete(Long id) { }
            @Override public List<ai.chat2db.community.query.excel.domain.api.ErrorCode> validate(Long id) { return List.of(); }
            @Override public void publish(Long id) { }
            @Override public void disable(Long id) { }
            @Override public boolean checkCompatibility(Long viewId) { return true; }
            @Override public Long copy(Long id, String newName) { return null; }
            @Override public ai.chat2db.community.query.excel.domain.api.model.PreviewResult preview(
                    Long id, int pageNo, int pageSize, List filters) { return null; }
        };
        adapter = new ChartSavedQueryViewAdapter(delegate);
        ChartQueryResult result = adapter.executeQuery(1L);
        assertNotNull(result);
        assertEquals(List.of("x"), result.columns());
        assertNull(result.rows());
    }

    @Test
    void executeQuery_handlesNullRowEntries() {
        delegate = new ISavedQueryViewService() {
            @Override public QueryResult executeQuery(Long id, List filters) {
                return QueryResult.builder()
                        .columns(Arrays.asList("a"))
                        .rows(Arrays.asList(null, Arrays.asList("val")))
                        .total(2L).build();
            }
            @Override public ai.chat2db.community.domain.api.model.PageResponse list(
                    Long workspaceId, int pageNo, int pageSize, String searchKey) { return null; }
            @Override public ai.chat2db.community.query.excel.domain.api.model.SavedQueryView getById(Long id) { return null; }
            @Override public Long create(ai.chat2db.community.query.excel.domain.api.model.SavedQueryView view) { return null; }
            @Override public void update(ai.chat2db.community.query.excel.domain.api.model.SavedQueryView view) { }
            @Override public void delete(Long id) { }
            @Override public List<ai.chat2db.community.query.excel.domain.api.ErrorCode> validate(Long id) { return List.of(); }
            @Override public void publish(Long id) { }
            @Override public void disable(Long id) { }
            @Override public boolean checkCompatibility(Long viewId) { return true; }
            @Override public Long copy(Long id, String newName) { return null; }
            @Override public ai.chat2db.community.query.excel.domain.api.model.PreviewResult preview(
                    Long id, int pageNo, int pageSize, List filters) { return null; }
        };
        adapter = new ChartSavedQueryViewAdapter(delegate);
        ChartQueryResult result = adapter.executeQuery(1L);
        assertNotNull(result);
        assertEquals(2, result.rows().size());
        assertNull(result.rows().get(0));
        assertEquals(List.of("val"), result.rows().get(1));
    }
}