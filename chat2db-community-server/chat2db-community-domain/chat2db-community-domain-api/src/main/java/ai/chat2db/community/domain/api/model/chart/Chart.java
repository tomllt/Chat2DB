package ai.chat2db.community.domain.api.model.chart;

import java.util.Date;

import lombok.Data;


@Data
public class Chart {


    private Long id;


    private Date gmtCreate;


    private Date gmtModified;


    private String name;


    private String description;


    private String schema;


    private Long dataSourceId;


    private String dataSourceName;


    private String schemaName;


    private String type;


    private String databaseName;


    private String ddl;


    private String deleted;


    private Long userId;


    private Object chartSchema;


    private Object metaData;


    private Object databaseInfo;


    private String refreshType;


    private String refreshCycle;


    /**
     * Dataset ID for the saved query view data source (only relevant when
     * {@link #dataSourceType} is {@code SAVED_QUERY_VIEW}).
     */
    private Long queryDatasetId;


    /**
     * Saved query view ID to execute when {@link #dataSourceType} is
     * {@code SAVED_QUERY_VIEW}.
     */
    private Long savedQueryViewId;


    /**
     * Data source type discriminator: {@code LEGACY_SQL} (default) or
     * {@code SAVED_QUERY_VIEW}.  Values correspond to {@link
     * ai.chat2db.community.domain.api.enums.ChartDataSourceType}.
     */
    private String dataSourceType;
}
