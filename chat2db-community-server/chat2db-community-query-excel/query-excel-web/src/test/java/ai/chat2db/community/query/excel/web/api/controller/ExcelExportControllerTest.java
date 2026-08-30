package ai.chat2db.community.query.excel.web.api.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.ExportResult;
import ai.chat2db.community.query.excel.domain.api.service.IExcelExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ExcelExportControllerTest {

    @Mock
    private IExcelExportService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExcelExportController(service))
                .setControllerAdvice(new QueryExcelExceptionHandler())
                .build();
    }

    @Test
    void exportReturnsResult() throws Exception {
        ExportResult result = ExportResult.builder()
                .downloadToken("tok_123")
                .exportId(1L)
                .rowCount(100)
                .fileSize(2048L)
                .status("SUCCESS")
                .build();
        when(service.export(anyLong(), anyLong(), nullable(List.class))).thenReturn(result);

        mockMvc.perform(post("/api/saved-query-views/5/export/excel")
                        .param("templateId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.downloadToken").value("tok_123"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void getExportStatusReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/excel-exports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void downloadReturnsXlsxBytes() throws Exception {
        byte[] content = new byte[] {0x50, 0x4b, 0x03, 0x04};
        when(service.download("tok_123")).thenReturn(content);

        mockMvc.perform(get("/api/excel-exports/1/download")
                        .param("token", "tok_123"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(content().bytes(content));
    }
}