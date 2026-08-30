package ai.chat2db.community.query.excel.domain.api.model;

import lombok.Data;

@Data
public class MergeRange {

    private Integer startRow;

    private Integer endRow;

    private Integer startColumn;

    private Integer endColumn;
}