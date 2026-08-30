package ai.chat2db.community.query.excel.domain.api.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Result of a SQL query execution, decoupled from Chat2DB's existing execution
 * model (requirements §6.5). Columns are ordered column names; rows are
 * ordered column-value lists matching the column order.
 */
@Data
@Builder
public class QueryResult {

    /** Column names in display order. */
    private List<String> columns;

    /** Data rows, each entry is a list of cell values matching {@link #columns}. */
    private List<List<Object>> rows;

    /** Total number of rows (before pagination). */
    private long total;
}