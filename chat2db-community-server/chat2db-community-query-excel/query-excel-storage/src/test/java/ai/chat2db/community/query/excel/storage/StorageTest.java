package ai.chat2db.community.query.excel.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ai.chat2db.community.query.excel.domain.api.model.ExcelExportRecord;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;

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