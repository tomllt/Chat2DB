package ai.chat2db.community.query.excel.storage;

import java.util.ArrayList;
import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.storage.small.SmallDataStorage;

public class ExcelReportTemplateStorage extends SmallDataStorage<ExcelReportTemplate> {

    public static final ExcelReportTemplateStorage INSTANCE = new ExcelReportTemplateStorage();

    protected ExcelReportTemplateStorage() {
        super("excel-report-template", ExcelReportTemplate.class);
    }

    public List<ExcelReportTemplate> queryByQueryViewId(Long queryViewId) {
        List<ExcelReportTemplate> result = new ArrayList<>();
        for (ExcelReportTemplate template : dataMap.values()) {
            if (template.getQueryViewId() != null && template.getQueryViewId().equals(queryViewId)) {
                result.add(template);
            }
        }
        return result;
    }
}