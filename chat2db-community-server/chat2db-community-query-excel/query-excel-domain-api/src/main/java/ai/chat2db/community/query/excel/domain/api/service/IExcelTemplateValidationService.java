package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.ValidationError;

/**
 * Validates an {@link ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate}
 * against its stored .xlsx file and the full set of structural rules
 * defined in §8.6-8.9, §8.11, §8.12, and §13.12.
 * <p>An empty returned list means the template is fully valid.</p>
 */
public interface IExcelTemplateValidationService {

    /**
     * Validates the template with the given id.
     *
     * @param templateId the template id
     * @return list of validation errors (empty = valid)
     */
    List<ValidationError> validateTemplate(Long templateId);
}