package ai.chat2db.community.query.excel.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.chat2db.community.query.excel.domain.api.model.ExcelExportRecord;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;

class StorageTest {

    @Test
    void queryDatasetCrud() {
        QueryDatasetStorage storage = QueryDatasetStorage.INSTANCE;

        QueryDataset dataset = new QueryDataset();
        dataset.setName("test-dataset-" + System.nanoTime());
        Long id = storage.save(dataset);
        assertNotNull(id);

        try {
            QueryDataset loaded = storage.getById(id);
            assertNotNull(loaded);
            assertEquals(dataset.getName(), loaded.getName());

            loaded.setDescription("updated");
            storage.update(loaded);
            assertEquals("updated", storage.getById(id).getDescription());

            assertTrue(storage.getDataList().stream().anyMatch(d -> id.equals(d.getId())));

            storage.delete(id);
            assertNull(storage.getById(id));
        } finally {
            storage.delete(id);
        }
    }

    @Test
    void savedQueryViewCrudAndQueryByDataset() {
        SavedQueryViewStorage storage = SavedQueryViewStorage.INSTANCE;

        Long datasetId = 900001L;
        Long otherDatasetId = 900002L;

        SavedQueryView view1 = new SavedQueryView();
        view1.setDatasetId(datasetId);
        view1.setName("view-matching");

        SavedQueryView view2 = new SavedQueryView();
        view2.setDatasetId(datasetId);
        view2.setName("view-matching-2");

        SavedQueryView view3 = new SavedQueryView();
        view3.setDatasetId(otherDatasetId);
        view3.setName("view-other");

        Long id1 = storage.save(view1);
        Long id2 = storage.save(view2);
        Long id3 = storage.save(view3);
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotNull(id3);

        try {
            SavedQueryView loaded = storage.getById(id1);
            assertNotNull(loaded);
            assertEquals(datasetId, loaded.getDatasetId());

            loaded.setName("renamed");
            storage.update(loaded);
            assertEquals("renamed", storage.getById(id1).getName());

            List<SavedQueryView> matches = storage.queryByDatasetId(datasetId);
            assertEquals(2, matches.size());
            assertTrue(matches.stream().allMatch(v -> datasetId.equals(v.getDatasetId())));

            List<SavedQueryView> otherMatches = storage.queryByDatasetId(otherDatasetId);
            assertEquals(1, otherMatches.size());
            assertEquals(view3.getName(), otherMatches.get(0).getName());

            List<SavedQueryView> noMatches = storage.queryByDatasetId(999999L);
            assertTrue(noMatches.isEmpty());

            storage.delete(id1);
            assertNull(storage.getById(id1));
        } finally {
            storage.delete(id1);
            storage.delete(id2);
            storage.delete(id3);
        }
    }

    @Test
    void excelReportTemplateCrudAndQueryByQueryView() {
        ExcelReportTemplateStorage storage = ExcelReportTemplateStorage.INSTANCE;

        Long queryViewId = 800001L;
        Long otherQueryViewId = 800002L;

        ExcelReportTemplate template1 = new ExcelReportTemplate();
        template1.setQueryViewId(queryViewId);
        template1.setName("template-matching");

        ExcelReportTemplate template2 = new ExcelReportTemplate();
        template2.setQueryViewId(otherQueryViewId);
        template2.setName("template-other");

        Long id1 = storage.save(template1);
        Long id2 = storage.save(template2);
        assertNotNull(id1);
        assertNotNull(id2);

        try {
            ExcelReportTemplate loaded = storage.getById(id1);
            assertNotNull(loaded);
            assertEquals(queryViewId, loaded.getQueryViewId());

            loaded.setName("renamed");
            storage.update(loaded);
            assertEquals("renamed", storage.getById(id1).getName());

            List<ExcelReportTemplate> matches = storage.queryByQueryViewId(queryViewId);
            assertEquals(1, matches.size());
            assertEquals(id1, matches.get(0).getId());

            List<ExcelReportTemplate> noMatches = storage.queryByQueryViewId(999999L);
            assertTrue(noMatches.isEmpty());

            storage.delete(id1);
            assertNull(storage.getById(id1));
        } finally {
            storage.delete(id1);
            storage.delete(id2);
        }
    }

