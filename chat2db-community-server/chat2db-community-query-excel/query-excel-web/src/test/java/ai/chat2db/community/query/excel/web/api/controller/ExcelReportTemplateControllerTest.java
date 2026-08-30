package ai.chat2db.community.query.excel.web.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.service.IExcelReportTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ExcelReportTemplateControllerTest {

    @Mock
    private IExcelReportTemplateService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExcelReportTemplateController(service))
                .setControllerAdvice(new QueryExcelExceptionHandler())
                .build();
    }

    @Test
    void listReturnsPage() throws Exception {
        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(1L);
        template.setName("Template");
        PageResponse<ExcelReportTemplate> page = PageResponse.of(List.of(template), 1L, 1, 20);
        when(service.list(nullable(Long.class), anyInt(), anyInt(), nullable(String.class))).thenReturn(page);

        mockMvc.perform(get("/api/excel-report-templates")
                        .param("workspaceId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.data[0].name").value("Template"));
    }

    @Test
    void createReturnsId() throws Exception {
        when(service.create(any(ExcelReportTemplate.class))).thenReturn(42L);

        mockMvc.perform(post("/api/excel-report-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"T\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(42));
    }

    @Test
    void getByIdReturnsTemplate() throws Exception {
        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(1L);
        template.setName("Template");
        when(service.getById(1L)).thenReturn(template);

        mockMvc.perform(get("/api/excel-report-templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Template"));
    }

    @Test
    void updateReturnsSuccess() throws Exception {
        mockMvc.perform(put("/api/excel-report-templates/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).update(any(ExcelReportTemplate.class));
    }

    @Test
    void deleteReturnsSuccess() throws Exception {
        mockMvc.perform(delete("/api/excel-report-templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("success"));
        verify(service).delete(1L);
    }

    @Test
    void validateReturnsErrorCodes() throws Exception {
        when(service.validate(1L)).thenReturn(List.of(ErrorCode.EX_INVALID_FILE_FORMAT));

        mockMvc.perform(post("/api/excel-report-templates/1/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("EX_INVALID_FILE_FORMAT"));
    }

    @Test
    void copyReturnsNewId() throws Exception {
        when(service.copy(1L, "Copy")).thenReturn(2L);

        mockMvc.perform(post("/api/excel-report-templates/1/copy")
                        .param("name", "Copy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void uploadReturnsId() throws Exception {
        when(service.upload(anyLong(), any(), any(), any(byte[].class), anyLong()))
                .thenReturn(7L);
        MockMultipartFile file = new MockMultipartFile("file", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/excel-report-templates/upload")
                        .file(file)
                        .param("workspaceId", "1")
                        .param("name", "Template")
                        .param("queryViewId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(7));
    }

    @Test
    void getSheetNamesReturnsList() throws Exception {
        when(service.getSheetNames(1L)).thenReturn(List.of("Sheet1", "Sheet2"));

        mockMvc.perform(get("/api/excel-report-templates/1/sheet-names"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("Sheet1"));
    }

    @Test
    void updateSheetConfigsReturnsSuccess() throws Exception {
        mockMvc.perform(put("/api/excel-report-templates/1/sheet-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"sheetName\":\"Sheet1\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).updateSheetConfigs(anyLong(), anyList());
    }

    @Test
    void updateFieldBindingsReturnsSuccess() throws Exception {
        mockMvc.perform(put("/api/excel-report-templates/1/field-bindings")
                        .param("sheetName", "Sheet1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"queryFieldId\":\"f1\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).updateFieldBindings(anyLong(), any(), anyList());
    }

    @Test
    void serviceNotFoundMapsTo404() throws Exception {
        doThrow(new QueryExcelException(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(),
                ErrorCode.EX_TEMPLATE_NOT_FOUND.getMessage()))
                .when(service).getById(999L);

        mockMvc.perform(get("/api/excel-report-templates/999"))
                .andExpect(status().isNotFound());
    }
}