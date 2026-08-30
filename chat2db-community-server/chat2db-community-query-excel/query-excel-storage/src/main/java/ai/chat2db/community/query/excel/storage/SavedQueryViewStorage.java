package ai.chat2db.community.query.excel.storage;

import java.util.ArrayList;
import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.storage.small.SmallDataStorage;

public class SavedQueryViewStorage extends SmallDataStorage<SavedQueryView> {

    public static final SavedQueryViewStorage INSTANCE = new SavedQueryViewStorage();

    protected SavedQueryViewStorage() {
        super("saved-query-view", SavedQueryView.class);
    }

    public List<SavedQueryView> queryByDatasetId(Long datasetId) {
        List<SavedQueryView> result = new ArrayList<>();
        for (SavedQueryView view : dataMap.values()) {
            if (view.getDatasetId() != null && view.getDatasetId().equals(datasetId)) {
                result.add(view);
            }
        }
        return result;
    }
}