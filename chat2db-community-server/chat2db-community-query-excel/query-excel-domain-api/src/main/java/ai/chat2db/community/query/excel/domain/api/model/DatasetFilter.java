package ai.chat2db.community.query.excel.domain.api.model;

import java.util.List;

import lombok.Data;

@Data
public class DatasetFilter {

    private String fieldId;

    private String operator;

    private String value;

    private List<String> values;
}