    @Test
    void reportBundleStorageIsWorkspaceScopedAndDefensivelyCopiesDrafts() {
        ReportBundleStorage storage = ReportBundleStorage.INSTANCE;
        Long workspaceId = 710001L;
        Long otherWorkspaceId = 710002L;
        ReportBundle bundle = new ReportBundle();
        bundle.setWorkspaceId(workspaceId);
        bundle.setName("bundle-" + System.nanoTime());
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setTargetColumn("revenue");
        bundle.setBoundFields(new ArrayList<>(List.of(binding)));

        Long id = storage.save(bundle);
        assertNotNull(id);
        try {
            assertEquals(1, storage.getDataList(workspaceId).stream()
                    .filter(saved -> id.equals(saved.getId())).count());
            assertTrue(storage.getDataList(otherWorkspaceId).stream()
                    .noneMatch(saved -> id.equals(saved.getId())));
            assertNotNull(storage.getById(workspaceId, id));
            assertNull(storage.getById(otherWorkspaceId, id));

            binding.setTargetColumn("mutated-after-save");
            assertEquals("revenue", storage.getById(workspaceId, id).getBoundFields().get(0).getTargetColumn());

            ReportBundle update = new ReportBundle();
            update.setId(id);
            update.setWorkspaceId(otherWorkspaceId);
            update.setName("cross-workspace-update");
            storage.update(otherWorkspaceId, update);
            assertEquals(bundle.getName(), storage.getById(workspaceId, id).getName());

            storage.delete(otherWorkspaceId, id);
            assertNotNull(storage.getById(workspaceId, id));
            storage.update(workspaceId, updateForBundle(id, workspaceId, "updated"));
            assertEquals("updated", storage.getById(workspaceId, id).getName());
        } finally {
            storage.delete(workspaceId, id);
        }
    }

    @Test
    void reportBundleVersionStorageQueriesByWorkspaceAndBundleAndCopiesSnapshots() {
        ReportBundleVersionStorage storage = ReportBundleVersionStorage.INSTANCE;
        Long workspaceId = 720001L;
        Long otherWorkspaceId = 720002L;
        Long bundleId = System.nanoTime();
        ReportBundleVersion version = new ReportBundleVersion();
        version.setWorkspaceId(workspaceId);
        version.setBundleId(bundleId);
        version.setVersionName("version-" + System.nanoTime());
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setTargetColumn("revenue");
        version.setBoundFieldsSnapshot(new ArrayList<>(List.of(binding)));
        ViewFilter filter = new ViewFilter();
        filter.setValues(new ArrayList<>(List.of("open")));
        version.setPresetRowFiltersSnapshot(new ArrayList<>(List.of(filter)));
        version.setSelectedRowKeys(new ArrayList<>(List.of("row-1")));

        Long id = storage.save(version);
        assertNotNull(id);
        try {
            assertEquals(1, storage.queryByWorkspaceIdAndBundleId(workspaceId, bundleId).size());
            assertTrue(storage.queryByWorkspaceIdAndBundleId(otherWorkspaceId, bundleId).isEmpty());
            assertNull(storage.getById(otherWorkspaceId, id));

            binding.setTargetColumn("mutated-binding");
            filter.getValues().set(0, "closed");
            version.getSelectedRowKeys().set(0, "row-2");
            ReportBundleVersion loaded = storage.getById(workspaceId, id);
            assertEquals("revenue", loaded.getBoundFieldsSnapshot().get(0).getTargetColumn());
            assertEquals("open", loaded.getPresetRowFiltersSnapshot().get(0).getValues().get(0));
            assertEquals("row-1", loaded.getSelectedRowKeys().get(0));

            storage.delete(otherWorkspaceId, id);
            assertNotNull(storage.getById(workspaceId, id));
            storage.delete(workspaceId, id);
            assertNull(storage.getById(workspaceId, id));
        } finally {
            storage.delete(workspaceId, id);
        }
    }

    private ReportBundle updateForBundle(Long id, Long workspaceId, String name) {
        ReportBundle update = new ReportBundle();
        update.setId(id);
        update.setWorkspaceId(workspaceId);
        update.setName(name);
        return update;
    }

    @Test
    void unscopedInheritedMethodsRejectAccess() {
        ReportBundleStorage bundles = ReportBundleStorage.INSTANCE;
        ReportBundleVersionStorage versions = ReportBundleVersionStorage.INSTANCE;
        assertThrows(UnsupportedOperationException.class, () -> bundles.getById(1L));
        assertThrows(UnsupportedOperationException.class, bundles::getDataList);
        assertThrows(UnsupportedOperationException.class, () -> bundles.update(new ReportBundle()));
        assertThrows(UnsupportedOperationException.class, () -> bundles.delete(1L));
        assertThrows(UnsupportedOperationException.class, () -> versions.getById(1L));
        assertThrows(UnsupportedOperationException.class, versions::getDataList);
        assertThrows(UnsupportedOperationException.class, () -> versions.update(new ReportBundleVersion()));
        assertThrows(UnsupportedOperationException.class, () -> versions.delete(1L));
    }

