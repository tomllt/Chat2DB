package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.SheetConfig;

/**
 * Domain service for managing {@link ExcelReportTemplate} definitions and
 * uploading template files (requirements §8.1-8.3, §8.11).
 */
public interface IExcelReportTemplateService {

    /**
     * Lists Excel report templates, optionally filtered by workspace and search key, paginated.
     */
    PageResponse<ExcelReportTemplate> list(Long workspaceId, int pageNo, int pageSize, String searchKey);

    /**
     * Returns an Excel report template by id, or {@code null} when it does not exist.
     */
    ExcelReportTemplate getById(Long id);

    /**
     * Creates a new Excel report template; returns the generated id.
     */
    Long create(ExcelReportTemplate template);

    /**
     * Updates an existing Excel report template, enforcing optimistic locking.
     */
    void update(ExcelReportTemplate template);

    /**
     * Deletes an Excel report template by id, including its stored template file.
     */
    void delete(Long id);

    /**
     * Validates an Excel report template; returns collected error codes (empty list = valid).
     */
    List<ErrorCode> validate(Long id);

    /**
     * Deep-copies an Excel report template into a new template; returns the new id.
     */
    Long copy(Long id, String newName);

    /**
     * Uploads a template file (binary .xlsx content) and registers a new
     * template bound to the given query view; returns the generated id.
     */
    Long upload(Long workspaceId, String name, String description, byte[] fileContent, Long queryViewId);

    /**
     * Returns the sheet names of the stored template file for the given template id.
     */
    List<String> getSheetNames(Long templateId);

    /**
     * Replaces the entire sheet configuration list for a template.
     * Validates every sheet name, field binding, and column reference before saving.
     *
     * @param templateId   the template id
     * @param sheetConfigs the new list of sheet configs (replaces all existing ones)
     * @throws ai.chat2db.community.query.excel.domain.api.model.QueryExcelException
     *         with EX_TEMPLATE_NOT_FOUND, EX_SHEET_NOT_FOUND, or EX_BINDING_FIELD_NOT_FOUND
     */
    void updateSheetConfigs(Long templateId, List<SheetConfig> sheetConfigs);

    /**
     * Replaces the field bindings for a single sheet within a template.
     * Validates the sheet name and every binding before saving.
     *
     * @param templateId the template id
     * @param sheetName  the sheet whose field bindings to update
     * @param bindings   the new list of column bindings (replaces existing ones)
     * @throws ai.chat2db.community.query.excel.domain.api.model.QueryExcelException
     *         with EX_TEMPLATE_NOT_FOUND, EX_SHEET_NOT_FOUND, or EX_BINDING_FIELD_NOT_FOUND
     */
    void updateFieldBindings(Long templateId, String sheetName, List<ExcelColumnBinding> bindings);
}