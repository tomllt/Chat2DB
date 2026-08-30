package ai.chat2db.community.query.excel.domain.api.model;

import java.util.List;

import lombok.Data;

@Data
public class SheetConfig {

    private String sheetName;

    private Integer dataStartRow;

    private Integer dataStartColumn;

    private String headerMapping;

    private String rowExpansionMode;

    private Integer freezeRows;

    private Integer freezeColumns;

    private List<MergeRange> mergeRanges;

    private Boolean autoWidth;

    private String emptyResultBehavior;

    private List<ExcelColumnBinding> fieldBindings;
}