package ai.chat2db.community.query.excel.storage;

import ai.chat2db.community.query.excel.domain.api.model.ExcelExportRecord;
import ai.chat2db.community.storage.small.SmallDataStorage;
import org.springframework.stereotype.Component;

@Component
public class ExcelExportRecordStorage extends SmallDataStorage<ExcelExportRecord> {

    public static final ExcelExportRecordStorage INSTANCE = new ExcelExportRecordStorage();

    protected ExcelExportRecordStorage() {
        super("excel-export-record", ExcelExportRecord.class);
    }
}