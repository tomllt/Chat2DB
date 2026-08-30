package ai.chat2db.community.query.excel.domain.api.model;

import lombok.Getter;

@Getter
public class QueryExcelException extends RuntimeException {

    private final String errorCode;

    private final String message;

    public QueryExcelException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
    }

    public static QueryExcelException of(String errorCode, String message) {
        return new QueryExcelException(errorCode, message);
    }
}