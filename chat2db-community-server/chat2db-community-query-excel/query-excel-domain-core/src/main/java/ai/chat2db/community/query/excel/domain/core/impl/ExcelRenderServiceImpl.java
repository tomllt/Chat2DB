package ai.chat2db.community.query.excel.domain.core.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.enums.Alignment;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.SheetConfig;
import ai.chat2db.community.query.excel.domain.api.service.IExcelRenderService;
import ai.chat2db.community.query.excel.domain.core.util.ExcelTemplateFileUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * POI-based {@link IExcelRenderService} implementation (requirements §8.6-8.9).
 * <p>Loads the template's stored .xlsx, fills data rows at the configured
 * positions per sheet config, preserves template styles, applies number
 * formats, null display, alignment, and freeze panes, then returns the
 * rendered workbook as a byte array.</p>
 */
@Service
public class ExcelRenderServiceImpl implements IExcelRenderService {

    @Override
    public byte[] render(ExcelReportTemplate template, List<List<Object>> rows, List<String> columns) {
        File templateFile = ExcelTemplateFileUtil.getTemplateFile(template.getId());
        if (templateFile == null || !templateFile.exists()) {
            throw new QueryExcelException(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(),
                    ErrorCode.EX_TEMPLATE_NOT_FOUND.getMessage());
        }
        try (InputStream in = new FileInputStream(templateFile);
             Workbook workbook = new XSSFWorkbook(in)) {
            renderSheets(workbook, template, rows, columns);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (QueryExcelException e) {
            throw e;
        } catch (Exception e) {
            throw new QueryExcelException(ErrorCode.EX_CORRUPTED_TEMPLATE.getCode(),
                    ErrorCode.EX_CORRUPTED_TEMPLATE.getMessage() + ": " + e.getMessage());
        }
    }

    // ── per-sheet rendering ──────────────────────────────────────

    private void renderSheets(Workbook workbook, ExcelReportTemplate template,
                              List<List<Object>> rows, List<String> columns) {
        List<SheetConfig> sheetConfigs = template.getSheetConfigs();
        if (sheetConfigs == null || rows == null) {
            return;
        }
        for (SheetConfig config : sheetConfigs) {
            if (config == null || config.getFieldBindings() == null || config.getFieldBindings().isEmpty()) {
                continue;
            }
            Sheet sheet = workbook.getSheet(config.getSheetName());
            if (sheet == null) {
                continue; // missing sheet — validation already covered it
            }
            renderSheet(sheet, config, rows, columns);
        }
    }

    private void renderSheet(Sheet sheet, SheetConfig config,
                             List<List<Object>> rows, List<String> columns) {
        if (config.getHeaderMapping() != null && !config.getHeaderMapping().isBlank()) {
            writeHeaderRow(sheet, config);
        }
        writeDataRows(sheet, config, rows, columns);
        if (config.getFreezeRows() != null && config.getFreezeRows() > 0
                || config.getFreezeColumns() != null && config.getFreezeColumns() > 0) {
            sheet.createFreezePane(safeInt(config.getFreezeColumns()), safeInt(config.getFreezeRows()));
        }
    }

    // ── header row ───────────────────────────────────────────────

    private void writeHeaderRow(Sheet sheet, SheetConfig config) {
        int headerRowIndex = Math.max(config.getDataStartRow() - 2, 0);
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) {
            headerRow = sheet.createRow(headerRowIndex);
        }
        int startColumn = Math.max(config.getDataStartColumn() - 1, 0);
        for (ExcelColumnBinding binding : config.getFieldBindings()) {
            if (binding == null || binding.getTargetColumn() == null || binding.getTargetColumn().isBlank()) {
                continue;
            }
            String displayName = binding.getDisplayName();
            if (displayName == null || displayName.isBlank()) {
                continue;
            }
            int columnIndex = columnLetterToIndex(binding.getTargetColumn());
            if (columnIndex < startColumn) {
                continue;
            }
            Cell cell = headerRow.getCell(columnIndex);
            if (cell == null) {
                cell = headerRow.createCell(columnIndex);
            }
            cell.setCellValue(displayName);
        }
    }

    // ── data rows ────────────────────────────────────────────────

