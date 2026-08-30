package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;

/**
 * Renders an {@link ExcelReportTemplate} with data rows, producing a populated
 * .xlsx byte array (requirements §8.6-8.9).
 * <p>The renderer loads the template's stored .xlsx file via
 * {@link ai.chat2db.community.query.excel.domain.core.util.ExcelTemplateFileUtil},
 * fills data rows at the configured positions, preserves template styles,
 * applies number formats, null display, alignment, and freeze panes.</p>
 *
 * <h3>Render algorithm (per sheet config)</h3>
 * <ol>
 *   <li>Load the template file into an {@link org.apache.poi.xssf.usermodel.XSSFWorkbook}</li>
 *   <li>For each {@link ai.chat2db.community.query.excel.domain.api.model.SheetConfig}
 *       with non-empty field bindings:
 *     <ul>
 *       <li>Look up the sheet by name</li>
 *       <li>Write header row (display names) if header mapping is configured</li>
 *       <li>Write data rows starting at the configured data start row/column</li>
 *       <li>Apply number format, null display, and alignment per binding</li>
 *       <li>Preserve existing template styles (clone and override only format/alignment)</li>
 *       <li>Skip cells that fall inside pre-existing merged regions</li>
 *       <li>Apply freeze panes</li>
 *     </ul>
 *   </li>
 *   <li>Write the workbook to a byte array and return it</li>
 * </ol>
 */
public interface IExcelRenderService {

    /**
     * Renders the template with the given data rows and column names.
     *
     * @param template the template definition (must have an id, sheetConfigs,
     *                 and a stored .xlsx file accessible via
     *                 {@link ai.chat2db.community.query.excel.domain.core.util.ExcelTemplateFileUtil})
     * @param rows     the data rows to fill; each row is a list of cell values
     *                 whose ordinal positions match the field bindings
     * @param columns  the column names corresponding to the bindings (by ordinal position)
     * @return the rendered .xlsx byte array
     * @throws ai.chat2db.community.query.excel.domain.api.model.QueryExcelException
     *         when the template file is missing or corrupted
     *         (error codes {@code EX_TEMPLATE_NOT_FOUND} / {@code EX_CORRUPTED_TEMPLATE})
     */
    byte[] render(ExcelReportTemplate template, List<List<Object>> rows, List<String> columns);
}