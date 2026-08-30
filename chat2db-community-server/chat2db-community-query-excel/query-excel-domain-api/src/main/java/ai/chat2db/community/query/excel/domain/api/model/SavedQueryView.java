package ai.chat2db.community.query.excel.domain.api.model;

import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class SavedQueryView {

    private Long id;

    private Long workspaceId;

    private Long datasetId;

    private Integer datasetVersion;

    private String name;

    private String description;

    private List<ViewDimension> dimensions;

    private List<ViewMeasure> measures;

    private List<String> rowFields;

    private List<String> columnFields;

    private List<ViewFilter> filters;

    private List<ViewSort> sort;

    private Integer pageSize;

    private String status;

    private Integer version;

    private Long ownerId;

    private Date gmtCreate;

    private Date gmtModified;
}