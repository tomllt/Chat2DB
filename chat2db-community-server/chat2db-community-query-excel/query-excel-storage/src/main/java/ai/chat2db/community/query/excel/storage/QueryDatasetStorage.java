package ai.chat2db.community.query.excel.storage;

import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.storage.small.SmallDataStorage;

public class QueryDatasetStorage extends SmallDataStorage<QueryDataset> {

    public static final QueryDatasetStorage INSTANCE = new QueryDatasetStorage();

    protected QueryDatasetStorage() {
        super("query-dataset", QueryDataset.class);
    }
}