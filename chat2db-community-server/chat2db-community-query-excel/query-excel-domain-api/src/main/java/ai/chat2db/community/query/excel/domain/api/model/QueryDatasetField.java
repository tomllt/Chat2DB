package ai.chat2db.community.query.excel.domain.api.model;

import lombok.Data;

@Data
public class QueryDatasetField {

    private String fieldId;

    private String sourceColumn;

    private String displayName;

    private String dataType;

    private String role;

    private String aggregation;

    private Boolean filterable;

    private Boolean sortable;

    private Boolean visible;

    private String numberFormat;

    private String nullDisplay;
}