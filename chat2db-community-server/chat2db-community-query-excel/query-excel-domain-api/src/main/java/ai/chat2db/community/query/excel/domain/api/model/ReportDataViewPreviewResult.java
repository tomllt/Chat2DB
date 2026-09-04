package ai.chat2db.community.query.excel.domain.api.model;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * Read-only preview result for a report version, including stable whole-row keys.
 */
@Data
@Builder
public class ReportDataViewPreviewResult {

    private List<Map<String, Object>> rows;

    private long total;

    private int pageNo;

    private int pageSize;

    private List<String> columns;

    private List<String> rowKeys;
}
