package ai.chat2db.community.query.excel.domain.api.model;

import java.util.List;

import lombok.Data;

@Data
public class ViewFilter {

    private String fieldId;

    private String filterType;

    private String operator;

    private String value;

    private List<String> values;
}