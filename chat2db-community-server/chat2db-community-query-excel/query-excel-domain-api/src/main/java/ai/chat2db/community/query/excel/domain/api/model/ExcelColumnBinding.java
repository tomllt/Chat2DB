package ai.chat2db.community.query.excel.domain.api.model;

import lombok.Data;

@Data
public class ExcelColumnBinding {

    private String queryFieldId;

    private String targetColumn;

    private String displayName;

    private String numberFormat;

    private String nullDisplay;

    private String alignment;

    private Boolean exportEnabled;
}