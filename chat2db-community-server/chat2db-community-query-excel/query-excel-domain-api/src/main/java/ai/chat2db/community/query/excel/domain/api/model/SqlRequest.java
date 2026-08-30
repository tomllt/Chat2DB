package ai.chat2db.community.query.excel.domain.api.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * A parameterized SQL query with ordered bind parameters.
 */
@Data
@Builder
public class SqlRequest {

    /** Parameterized SQL string with {@code ?} placeholders. */
    private String sql;

    /** Ordered bind parameter values. */
    private List<Object> params;
}