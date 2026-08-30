package ai.chat2db.community.query.excel.domain.api.model;

import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class ExcelReportTemplate {

    private Long id;

    private Long workspaceId;

    private String name;

    private String description;

    private String templateFile;

    private String fileHash;

    private Integer templateVersion;

    private Long queryViewId;

    private List<SheetConfig> sheetConfigs;

    private String status;

    private Long ownerId;

    private Date gmtCreate;

    private Date gmtModified;
}