    private void writeDataRows(Sheet sheet, SheetConfig config, List<List<Object>> rows, List<String> columns) {
        int rowIndex = Math.max(config.getDataStartRow() - 1, 0);
        int startColumn = Math.max(config.getDataStartColumn() - 1, 0);
        for (List<Object> row : rows) {
            if (row == null) {
                rowIndex++;
                continue;
            }
            Row targetRow = sheet.getRow(rowIndex);
            if (targetRow == null) {
                targetRow = sheet.createRow(rowIndex);
            }
            List<ExcelColumnBinding> bindings = config.getFieldBindings();
            for (int bi = 0; bi < bindings.size(); bi++) {
                ExcelColumnBinding binding = bindings.get(bi);
                if (binding == null || binding.getTargetColumn() == null || binding.getTargetColumn().isBlank()) {
                    continue;
                }
                int columnIndex = columnLetterToIndex(binding.getTargetColumn());
                if (columnIndex < startColumn || isInMergedRegion(sheet, rowIndex, columnIndex)) {
                    continue;
                }
                // Resolve value: match the binding's queryFieldId in the columns list,
                // fall back to the binding's ordinal position in the list.
                int valueIndex = (columns != null) ? columns.indexOf(binding.getQueryFieldId()) : -1;
                if (valueIndex < 0) {
                    valueIndex = bi;
                }
                Object value = valueIndex < row.size() ? row.get(valueIndex) : null;
                writeCell(targetRow, columnIndex, value, binding);
            }
            rowIndex++;
        }
    }

    private void writeCell(Row targetRow, int columnIndex, Object value, ExcelColumnBinding binding) {
        Cell cell = targetRow.getCell(columnIndex);
        if (cell == null) {
            cell = targetRow.createCell(columnIndex);
        }
        CellStyle style = cell.getCellStyle();
        CellStyle effective = style;
        if (needsFormatting(binding)) {
            effective = cloneAndApplyFormatting(targetRow.getSheet().getWorkbook(), style, binding);
        }
        if (cell.getCellType() != CellType.BLANK || effective != style) {
            cell.setCellStyle(effective);
        }
        if (value == null) {
            cell.setCellValue(binding.getNullDisplay() == null ? "" : binding.getNullDisplay());
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof java.util.Date) {
            cell.setCellValue((java.util.Date) value);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    /**
     * Clones the template cell style and applies number format and/or alignment
     * from the binding; other style aspects (fonts, borders, fills, colors) are
     * preserved from the template.
     */
    private CellStyle cloneAndApplyFormatting(Workbook workbook, CellStyle base, ExcelColumnBinding binding) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(base);
        String numberFormat = binding.getNumberFormat();
        if (numberFormat != null && !numberFormat.isBlank()) {
            short formatIndex = workbook.getCreationHelper()
                    .createDataFormat().getFormat(numberFormat);
            style.setDataFormat(formatIndex);
        }
        HorizontalAlignment alignment = parseAlignment(binding.getAlignment());
        if (alignment != null) {
            style.setAlignment(alignment);
        }
        return style;
    }

    private boolean needsFormatting(ExcelColumnBinding binding) {
        return binding.getNumberFormat() != null && !binding.getNumberFormat().isBlank()
                || binding.getAlignment() != null && !binding.getAlignment().isBlank();
    }

    /**
     * Maps the domain {@link Alignment} enum value names to POI
     * {@link HorizontalAlignment}; blank or unknown values keep the template
     * alignment.
     */
    private static HorizontalAlignment parseAlignment(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Alignment alignment = Alignment.valueOf(value);
        switch (alignment) {
            case LEFT:
                return HorizontalAlignment.LEFT;
            case CENTER:
                return HorizontalAlignment.CENTER;
            case RIGHT:
                return HorizontalAlignment.RIGHT;
            default:
                return null;
        }
    }

    // ── helpers ──────────────────────────────────────────────────

    /**
     * Converts a column letter like {@code A}, {@code B} … {@code AA} to a
     * zero-based column index (POI convention: {@code A}=0, {@code B}=1,
     * {@code AA}=26).
     */
    static int columnLetterToIndex(String letters) {
        if (letters == null || letters.isBlank()) {
            throw new QueryExcelException(ErrorCode.EX_BINDING_FIELD_NOT_FOUND.getCode(),
                    ErrorCode.EX_BINDING_FIELD_NOT_FOUND.getMessage());
        }
        String upper = letters.trim().toUpperCase();
        int index = 0;
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c < 'A' || c > 'Z') {
                throw new QueryExcelException(ErrorCode.EX_BINDING_FIELD_NOT_FOUND.getCode(),
                        ErrorCode.EX_BINDING_FIELD_NOT_FOUND.getMessage());
            }
            index = index * 26 + (c - 'A' + 1);
        }
        return index - 1;
    }

    /**
     * Returns {@code true} when the given cell (row/column indices) is covered
     * by a pre-existing merged region of the sheet.
     */
    private static boolean isInMergedRegion(Sheet sheet, int rowIndex, int columnIndex) {
        if (sheet.getNumMergedRegions() == 0) {
            return false;
        }
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.isInRange(rowIndex, columnIndex)) {
                return true;
            }
        }
        return false;
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}