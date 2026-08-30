package ai.chat2db.community.query.excel.domain.api.model;

import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class QueryDataset {

    private Long id;

    private Long workspaceId;

    private String name;

    private String description;

    private Long datasourceId;

    private String databaseName;

    private String schemaName;

    private String tableName;

    private String sourceObjectType;

    private String status;

    private Integer version;

    private String sourceSchemaHash;

    private List<QueryDatasetField> fields;

    private List<DatasetFilter> baseFilters;

    private Long ownerId;

    private Date gmtCreate;

    private Date gmtModified;
}