    @Test
    void malformedNestedElementsAreSkippedAtStorageBoundary() {
        ReportBundle bundle = new ReportBundle();
        bundle.setWorkspaceId(730001L);
        bundle.setName("malformed-bundle-" + System.nanoTime());
        List<ExcelColumnBinding> malformedBindings = new ArrayList<>();
        malformedBindings.add(null);
        bundle.setBoundFields(malformedBindings);
        List<ViewFilter> malformedFilters = new ArrayList<>();
        malformedFilters.add(null);
        bundle.setPresetRowFilters(malformedFilters);

        Long bundleId = ReportBundleStorage.INSTANCE.save(bundle);
        assertNotNull(bundleId);
        ReportBundleVersion version = new ReportBundleVersion();
        version.setWorkspaceId(730001L);
        version.setBundleId(bundleId);
        version.setVersionName("malformed-version-" + System.nanoTime());
        List<ExcelColumnBinding> malformedSnapshotBindings = new ArrayList<>();
        malformedSnapshotBindings.add(null);
        version.setBoundFieldsSnapshot(malformedSnapshotBindings);
        List<ViewFilter> malformedSnapshotFilters = new ArrayList<>();
        malformedSnapshotFilters.add(null);
        version.setPresetRowFiltersSnapshot(malformedSnapshotFilters);
        version.setRowFilter(new ArrayList<>(malformedSnapshotFilters));
        List<String> malformedKeys = new ArrayList<>(List.of("row-1"));
        malformedKeys.add(null);
        version.setSelectedRowKeys(malformedKeys);
        Long versionId = ReportBundleVersionStorage.INSTANCE.save(version);

        try {
            assertNotNull(versionId);
            assertTrue(ReportBundleStorage.INSTANCE.getById(730001L, bundleId).getBoundFields().isEmpty());
            ReportBundleVersion loaded = ReportBundleVersionStorage.INSTANCE.getById(730001L, versionId);
            assertTrue(loaded.getBoundFieldsSnapshot().isEmpty());
            assertTrue(loaded.getPresetRowFiltersSnapshot().isEmpty());
            assertTrue(loaded.getRowFilter().isEmpty());
            assertEquals(List.of("row-1"), loaded.getSelectedRowKeys());
        } finally {
            ReportBundleVersionStorage.INSTANCE.delete(730001L, versionId);
            ReportBundleStorage.INSTANCE.delete(730001L, bundleId);
        }
    }

    @Test
    void freshStorageInstancesReloadPersistedRecords() throws Exception {
        Long workspaceId = 740001L;
        ReportBundle bundle = new ReportBundle();
        bundle.setWorkspaceId(workspaceId);
        bundle.setName("reload-bundle-" + System.nanoTime());
        Long bundleId = ReportBundleStorage.INSTANCE.save(bundle);
        ReportBundleVersion version = new ReportBundleVersion();
        version.setWorkspaceId(workspaceId);
        version.setBundleId(bundleId);
        version.setVersionName("reload-version-" + System.nanoTime());
        Long versionId = ReportBundleVersionStorage.INSTANCE.save(version);

        try {
            ReportBundleStorage reloadedBundles = freshStorage(ReportBundleStorage.class);
            ReportBundleVersionStorage reloadedVersions = freshStorage(ReportBundleVersionStorage.class);
            assertEquals(bundle.getName(), reloadedBundles.getById(workspaceId, bundleId).getName());
            assertEquals(version.getVersionName(), reloadedVersions.getById(workspaceId, versionId).getVersionName());
            assertEquals(1, reloadedVersions.queryByWorkspaceIdAndBundleId(workspaceId, bundleId).size());
        } finally {
            ReportBundleVersionStorage.INSTANCE.delete(workspaceId, versionId);
            ReportBundleStorage.INSTANCE.delete(workspaceId, bundleId);
        }
    }

    private <T> T freshStorage(Class<T> storageType) throws Exception {
        Constructor<T> constructor = storageType.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @Test
    void excelExportRecordCrud() {
        ExcelExportRecordStorage storage = ExcelExportRecordStorage.INSTANCE;

        ExcelExportRecord record = new ExcelExportRecord();
        record.setQueryId("q-" + System.nanoTime());
        record.setStatus("SUCCEEDED");
        Long id = storage.save(record);
        assertNotNull(id);

        try {
            ExcelExportRecord loaded = storage.getById(id);
            assertNotNull(loaded);
            assertEquals(record.getQueryId(), loaded.getQueryId());

            loaded.setStatus("FAILED");
            storage.update(loaded);
            assertEquals("FAILED", storage.getById(id).getStatus());

            assertTrue(storage.getDataList().stream().anyMatch(r -> id.equals(r.getId())));

            storage.delete(id);
            assertNull(storage.getById(id));
        } finally {
            storage.delete(id);
        }
    }
}