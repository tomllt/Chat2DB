package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.MergeRange;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.SheetConfig;
import ai.chat2db.community.query.excel.domain.api.model.ValidationError;
import ai.chat2db.community.query.excel.domain.api.service.IExcelReportTemplateService;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import ai.chat2db.community.query.excel.domain.core.util.ExcelTemplateFileUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExcelTemplateValidationServiceImpl}.
 * <p>Builds real .xlsx templates via POI and validates them through
 * the service with a mocked template service.</p>
 */
class ExcelTemplateValidationServiceImplTest {

    private IExcelReportTemplateService templateServiceMock;
    private ISavedQueryViewService viewServiceMock;
    private IQueryDatasetService datasetServiceMock;
    private ExcelTemplateValidationServiceImpl service;

    /** Files created during this test run, for cleanup. */
    private final List<File> createdFiles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        templateServiceMock = mock(IExcelReportTemplateService.class);
        viewServiceMock = mock(ISavedQueryViewService.class);
        datasetServiceMock = mock(IQueryDatasetService.class);
        service = new ExcelTemplateValidationServiceImpl(
                templateServiceMock, viewServiceMock, datasetServiceMock);
    }

    @AfterEach
    void cleanUpFiles() {
        for (File f : createdFiles) {
            if (f != null && f.exists()) {
                f.delete();
            }
        }
        createdFiles.clear();
    }

    // ── valid template ───────────────────────────────────────────

    @Test
    void validTemplateReturnsEmptyErrors() {
        ExcelReportTemplate template = templateWithFile("Data");

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        assertTrue(errors.isEmpty(), "expected no errors, got: " + errors);
    }

    // ── sheet existence ──────────────────────────────────────────

    @Test
    void templateWithUnknownSheetConfigReturnsSheetNotFound() {
        ExcelReportTemplate template = templateWithFile("Data");
        template.setSheetConfigs(List.of(sheetConfig("MissingSheet")));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(ErrorCode.EX_SHEET_NOT_FOUND.getCode(), error.getErrorCode());
        assertEquals("MissingSheet", error.getSheetName());
        assertFalse(error.isWarning());
    }

    // ── merge validation ─────────────────────────────────────────

    @Test
    void overlappingMergeRangesReturnsMergeOverlap() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        config.setDataStartRow(10);
        config.setDataStartColumn(10);
        config.setMergeRanges(List.of(
                mergeRange(0, 5, 0, 5),
                mergeRange(3, 8, 3, 8))); // overlaps first
        template.setSheetConfigs(List.of(config));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> overlap = filter(errors, ErrorCode.EX_MERGE_OVERLAP.getCode());
        assertEquals(1, overlap.size());
        assertEquals("Data", overlap.get(0).getSheetName());
        assertNotNull(overlap.get(0).getCellRange());
    }

    @Test
    void mergeOverDataStartCellReturnsMergeDataOverlap() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        config.setDataStartRow(2);
        config.setDataStartColumn(2);
        config.setMergeRanges(List.of(mergeRange(0, 5, 0, 5))); // covers (2,2)
        template.setSheetConfigs(List.of(config));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> overlap = filter(errors, ErrorCode.EX_MERGE_DATA_OVERLAP.getCode());
        assertEquals(1, overlap.size());
        assertEquals("Data", overlap.get(0).getSheetName());
        assertNotNull(overlap.get(0).getCellRange());
    }

    @Test
    void invalidMergeStartRowAfterEndRowReturnsMergeOverlap() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        config.setDataStartRow(10);
        config.setDataStartColumn(10);
        config.setMergeRanges(List.of(mergeRange(8, 2, 0, 5))); // startRow > endRow
        template.setSheetConfigs(List.of(config));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> overlap = filter(errors, ErrorCode.EX_MERGE_OVERLAP.getCode());
        assertEquals(1, overlap.size());
        assertEquals("Data", overlap.get(0).getSheetName());
        assertNotNull(overlap.get(0).getCellRange());
    }

    @Test
    void nonOverlappingMergesAreValid() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        config.setDataStartRow(50);
        config.setDataStartColumn(50);
        config.setMergeRanges(List.of(
                mergeRange(0, 1, 0, 1),
                mergeRange(2, 3, 2, 3))); // no overlap
        template.setSheetConfigs(List.of(config));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        assertTrue(errors.isEmpty(), "expected no errors, got: " + errors);
    }

    // ── freeze validation ────────────────────────────────────────

    @Test
    void negativeFreezeRowsReturnsCorruptedTemplate() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        config.setFreezeRows(-1);
        template.setSheetConfigs(List.of(config));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> corrupted = filter(errors, ErrorCode.EX_CORRUPTED_TEMPLATE.getCode());
        assertEquals(1, corrupted.size());
        assertEquals("Data", corrupted.get(0).getSheetName());
    }

    @Test
    void negativeFreezeColumnsReturnsCorruptedTemplate() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        config.setFreezeColumns(-2);
        template.setSheetConfigs(List.of(config));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> corrupted = filter(errors, ErrorCode.EX_CORRUPTED_TEMPLATE.getCode());
        assertEquals(1, corrupted.size());
    }

    @Test
    void zeroFreezeIsValid() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        config.setFreezeRows(0);
        config.setFreezeColumns(0);
        template.setSheetConfigs(List.of(config));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        assertTrue(errors.isEmpty(), "expected no errors, got: " + errors);
    }

    // ── font validation ──────────────────────────────────────────

    @Test
    void nonWhitelistFontInBindingReturnsFontFallbackWarning() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("field1");
        binding.setTargetColumn("A");
        binding.setDisplayName("Impact"); // non-whitelist font name
        config.setFieldBindings(List.of(binding));
        template.setSheetConfigs(List.of(config));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> fonts = filter(errors, ErrorCode.EX_FONT_FALLBACK.getCode());
        assertEquals(1, fonts.size());
        assertTrue(fonts.get(0).isWarning(), "font fallback should be a warning");
    }

    @Test
    void whitelistFontIsValid() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("field1");
        binding.setTargetColumn("A");
        binding.setDisplayName("Arial"); // whitelist
        config.setFieldBindings(List.of(binding));
        template.setSheetConfigs(List.of(config));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> fonts = filter(errors, ErrorCode.EX_FONT_FALLBACK.getCode());
        assertTrue(fonts.isEmpty(), "expected no font errors, got: " + fonts);
    }

    // ── data start validation ────────────────────────────────────

    @Test
    void negativeDataStartRowReturnsCorruptedTemplate() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        config.setDataStartRow(-3);
        template.setSheetConfigs(List.of(config));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> corrupted = filter(errors, ErrorCode.EX_CORRUPTED_TEMPLATE.getCode());
        assertEquals(1, corrupted.size());
        assertEquals("Data", corrupted.get(0).getSheetName());
    }

    // ── type compatibility ───────────────────────────────────────

    @Test
    void typeIncompatibleBindingNumericFormatOnTextFieldReturnsFieldTypeIncompatible() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("field1");
        binding.setTargetColumn("A");
        binding.setNumberFormat("#,##0.00");
        config.setFieldBindings(List.of(binding));
        template.setSheetConfigs(List.of(config));
        template.setQueryViewId(100L);

        // Field is VARCHAR (text), numeric format → incompatible
        QueryDatasetField field = datasetField("field1", "VARCHAR");
        when(viewServiceMock.getById(100L)).thenReturn(view(10L));
        when(datasetServiceMock.getById(10L)).thenReturn(dataset(List.of(field)));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> incompatible = filter(errors, ErrorCode.EX_FIELD_TYPE_INCOMPATIBLE.getCode());
        assertEquals(1, incompatible.size());
        assertEquals("Data", incompatible.get(0).getSheetName());
    }

    // ── template not found ───────────────────────────────────────

    @Test
    void nonexistentTemplateReturnsTemplateNotFound() {
        when(templateServiceMock.getById(999L)).thenReturn(null);

        List<ValidationError> errors = service.validateTemplate(999L);

        assertEquals(1, errors.size());
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), errors.get(0).getErrorCode());
        assertNull(errors.get(0).getSheetName());
    }

    // ── corrupted file ───────────────────────────────────────────

    @Test
    void corruptedFileNotXlsxReturnsCorruptedTemplate() {
        ExcelReportTemplate template = templateWithFile("Data");
        // Overwrite the file with garbage
        writeTemplateFile("not an xlsx at all".getBytes());
        // The template still points to the old file — but we need to make it
        // point to a garbage file. Let's set a new garbage file.
        File garbageFile = writeTemplateFile("garbage content that is not xlsx".getBytes());
        template.setTemplateFile(garbageFile.getAbsolutePath());

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> corrupted = filter(errors, ErrorCode.EX_CORRUPTED_TEMPLATE.getCode());
        assertEquals(1, corrupted.size());
    }

    @Test
    void missingFileReturnsCorruptedTemplate() {
        ExcelReportTemplate template = templateWithFile("Data");
        template.setTemplateFile("/nonexistent/path/not-found.xlsx");

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        List<ValidationError> corrupted = filter(errors, ErrorCode.EX_CORRUPTED_TEMPLATE.getCode());
        assertEquals(1, corrupted.size());
    }

    // ── multi-error accumulation ─────────────────────────────────

    @Test
    void multipleErrorsAccumulateInOneCall() {
        ExcelReportTemplate template = templateWithFile("Data");
        SheetConfig config = sheetConfig("Data");
        config.setDataStartRow(-1); // → EX_CORRUPTED_TEMPLATE
        config.setMergeRanges(List.of(mergeRange(8, 2, 0, 5))); // → EX_MERGE_OVERLAP
        template.setSheetConfigs(List.of(config));
        template.setQueryViewId(100L);

        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId("field1");
        binding.setTargetColumn("A");
        binding.setNumberFormat("#,##0.00");
        config.setFieldBindings(List.of(binding));

        QueryDatasetField textField = datasetField("field1", "VARCHAR");
        when(viewServiceMock.getById(100L)).thenReturn(view(10L));
        when(datasetServiceMock.getById(10L)).thenReturn(dataset(List.of(textField)));

        when(templateServiceMock.getById(1L)).thenReturn(template);

        List<ValidationError> errors = service.validateTemplate(1L);

        // We expect 2 errors: EX_MERGE_OVERLAP (invalid merge) + EX_CORRUPTED_TEMPLATE (negative dataStartRow)
        // EX_FIELD_TYPE_INCOMPATIBLE may also appear if the binding is checked
        assertTrue(errors.size() >= 2, "expected at least 2 errors, got: " + errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.getErrorCode().equals(ErrorCode.EX_MERGE_OVERLAP.getCode())));
    }

    // ── helpers ──────────────────────────────────────────────────

    private static List<ValidationError> filter(List<ValidationError> errors, String code) {
        return errors.stream()
                .filter(e -> e.getErrorCode().equals(code))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Creates a template whose stored .xlsx file contains a sheet with the given name.
     */
    private ExcelReportTemplate templateWithFile(String sheetName) {
        ExcelReportTemplate t = new ExcelReportTemplate();
        t.setId(1L);
        t.setName("Template 1");
        byte[] content = xlsxBytes(sheetName);
        File file = writeTemplateFile(content);
        t.setTemplateFile(file.getAbsolutePath());
        t.setSheetConfigs(List.of(sheetConfig(sheetName)));
        return t;
    }

    private static SheetConfig sheetConfig(String sheetName) {
        SheetConfig c = new SheetConfig();
        c.setSheetName(sheetName);
        c.setDataStartRow(0);
        c.setDataStartColumn(0);
        c.setFreezeRows(0);
        c.setFreezeColumns(0);
        return c;
    }

    private static MergeRange mergeRange(int startRow, int endRow, int startCol, int endCol) {
        MergeRange m = new MergeRange();
        m.setStartRow(startRow);
        m.setEndRow(endRow);
        m.setStartColumn(startCol);
        m.setEndColumn(endCol);
        return m;
    }

    private static SavedQueryView view(Long datasetId) {
        SavedQueryView v = new SavedQueryView();
        v.setDatasetId(datasetId);
        return v;
    }

    private static QueryDataset dataset(List<QueryDatasetField> fields) {
        QueryDataset d = new QueryDataset();
        d.setFields(fields);
        return d;
    }

    private static QueryDatasetField datasetField(String fieldId, String dataType) {
        QueryDatasetField f = new QueryDatasetField();
        f.setFieldId(fieldId);
        f.setDataType(dataType);
        return f;
    }

    private static byte[] xlsxBytes(String... sheetNames) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            for (String name : sheetNames) {
                Sheet sheet = wb.createSheet(name);
                sheet.createRow(0).createCell(0).setCellValue("header");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File writeTemplateFile(byte[] content) {
        File dir = ExcelTemplateFileUtil.getTemplatesDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File f = new File(dir, "valid-test-" + System.nanoTime() + ".xlsx");
        try {
            Files.write(f.toPath(), content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        createdFiles.add(f);
        return f;
    }
}