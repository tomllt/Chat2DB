package ai.chat2db.community.query.excel.web.api.controller;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.tools.wrapper.result.ActionResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link QueryExcelException} from the service layer onto HTTP status
 * codes: not-found to 404, client-side validation failures to 400, everything
 * else to 500.
 */
@RestControllerAdvice(assignableTypes = {
        QueryDatasetController.class,
        SavedQueryViewController.class,
        ExcelReportTemplateController.class,
        ExcelExportController.class
})
public class QueryExcelExceptionHandler {

    private static final String NOT_FOUND = "NOT_FOUND";

    @ExceptionHandler(QueryExcelException.class)
    public ResponseEntity<ActionResult> handleQueryExcelException(QueryExcelException ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ErrorCode.DS_NOT_FOUND.getCode().equals(ex.getErrorCode())
                || ErrorCode.QV_NOT_FOUND.getCode().equals(ex.getErrorCode())
                || ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode().equals(ex.getErrorCode())
                || ErrorCode.EX_SHEET_NOT_FOUND.getCode().equals(ex.getErrorCode())
                || ErrorCode.DS_SOURCE_TABLE_DELETED.getCode().equals(ex.getErrorCode())) {
            status = HttpStatus.NOT_FOUND;
        } else if (isClientError(ex.getErrorCode())) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status)
                .body(ActionResult.fail(ex.getErrorCode(), ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ActionResult> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ActionResult.fail("INTERNAL_ERROR", ex.getMessage(), ex.getClass().getSimpleName()));
    }

    private static boolean isClientError(String code) {
        if (code == null) {
            return false;
        }
        return code.startsWith("DS_") || code.startsWith("QV_") || code.startsWith("EX_")
                || code.startsWith("PERM_");
    }
}