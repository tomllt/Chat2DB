package ai.chat2db.community.query.excel.domain.api.model;

import java.util.Date;

import lombok.Data;

@Data
public class ExcelExportRecord {

    private Long id;

    private Long workspaceId;

    private String queryId;

    private Long templateId;

    private Long queryViewId;

    private Long datasetId;

    private Integer datasetVersion;

    private Integer queryViewVersion;

    private Integer templateVersion;

    private Long userId;

    private String status;

    private Long queryMs;

    private Integer rowCount;

    private Long fileSize;

    private String errorCode;

    private String permissionResult;

    private String downloadToken;

    private Date downloadTokenExpiresAt;

    private Date exportedAt;

    private Date gmtCreate;

    private Date gmtModified;
}