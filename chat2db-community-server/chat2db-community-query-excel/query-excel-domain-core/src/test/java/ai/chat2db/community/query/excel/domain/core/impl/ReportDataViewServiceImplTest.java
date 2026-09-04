package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.query.excel.domain.api.model.ReportDataViewPreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import ai.chat2db.community.query.excel.storage.ReportBundleStorage;
import ai.chat2db.community.query.excel.storage.ReportBundleVersionStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportDataViewServiceImplTest {

    private static final Long WORKSPACE_ID = 11L;
    private static final Long BUNDLE_ID = 22L;
    private static final Long VERSION_ID = 33L;
    private static final Long VIEW_ID = 44L;

    private ReportBundleVersionStorage versions;
    private ReportBundleStorage bundles;
    private ISavedQueryViewService savedViews;
    private ReportDataViewServiceImpl service;

    @BeforeEach
    void setUp() {
        versions = mock(ReportBundleVersionStorage.class);
        bundles = mock(ReportBundleStorage.class);
        savedViews = mock(ISavedQueryViewService.class);
        service = new ReportDataViewServiceImpl(versions, bundles, savedViews);
        SavedQueryView view = new SavedQueryView();
        view.setId(VIEW_ID);
        when(savedViews.getById(VIEW_ID)).thenReturn(view);
    }

    @Test
    void previewUsesPresetOnlyFiltersAndVersionBindings() {
        ReportBundleVersion version = version(List.of(filter("region", "EU")), List.of("row-1"));
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("region");
        binding.setDisplayName("region");
        binding.setTargetColumn("Region snapshot");
        version.setBoundFieldsSnapshot(new ArrayList<>(List.of(binding)));
        ReportBundle bundle = bundle();
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(version);
        when(bundles.getById(WORKSPACE_ID, BUNDLE_ID)).thenReturn(bundle);
        when(savedViews.preview(VIEW_ID, 1, 20, version.getPresetRowFiltersSnapshot()))
                .thenReturn(preview(List.of(Map.of("__row_key", "row-1", "region", "EU"))));

        ReportDataViewPreviewResult result = service.preview(WORKSPACE_ID, VERSION_ID, 1, 20, null);

        assertEquals(List.of("row-1"), result.getRowKeys());
        assertEquals(List.of("Region snapshot"), result.getColumns());
        assertEquals("EU", result.getRows().get(0).get("Region snapshot"));
        verify(savedViews).preview(VIEW_ID, 1, 20, version.getPresetRowFiltersSnapshot());
    }

    @Test
    void previewUsesRuntimeOnlyFilters() {
        ReportBundleVersion version = version(List.of(), List.of("row-1"));
        ViewFilter runtime = filter("amount", "100");
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(version);
        when(bundles.getById(WORKSPACE_ID, BUNDLE_ID)).thenReturn(bundle());
        when(savedViews.preview(VIEW_ID, 1, 20, List.of(runtime))).thenReturn(preview(List.of()));

        service.preview(WORKSPACE_ID, VERSION_ID, 1, 20, List.of(runtime));

        verify(savedViews).preview(VIEW_ID, 1, 20, List.of(runtime));
    }

    @Test
    void previewMergesFiltersAndRuntimeReplacesSameFieldDeterministically() {
        ReportBundleVersion version = version(List.of(filter("region", "EU"), filter("amount", "10")), List.of());
        ViewFilter runtimeRegion = filter("region", "US");
        ViewFilter runtimeStatus = filter("status", "open");
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(version);
        when(bundles.getById(WORKSPACE_ID, BUNDLE_ID)).thenReturn(bundle());
        when(savedViews.preview(any(), anyInt(), anyInt(), anyList())).thenReturn(preview(List.of()));

        service.preview(WORKSPACE_ID, VERSION_ID, 1, 20, List.of(runtimeRegion, runtimeStatus));

        ArgumentCaptor<List<ViewFilter>> captor = ArgumentCaptor.forClass(List.class);
        verify(savedViews).preview(eq(VIEW_ID), eq(1), eq(20), captor.capture());
        assertEquals(List.of("region", "amount", "status"),
                captor.getValue().stream().map(ViewFilter::getFieldId).toList());
        assertEquals("US", captor.getValue().get(0).getValue());
    }

    @Test
    void previewDoesNotObserveDraftMutationAfterVersionWasSaved() {
        ReportBundleVersion version = version(List.of(filter("region", "EU")), List.of("row-1"));
        ViewFilter draftFilter = filter("region", "DRAFT");
        ReportBundle draft = bundle();
        draft.setPresetRowFilters(new ArrayList<>(List.of(draftFilter)));
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(version);
        when(bundles.getById(WORKSPACE_ID, BUNDLE_ID)).thenReturn(draft);
        when(savedViews.preview(VIEW_ID, 1, 20, version.getPresetRowFiltersSnapshot())).thenReturn(preview(List.of()));

        service.preview(WORKSPACE_ID, VERSION_ID, 1, 20, null);

        verify(savedViews).preview(VIEW_ID, 1, 20, version.getPresetRowFiltersSnapshot());
    }

    @Test
    void previewUsesBoundFieldsSnapshotAfterDraftBindingMutation() {
        ReportBundleVersion version = version(List.of(), List.of("row-1"));
        ExcelColumnBinding snapshotBinding = binding("snapshot-field", "Snapshot column");
        version.setBoundFieldsSnapshot(new ArrayList<>(List.of(snapshotBinding)));
        ReportBundle draft = bundle();
        draft.setBoundFields(new ArrayList<>(List.of(binding("draft-field", "Draft column"))));
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(version);
        when(bundles.getById(WORKSPACE_ID, BUNDLE_ID)).thenReturn(draft);
        when(savedViews.preview(VIEW_ID, 1, 20, List.of()))
                .thenReturn(preview(List.of(Map.of("snapshot-field", "saved-value", "draft-field", "draft-value"))));

        ReportDataViewPreviewResult result = service.preview(WORKSPACE_ID, VERSION_ID, 1, 20, null);

        assertEquals(List.of("Snapshot column"), result.getColumns());
        assertEquals("saved-value", result.getRows().get(0).get("Snapshot column"));
        verify(savedViews).preview(VIEW_ID, 1, 20, List.of());
    }

    @Test
    void previewUsesActualReturnedRowKeysForNonContiguousSelection() {
        ReportBundleVersion version = version(List.of(), List.of("row-1", "row-9", "row-3"));
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(version);
        when(bundles.getById(WORKSPACE_ID, BUNDLE_ID)).thenReturn(bundle());
        when(savedViews.preview(VIEW_ID, 2, 2, List.of())).thenReturn(
                PreviewResult.builder().columns(List.of("__row_key", "region"))
                        .rows(List.of(Map.of("__row_key", "row-9", "region", "US"),
                                Map.of("__row_key", "row-3", "region", "APAC")))
                        .total(3).pageNo(2).pageSize(2).build());

        ReportDataViewPreviewResult result = service.preview(WORKSPACE_ID, VERSION_ID, 2, 2, List.of());

        assertEquals(2, result.getPageNo());
        assertEquals(2, result.getPageSize());
        assertEquals(List.of("row-9", "row-3"), result.getRowKeys());
    }

    @Test
    void previewExcludesMalformedPresetAndRuntimeFiltersBeforeDelegation() {
        ViewFilter malformedPreset = filter(null, "bad-preset");
        ViewFilter malformedRuntime = filter(null, "bad-runtime");
        ReportBundleVersion version = version(List.of(malformedPreset, filter("region", "EU")), List.of());
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(version);
        when(bundles.getById(WORKSPACE_ID, BUNDLE_ID)).thenReturn(bundle());
        when(savedViews.preview(any(), anyInt(), anyInt(), anyList())).thenReturn(preview(List.of()));

        service.preview(WORKSPACE_ID, VERSION_ID, 1, 20, List.of(malformedRuntime, filter("status", "open")));

        ArgumentCaptor<List<ViewFilter>> captor = ArgumentCaptor.forClass(List.class);
        verify(savedViews).preview(eq(VIEW_ID), eq(1), eq(20), captor.capture());
        assertEquals(List.of("region", "status"), captor.getValue().stream().map(ViewFilter::getFieldId).toList());
    }

    @Test
    void previewRejectsMissingBundleWithExactError() {
        ReportBundleVersion version = version(List.of(), List.of());
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(version);
        when(bundles.getById(WORKSPACE_ID, BUNDLE_ID)).thenReturn(null);

        QueryExcelException missing = assertThrows(QueryExcelException.class,
                () -> service.preview(WORKSPACE_ID, VERSION_ID, 1, 20, List.of()));

        assertEquals(ErrorCode.QV_NOT_FOUND.getCode(), missing.getErrorCode());
        assertEquals("Report bundle not found", missing.getMessage());
    }

    @Test
    void previewRejectsMissingSavedQueryWithExactError() {
        ReportBundleVersion version = version(List.of(), List.of());
        ReportBundle bundle = bundle();
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(version);
        when(bundles.getById(WORKSPACE_ID, BUNDLE_ID)).thenReturn(bundle);
        when(savedViews.getById(VIEW_ID)).thenReturn(null);

        QueryExcelException missing = assertThrows(QueryExcelException.class,
                () -> service.preview(WORKSPACE_ID, VERSION_ID, 1, 20, List.of()));

        assertEquals(ErrorCode.QV_NOT_FOUND.getCode(), missing.getErrorCode());
        assertEquals("Saved query view not found", missing.getMessage());
    }

    @Test
    void previewRejectsMissingAndCrossWorkspaceVersions() {
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(null);
        QueryExcelException missing = assertThrows(QueryExcelException.class,
                () -> service.preview(WORKSPACE_ID, VERSION_ID, 1, 20, List.of()));
        assertEquals(ErrorCode.QV_NOT_FOUND.getCode(), missing.getErrorCode());
        assertEquals("Report bundle version not found", missing.getMessage());

        ReportBundleVersion foreignVersion = version(List.of(), List.of());
        foreignVersion.setWorkspaceId(99L);
        when(versions.getById(WORKSPACE_ID, VERSION_ID)).thenReturn(foreignVersion);
        QueryExcelException foreign = assertThrows(QueryExcelException.class,
                () -> service.preview(WORKSPACE_ID, VERSION_ID, 1, 20, List.of()));
        assertEquals(ErrorCode.QV_NOT_FOUND.getCode(), foreign.getErrorCode());
        assertEquals("Report bundle version not found", foreign.getMessage());
    }

    private ReportBundleVersion version(List<ViewFilter> filters, List<String> keys) {
        ReportBundleVersion version = new ReportBundleVersion();
        version.setWorkspaceId(WORKSPACE_ID);
        version.setBundleId(BUNDLE_ID);
        version.setId(VERSION_ID);
        version.setPresetRowFiltersSnapshot(new ArrayList<>(filters));
        version.setSelectedRowKeys(new ArrayList<>(keys));
        return version;
    }

    private ReportBundle bundle() {
        ReportBundle bundle = new ReportBundle();
        bundle.setWorkspaceId(WORKSPACE_ID);
        bundle.setId(BUNDLE_ID);
        bundle.setQueryViewId(VIEW_ID);
        return bundle;
    }

    private ExcelColumnBinding binding(String field, String targetColumn) {
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId(field);
        binding.setDisplayName(field);
        binding.setTargetColumn(targetColumn);
        return binding;
    }

    private ViewFilter filter(String field, String value) {
        ViewFilter filter = new ViewFilter();
        filter.setFieldId(field);
        filter.setOperator("EQ");
        filter.setValue(value);
        return filter;
    }

    private PreviewResult preview(List<Map<String, Object>> rows) {
        return PreviewResult.builder().rows(rows).columns(List.of("region")).total(rows.size()).pageNo(1).pageSize(20).build();
    }
}
