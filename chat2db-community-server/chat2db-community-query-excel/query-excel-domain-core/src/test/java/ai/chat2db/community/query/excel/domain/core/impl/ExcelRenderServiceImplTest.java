package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.SheetConfig;
import ai.chat2db.community.query.excel.domain.core.util.ExcelTemplateFileUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExcelRenderServiceImpl}.
 * <p>Builds real .xlsx templates via POI, renders data through the service,
 * and verifies cell content, style preservation, freeze panes, merges, etc.</p>
 */
class ExcelRenderServiceImplTest {

    private ExcelRenderServiceImpl service;

    /** Files created during this test run, for cleanup. */
    private final List<File> createdFiles = new ArrayList<>();

    /** Template ids whose files should be cleaned up. */
    private final List<Long> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ExcelRenderServiceImpl();
    }

    @AfterEach
    void cleanUpFiles() {
        for (File f : createdFiles) {
            if (f != null && f.exists()) {
                f.delete();
            }
        }
        for (Long id : createdIds) {
            if (id != null) {
                File f = ExcelTemplateFileUtil.getTemplateFile(id);
                if (f.exists()) {
                    f.delete();
                }
            }
        }
        createdFiles.clear();
        createdIds.clear();
    }

    // ── 1. fill 2 rows of data at dataStartRow → cells contain data ─

    @Test
    void fillTwoRowsOfDataAtDataStartRow() {
        ExcelReportTemplate template = templateWithSheet("Data", 0, 0);
        addBinding(template, "Data", "A", null, null, null);

        List<List<Object>> rows = Arrays.asList(
                Arrays.asList("Alice"),
                Arrays.asList("Bob"));
        List<String> columns = Arrays.asList("name");

        byte[] output = service.render(template, rows, columns);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet sheet = wb.getSheet("Data");
            assertNotNull(sheet);
            assertEquals("Alice", cellValue(sheet, 0, 0));
            assertEquals("Bob", cellValue(sheet, 1, 0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 2. header mapping written ─────────────────────────────────

    @Test
    void headerMappingWritten() {
        ExcelReportTemplate template = templateWithSheet("Data", 2, 0);
        addBinding(template, "Data", "A", "User Name", null, null);
        SheetConfig config = template.getSheetConfigs().get(0);
        config.setHeaderMapping("ROW_ABOVE");

        List<List<Object>> rows = Arrays.asList(
                Arrays.asList("Alice"));
        List<String> columns = Arrays.asList("name");

        byte[] output = service.render(template, rows, columns);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet sheet = wb.getSheet("Data");
            assertNotNull(sheet);
            // header at row 0 (dataStartRow-2 = 0)
            assertEquals("User Name", cellValue(sheet, 0, 0));
            // data at row 1 (dataStartRow-1 = 1)
            assertEquals("Alice", cellValue(sheet, 1, 0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 3. number format applied ──────────────────────────────────

    @Test
    void numberFormatApplied() {
        ExcelReportTemplate template = templateWithSheet("Data", 0, 0);
        addBinding(template, "Data", "A", null, "#,##0.00", null);

        List<List<Object>> rows = Arrays.asList(
                Arrays.asList(1234.5));
        List<String> columns = Arrays.asList("amount");

        byte[] output = service.render(template, rows, columns);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet sheet = wb.getSheet("Data");
            assertNotNull(sheet);
            Cell cell = sheet.getRow(0).getCell(0);
            assertNotNull(cell);
            String fmt = cell.getCellStyle().getDataFormatString();
            assertTrue(fmt.contains("#,##0.00") || fmt.contains("0.00"),
                    "expected number format in data format string, got: " + fmt);
            assertEquals(1234.5, cell.getNumericCellValue(), 0.001);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 4. null value → nullDisplay written ───────────────────────

    @Test
    void nullValueWritesNullDisplay() {
        ExcelReportTemplate template = templateWithSheet("Data", 0, 0);
        addBinding(template, "Data", "A", null, null, "N/A");

        List<List<Object>> rows = Arrays.asList(
                Arrays.asList((Object) null));
        List<String> columns = Arrays.asList("value");

        byte[] output = service.render(template, rows, columns);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet sheet = wb.getSheet("Data");
            assertNotNull(sheet);
            assertEquals("N/A", cellValue(sheet, 0, 0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 5. alignment applied ──────────────────────────────────────

    @Test
    void alignmentApplied() {
        ExcelReportTemplate template = templateWithSheet("Data", 0, 0);
        addBinding(template, "Data", "A", null, null, null, "CENTER");

        List<List<Object>> rows = Arrays.asList(
                Arrays.asList("centered"));
        List<String> columns = Arrays.asList("value");

        byte[] output = service.render(template, rows, columns);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet sheet = wb.getSheet("Data");
            assertNotNull(sheet);
            Cell cell = sheet.getRow(0).getCell(0);
            assertNotNull(cell);
            assertEquals(HorizontalAlignment.CENTER, cell.getCellStyle().getAlignment());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 6. template title/style preserved ─────────────────────────

    @Test
    void templateStylePreserved() {
        // Create a template with a styled header cell: bold, blue background, border
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Data");
        Row headerRow = sheet.createRow(0);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("Title");
        CellStyle titleStyle = wb.createCellStyle();
        titleStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        org.apache.poi.ss.usermodel.Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleStyle.setFont(titleFont);
        headerCell.setCellStyle(titleStyle);

        // Also create a template row at row 1 with data-start style so data cell has style context
        Row templateRow = sheet.createRow(1);
        Cell templateCell = templateRow.createCell(0);
        CellStyle templateStyle = wb.createCellStyle();
        templateStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        templateStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        templateCell.setCellStyle(templateStyle);

        long templateId = 100L;
        File file = ExcelTemplateFileUtil.getTemplateFile(templateId);
        createdFiles.add(file);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            wb.close();
            Files.write(file.toPath(), bos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(templateId);
        template.setName("Style Test");
        SheetConfig config = new SheetConfig();
        config.setSheetName("Data");
        config.setDataStartRow(2);
        config.setDataStartColumn(1);
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setTargetColumn("A");
        binding.setQueryFieldId("f1");
        config.setFieldBindings(Collections.singletonList(binding));
        template.setSheetConfigs(Collections.singletonList(config));
        createdIds.add(templateId);

        List<List<Object>> rows = Arrays.asList(
                Arrays.asList("dataValue"));
        List<String> columns = Arrays.asList("f1");

        byte[] output = service.render(template, rows, columns);
        try (Workbook outWb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet outSheet = outWb.getSheet("Data");
            assertNotNull(outSheet);

            // Header cell at row 0 col 0 should still have its style
            Cell outHeader = outSheet.getRow(0).getCell(0);
            assertNotNull(outHeader);
            assertEquals("Title", outHeader.getStringCellValue());
            CellStyle outHeaderStyle = outHeader.getCellStyle();
            assertNotNull(outHeaderStyle);
            // The style should still have the fill (light blue)
            // We can't easily compare fill color by index since POI may reorder,
            // but we can check the fill pattern still exists
            assertNotNull(outHeaderStyle.getFillForegroundColorColor());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 7. freezePane applied ─────────────────────────────────────

    @Test
    void freezePaneApplied() {
        ExcelReportTemplate template = templateWithSheet("Data", 2, 0);
        addBinding(template, "Data", "A", null, null, null);
        template.getSheetConfigs().get(0).setFreezeRows(2);
        template.getSheetConfigs().get(0).setFreezeColumns(1);

        List<List<Object>> rows = Arrays.asList(
                Arrays.asList("r1"),
                Arrays.asList("r2"));
        List<String> columns = Arrays.asList("col");

        byte[] output = service.render(template, rows, columns);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet sheet = wb.getSheet("Data");
            assertNotNull(sheet);
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(2, sheet.getPaneInformation().getHorizontalSplitPosition());
            assertEquals(1, sheet.getPaneInformation().getVerticalSplitPosition());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 8. existing merge ranges preserved ────────────────────────

    @Test
    void existingMergePreserved() {
        // Create a template with a merged region at the top
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Data");
        Row row0 = sheet.createRow(0);
        Cell cell0 = row0.createCell(0);
        cell0.setCellValue("Merged Title");
        Cell cell1 = row0.createCell(1);
        cell1.setCellValue("");
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        // Data row at row 1
        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("template data");

        long templateId = 200L;
        File file = ExcelTemplateFileUtil.getTemplateFile(templateId);
        createdFiles.add(file);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            wb.close();
            Files.write(file.toPath(), bos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(templateId);
        template.setName("Merge Test");
        SheetConfig config = new SheetConfig();
        config.setSheetName("Data");
        config.setDataStartRow(2);
        config.setDataStartColumn(1);
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setTargetColumn("A");
        binding.setQueryFieldId("f1");
        config.setFieldBindings(Collections.singletonList(binding));
        template.setSheetConfigs(Collections.singletonList(config));
        createdIds.add(templateId);

        List<List<Object>> rows = Arrays.asList(
                Arrays.asList("data"));
        List<String> columns = Arrays.asList("f1");

        byte[] output = service.render(template, rows, columns);
        try (Workbook outWb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet outSheet = outWb.getSheet("Data");
            assertNotNull(outSheet);
            // Merged region at row 0 cols 0-1 should still exist
            assertEquals(1, outSheet.getNumMergedRegions());
            CellRangeAddress region = outSheet.getMergedRegion(0);
            assertEquals(0, region.getFirstRow());
            assertEquals(0, region.getLastRow());
            assertEquals(0, region.getFirstColumn());
            assertEquals(1, region.getLastColumn());
            // Title still there
            assertEquals("Merged Title", cellValue(outSheet, 0, 0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 9. data not written into merged region cells ──────────────

    @Test
    void dataNotWrittenIntoMergedRegion() {
        // Create a template where the data start row/col is inside a merged region
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Data");
        sheet.addMergedRegion(new CellRangeAddress(0, 2, 0, 2));
        Row row0 = sheet.createRow(0);
        row0.createCell(0).setCellValue("Big Merge");

        long templateId = 300L;
        File file = ExcelTemplateFileUtil.getTemplateFile(templateId);
        createdFiles.add(file);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            wb.close();
            Files.write(file.toPath(), bos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(templateId);
        template.setName("Merge Skip");
        SheetConfig config = new SheetConfig();
        config.setSheetName("Data");
        config.setDataStartRow(0);
        config.setDataStartColumn(0);
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setTargetColumn("A");
        binding.setQueryFieldId("f1");
        config.setFieldBindings(Collections.singletonList(binding));
        template.setSheetConfigs(Collections.singletonList(config));
        createdIds.add(templateId);

        // Data start at (0,0) — this is inside the merged region (0,0)-(2,2)
        // The renderer should skip writing to this cell
        List<List<Object>> rows = Arrays.asList(
                Arrays.asList("shouldNotOverwrite"));
        List<String> columns = Arrays.asList("f1");

        byte[] output = service.render(template, rows, columns);
        try (Workbook outWb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet outSheet = outWb.getSheet("Data");
            assertNotNull(outSheet);
            // The merged region cell should still have the original value
            assertEquals("Big Merge", cellValue(outSheet, 0, 0));
            // The cell at (0,0) should NOT have been overwritten with "shouldNotOverwrite"
            assertTrue(outSheet.getMergedRegions().size() >= 1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 10. multi-sheet template (2 sheets, independent bindings) ─

    @Test
    void multiSheetTemplateFillsBothSheets() {
        // Create a template with 2 sheets
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet1 = wb.createSheet("Sales");
        Sheet sheet2 = wb.createSheet("Inventory");
        sheet1.createRow(0).createCell(0).setCellValue("template");
        sheet2.createRow(0).createCell(0).setCellValue("template");

        long templateId = 400L;
        File file = ExcelTemplateFileUtil.getTemplateFile(templateId);
        createdFiles.add(file);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            wb.close();
            Files.write(file.toPath(), bos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(templateId);
        template.setName("Multi Sheet");

        SheetConfig salesConfig = new SheetConfig();
        salesConfig.setSheetName("Sales");
        salesConfig.setDataStartRow(2);
        salesConfig.setDataStartColumn(1);
        ExcelColumnBinding salesBinding = new ExcelColumnBinding();
        salesBinding.setTargetColumn("A");
        salesBinding.setQueryFieldId("product");
        salesConfig.setFieldBindings(Collections.singletonList(salesBinding));

        SheetConfig inventoryConfig = new SheetConfig();
        inventoryConfig.setSheetName("Inventory");
        inventoryConfig.setDataStartRow(2);
        inventoryConfig.setDataStartColumn(1);
        ExcelColumnBinding invBinding = new ExcelColumnBinding();
        invBinding.setTargetColumn("A");
        invBinding.setQueryFieldId("quantity");
        inventoryConfig.setFieldBindings(Collections.singletonList(invBinding));

        template.setSheetConfigs(Arrays.asList(salesConfig, inventoryConfig));
        createdIds.add(templateId);

        List<List<Object>> rows = Arrays.asList(
                Arrays.asList("Widget", 100));
        List<String> columns = Arrays.asList("product", "quantity");

        byte[] output = service.render(template, rows, columns);
        try (Workbook outWb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet outSheet1 = outWb.getSheet("Sales");
            Sheet outSheet2 = outWb.getSheet("Inventory");
            assertNotNull(outSheet1);
            assertNotNull(outSheet2);
            assertEquals("Widget", cellValue(outSheet1, 1, 0));
            assertEquals("100.0", cellValue(outSheet2, 1, 0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 11. empty rows list → no rows written ─────────────────────

    @Test
    void emptyRowsListWritesNoDataHeadersStillWritten() {
        ExcelReportTemplate template = templateWithSheet("Data", 1, 0);
        addBinding(template, "Data", "A", "Name", null, null);
        template.getSheetConfigs().get(0).setHeaderMapping("ROW_ABOVE");

        List<List<Object>> rows = Collections.emptyList();
        List<String> columns = Arrays.asList("name");

        byte[] output = service.render(template, rows, columns);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet sheet = wb.getSheet("Data");
            assertNotNull(sheet);
            // Header at row 0
            assertEquals("Name", cellValue(sheet, 0, 0));
            // Row 1 (data start) should be empty or not exist
            Row row1 = sheet.getRow(1);
            assertTrue(row1 == null || row1.getCell(0) == null
                    || row1.getCell(0).getStringCellValue().isEmpty(),
                    "expected no data at row 1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 12. template missing file → QueryExcelException ───────────

    @Test
    void missingTemplateFileThrowsException() {
        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(99999L);
        template.setName("Missing");
        SheetConfig config = new SheetConfig();
        config.setSheetName("Data");
        config.setDataStartRow(0);
        config.setDataStartColumn(0);
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setTargetColumn("A");
        binding.setQueryFieldId("f1");
        config.setFieldBindings(Collections.singletonList(binding));
        template.setSheetConfigs(Collections.singletonList(config));

        QueryExcelException ex = assertThrows(QueryExcelException.class,
                () -> service.render(template, Collections.emptyList(), Collections.emptyList()));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    // ── columnLetterToIndex ───────────────────────────────────────

    @Test
    void columnLetterToIndexConvertsCorrectly() {
        assertEquals(0, ExcelRenderServiceImpl.columnLetterToIndex("A"));
        assertEquals(1, ExcelRenderServiceImpl.columnLetterToIndex("B"));
        assertEquals(25, ExcelRenderServiceImpl.columnLetterToIndex("Z"));
        assertEquals(26, ExcelRenderServiceImpl.columnLetterToIndex("AA"));
        assertEquals(27, ExcelRenderServiceImpl.columnLetterToIndex("AB"));
        assertEquals(51, ExcelRenderServiceImpl.columnLetterToIndex("AZ"));
        assertEquals(52, ExcelRenderServiceImpl.columnLetterToIndex("BA"));
        assertEquals(701, ExcelRenderServiceImpl.columnLetterToIndex("ZZ"));
        assertEquals(702, ExcelRenderServiceImpl.columnLetterToIndex("AAA"));
    }

    @Test
    void columnLetterToIndexThrowsOnInvalidInput() {
        assertThrows(QueryExcelException.class,
                () -> ExcelRenderServiceImpl.columnLetterToIndex(null));
        assertThrows(QueryExcelException.class,
                () -> ExcelRenderServiceImpl.columnLetterToIndex(""));
        assertThrows(QueryExcelException.class,
                () -> ExcelRenderServiceImpl.columnLetterToIndex("123"));
        assertThrows(QueryExcelException.class,
                () -> ExcelRenderServiceImpl.columnLetterToIndex("A1"));
    }

    // ── helpers ───────────────────────────────────────────────────

    /**
     * Creates a template whose stored .xlsx file contains a sheet with the given name.
     * The template uses the given data start row/column.
     */
    private ExcelReportTemplate templateWithSheet(String sheetName, int dataStartRow, int dataStartColumn) {
        long templateId = Math.abs(System.nanoTime());
        File file = ExcelTemplateFileUtil.getTemplateFile(templateId);
        createdFiles.add(file);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            sheet.createRow(0).createCell(0).setCellValue("template");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            Files.write(file.toPath(), bos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(templateId);
        template.setName("Test-" + sheetName);
        SheetConfig config = new SheetConfig();
        config.setSheetName(sheetName);
        config.setDataStartRow(dataStartRow);
        config.setDataStartColumn(dataStartColumn);
        config.setFieldBindings(new ArrayList<>());
        template.setSheetConfigs(Collections.singletonList(config));
        createdIds.add(templateId);
        return template;
    }

    private void addBinding(ExcelReportTemplate template, String sheetName,
                            String targetColumn, String displayName,
                            String numberFormat, String nullDisplay) {
        addBinding(template, sheetName, targetColumn, displayName, numberFormat, nullDisplay, null);
    }

    private void addBinding(ExcelReportTemplate template, String sheetName,
                            String targetColumn, String displayName,
                            String numberFormat, String nullDisplay,
                            String alignment) {
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setTargetColumn(targetColumn);
        binding.setQueryFieldId("f" + targetColumn);
        binding.setDisplayName(displayName);
        binding.setNumberFormat(numberFormat);
        binding.setNullDisplay(nullDisplay);
        binding.setAlignment(alignment);
        for (SheetConfig config : template.getSheetConfigs()) {
            if (config.getSheetName().equals(sheetName)) {
                config.getFieldBindings().add(binding);
                return;
            }
        }
    }

    private static String cellValue(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        if (r == null) return null;
        Cell c = r.getCell(col);
        if (c == null) return null;
        switch (c.getCellType()) {
            case STRING:
                return c.getStringCellValue();
            case NUMERIC:
                return String.valueOf(c.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(c.getBooleanCellValue());
            default:
                return c.toString();
        }
    }
}