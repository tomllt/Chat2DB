package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.ExportResult;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;

/**
 * Orchestrates the full Excel export flow (requirements §8.10, §8.11, §9.2, §12.3).
 * <p>Validates the template, executes the saved query view once, checks permissions,
 * masks sensitive fields, renders the .xlsx file, writes an audit record, and
 * returns a download token. {@link #download(String)} resolves a previously
 * generated token to the stored .xlsx bytes.</p>
 */
public interface IExcelExportService {

    /**
     * Runs the full export flow for a template and query view.
     *
     * @param templateId      the Excel report template id
     * @param queryViewId     the saved query view id to execute
     * @param filterOverrides optional filter overrides applied to the query
     * @return the export result carrying the download token and metadata
     * @throws ai.chat2db.community.query.excel.domain.api.model.QueryExcelException
     *         with EX_TEMPLATE_NOT_FOUND, EX_CORRUPTED_TEMPLATE, EX_QUERY_TIMEOUT,
     *         EX_NO_DATA, EX_NO_PERMISSION, EX_ROW_LIMIT_EXCEEDED,
     *         EX_FILE_SIZE_EXCEEDED, or EX_BINDING_FIELD_DELETED
     */
    ExportResult export(Long templateId, Long queryViewId, List<ViewFilter> filterOverrides);

    /**
     * Returns the stored .xlsx file bytes for a download token.
     *
     * @param downloadToken the token returned by {@link #export(Long, Long, List)}
     * @return the rendered .xlsx file bytes
     * @throws ai.chat2db.community.query.excel.domain.api.model.QueryExcelException
     *         when the token is unknown or expired
     */
    byte[] download(String downloadToken);
}