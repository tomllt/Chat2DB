package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.storage.ReportBundleStorage;
import ai.chat2db.community.query.excel.storage.ReportBundleVersionStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportBundleServiceImplTest {

    private final ReportBundleStorage bundles = mock(ReportBundleStorage.class);
    private final ReportBundleVersionStorage versions = mock(ReportBundleVersionStorage.class);
    private final ReportBundleServiceImpl service = new ReportBundleServiceImpl(bundles, versions);

    @Test
    void workspaceScopedCrudAndVersionLifecycleRejectsForeignRecords() {
        ReportBundle draft = bundle(null, 7L);
        draft.setName("  Sales  ");
        when(bundles.save(any(ReportBundle.class))).thenReturn(10L);
        assertEquals(10L, service.create(draft));
        assertEquals("Sales", draft.getName());

        ReportBundle stored = bundle(10L, 7L);
        stored.setName("Sales");
        when(bundles.getById(7L, 10L)).thenReturn(stored);
        when(bundles.getDataList(7L)).thenReturn(List.of(stored));
        PageResponse<ReportBundle> page = service.list(7L, 1, 10, "sales");
        assertEquals(1, page.getData().size());
        service.update(7L, bundle(10L, 7L));
        service.delete(7L, 10L);
        verify(bundles).update(org.mockito.ArgumentMatchers.eq(7L), any(ReportBundle.class));
        verify(bundles).delete(7L, 10L);

        ReportBundleVersion storedVersion = version(20L, 10L, 1, "v1");
        when(versions.getById(7L, 20L)).thenReturn(storedVersion);
        assertEquals(storedVersion, service.getVersion(7L, 10L, 20L));
        service.deleteVersion(7L, 10L, 20L);
        verify(versions).delete(7L, 20L);

        when(bundles.getById(8L, 10L)).thenReturn(null);
        QueryExcelException missingBundle = assertThrows(QueryExcelException.class,
                () -> service.getById(8L, 10L));
        assertEquals(ErrorCode.EX_REPORT_BUNDLE_NOT_FOUND.getCode(), missingBundle.getErrorCode());
        assertEquals(ErrorCode.EX_REPORT_BUNDLE_NOT_FOUND.getMessage(), missingBundle.getMessage());
        QueryExcelException missingVersionsBundle = assertThrows(QueryExcelException.class,
                () -> service.listVersions(8L, 10L));
        assertEquals(ErrorCode.EX_REPORT_BUNDLE_NOT_FOUND.getCode(), missingVersionsBundle.getErrorCode());
        verify(versions, never()).queryByWorkspaceIdAndBundleId(any(Long.class), any(Long.class));
    }

    @Test
    void saveAsNewVersionRejectsBlankAndDuplicateNames() {
        ReportBundle bundle = bundle(10L, 7L);
        when(bundles.getById(7L, 10L)).thenReturn(bundle);
        ReportBundleVersion existing = version(20L, 10L, 1, "Initial");
        when(versions.queryByWorkspaceIdAndBundleId(7L, 10L)).thenReturn(List.of(existing));

        QueryExcelException blankName = assertThrows(QueryExcelException.class,
                () -> service.saveAsNewVersion(7L, 10L, " ", null, null, null, null));
        assertEquals(ErrorCode.EX_REPORT_VERSION_INVALID.getCode(), blankName.getErrorCode());
        assertEquals("Report version name is required", blankName.getMessage());
        QueryExcelException duplicateName = assertThrows(QueryExcelException.class,
                () -> service.saveAsNewVersion(7L, 10L, "Initial", null, null, null, null));
        assertEquals(ErrorCode.EX_REPORT_VERSION_DUPLICATE.getCode(), duplicateName.getErrorCode());
        assertEquals(ErrorCode.EX_REPORT_VERSION_DUPLICATE.getMessage(), duplicateName.getMessage());
        verify(versions, never()).save(any());
    }

    @Test
    void saveAsNewVersionUsesMonotonicNumberAndDeepCopiesSnapshot() {
        ReportBundle bundle = bundle(10L, 7L);
        bundle.setBoundFields(List.of(binding("source")));
        bundle.setPresetRowFilters(List.of(filter("status", "OPEN")));
        when(bundles.getById(7L, 10L)).thenReturn(bundle);
        when(versions.queryByWorkspaceIdAndBundleId(7L, 10L)).thenReturn(List.of(version(20L, 10L, 3, "v3")));
        when(versions.save(any(ReportBundleVersion.class))).thenAnswer(invocation -> {
            ReportBundleVersion saved = invocation.getArgument(0);
            saved.setId(21L);
            return 21L;
        });

        List<ExcelColumnBinding> fields = new ArrayList<>(List.of(binding("draft")));
        List<ViewFilter> filters = new ArrayList<>(List.of(filter("kind", "A")));
        List<String> keys = new ArrayList<>(List.of("row-1"));
        ReportBundleVersion result = service.saveAsNewVersion(7L, 10L, "v4", fields, filters, filters, keys);
        fields.get(0).setQueryFieldId("changed");
        filters.get(0).setValue("changed");
        keys.set(0, "changed");

        assertEquals(4, result.getVersionNo());
        assertEquals("draft", result.getBoundFieldsSnapshot().get(0).getQueryFieldId());
        assertEquals("A", result.getRowFilter().get(0).getValue());
        assertEquals("row-1", result.getSelectedRowKeys().get(0));
        assertNotSame(fields, result.getBoundFieldsSnapshot());
        ArgumentCaptor<ReportBundle> bundleCaptor = ArgumentCaptor.forClass(ReportBundle.class);
        verify(bundles).update(org.mockito.ArgumentMatchers.eq(7L), bundleCaptor.capture());
        assertEquals(21L, bundleCaptor.getValue().getActiveVersionId());
        assertEquals("source", bundleCaptor.getValue().getBoundFields().get(0).getQueryFieldId());
        assertEquals("OPEN", bundleCaptor.getValue().getPresetRowFilters().get(0).getValue());
    }

    @Test
    void concurrentSavesForSameBundleRejectDuplicateNamesAndAllocateUniqueNumbers() throws Exception {
        ReportBundle bundle = bundle(10L, 7L);
        when(bundles.getById(7L, 10L)).thenReturn(bundle);
        List<ReportBundleVersion> persisted = new ArrayList<>();
        when(versions.queryByWorkspaceIdAndBundleId(7L, 10L)).thenAnswer(invocation -> persisted.stream().toList());
        when(versions.save(any(ReportBundleVersion.class))).thenAnswer(invocation -> {
            ReportBundleVersion saved = invocation.getArgument(0);
            synchronized (persisted) {
                persisted.add(saved);
            }
            return (long) (persisted.size() + 20);
        });
        int callers = 8;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            List<Future<ReportBundleVersion>> results = IntStream.range(0, callers)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return service.saveAsNewVersion(7L, 10L, index < 2 ? "same-name" : "v" + index,
                                null, null, null, null);
                    })).toList();
            ready.await();
            start.countDown();
            List<ReportBundleVersion> saved = results.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    return null;
                }
            }).filter(java.util.Objects::nonNull).toList();

            assertEquals(callers - 1, saved.size());
            assertEquals(callers - 1, saved.stream().map(ReportBundleVersion::getVersionName).distinct().count());
            assertEquals(callers - 1, saved.stream().map(ReportBundleVersion::getVersionNo).distinct().count());
            assertEquals(callers - 1, persisted.size());
            assertTrue(saved.stream().allMatch(version -> version.getVersionNo() >= 1));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void saveAsNewVersionPersistsVersionBeforeChangingActiveVersion() {
        ReportBundle bundle = bundle(10L, 7L);
        when(bundles.getById(7L, 10L)).thenReturn(bundle);
        when(versions.queryByWorkspaceIdAndBundleId(7L, 10L)).thenReturn(List.of());
        doThrow(new IllegalStateException("disk full")).when(versions).save(any());

        assertThrows(IllegalStateException.class,
                () -> service.saveAsNewVersion(7L, 10L, "v1", null, null, null, null));
        verify(bundles, never()).update(any(Long.class), any(ReportBundle.class));
    }

    @Test
    void activeVersionUpdateFailureLeavesVersionPersistedForExplicitCleanup() {
        ReportBundle bundle = bundle(10L, 7L);
        bundle.setActiveVersionId(19L);
        when(bundles.getById(7L, 10L)).thenReturn(bundle);
        when(versions.queryByWorkspaceIdAndBundleId(7L, 10L)).thenReturn(List.of());
        when(versions.save(any(ReportBundleVersion.class))).thenAnswer(invocation -> 21L);
        doThrow(new IllegalStateException("active pointer write failed"))
                .when(bundles).update(any(Long.class), any(ReportBundle.class));

        assertThrows(IllegalStateException.class,
                () -> service.saveAsNewVersion(7L, 10L, "v1", null, null, null, null));

        ArgumentCaptor<ReportBundleVersion> versionCaptor = ArgumentCaptor.forClass(ReportBundleVersion.class);
        verify(versions).save(versionCaptor.capture());
        assertNotNull(versionCaptor.getValue());
        assertEquals(21L, versionCaptor.getValue().getId());
        assertEquals(19L, bundle.getActiveVersionId());
        verify(bundles).update(org.mockito.ArgumentMatchers.eq(7L), any(ReportBundle.class));
    }

    @Test
    void crossWorkspaceVersionAccessIsRejectedAndPresetUpdatesAreDraftOnly() {
        when(bundles.getById(7L, 10L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.getVersion(7L, 10L, 20L));
        verify(versions, never()).getById(any(Long.class), any(Long.class));

        ReportBundle bundle = bundle(10L, 7L);
        when(bundles.getById(7L, 10L)).thenReturn(bundle);
        List<ViewFilter> filters = new ArrayList<>(List.of(filter("state", "READY")));
        service.updatePresetFilters(7L, 10L, filters);
        filters.get(0).setValue("MUTATED");
        ArgumentCaptor<ReportBundle> captor = ArgumentCaptor.forClass(ReportBundle.class);
        verify(bundles).update(org.mockito.ArgumentMatchers.eq(7L), captor.capture());
        assertEquals("READY", captor.getValue().getPresetRowFilters().get(0).getValue());
    }

    private static ReportBundle bundle(Long id, Long workspaceId) {
        ReportBundle bundle = new ReportBundle();
        bundle.setId(id);
        bundle.setWorkspaceId(workspaceId);
        bundle.setName("Bundle");
        return bundle;
    }

    private static ReportBundle bundleWithActiveVersion(Long id, Long activeVersionId, Long workspaceId) {
        ReportBundle bundle = bundle(id, workspaceId);
        bundle.setActiveVersionId(activeVersionId);
        return bundle;
    }

    private static ReportBundleVersion version(Long id, Long bundleId, int no, String name) {
        ReportBundleVersion version = new ReportBundleVersion();
        version.setId(id);
        version.setBundleId(bundleId);
        version.setVersionNo(no);
        version.setVersionName(name);
        return version;
    }

    private static ExcelColumnBinding binding(String field) {
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId(field);
        binding.setTargetColumn("A");
        return binding;
    }

    private static ViewFilter filter(String field, String value) {
        ViewFilter filter = new ViewFilter();
        filter.setFieldId(field);
        filter.setValue(value);
        return filter;
    }
}
