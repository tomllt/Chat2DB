package ai.chat2db.community.query.excel.domain.api.model;

import lombok.Data;

/**
 * Value class describing a physical column of the source table, as reported by
 * the datasource metadata provider (requirements §5.6).
 */
@Data
public class ColumnInfo {

    private String columnName;

    private String dataType;

    private Boolean nullable;
}