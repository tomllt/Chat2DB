package ai.chat2db.community.query.excel.domain.api.model;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * Result shape of a dataset preview query.
 * <p>Rows are plain column-name to value maps; {@code columns} lists the
 * visible column names in display order.</p>
 */
@Data
@Builder
public class PreviewResult {

    private List<Map<String, Object>> rows;

    private long total;

    private int pageNo;

    private int pageSize;

    private List<String> columns;
}