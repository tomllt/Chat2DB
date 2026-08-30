package ai.chat2db.community.query.excel.web.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class QueryDatasetControllerTest {

    @Mock
    private IQueryDatasetService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new QueryDatasetController(service))
                .setControllerAdvice(new QueryExcelExceptionHandler())
                .build();
    }

    @Test
    void listReturnsPage() throws Exception {
        QueryDataset ds = new QueryDataset();
        ds.setId(1L);
        ds.setName("Test");
        PageResponse<QueryDataset> page = PageResponse.of(List.of(ds), 1L, 1, 20);
        when(service.list(nullable(Long.class), anyInt(), anyInt(), nullable(String.class))).thenReturn(page);

        mockMvc.perform(get("/api/query-datasets")
                        .param("workspaceId", "1")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.data[0].name").value("Test"));
    }

    @Test
    void createReturnsId() throws Exception {
        when(service.create(any(QueryDataset.class))).thenReturn(42L);

        mockMvc.perform(post("/api/query-datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(42));
    }

    @Test
    void getByIdReturnsDataset() throws Exception {
        QueryDataset ds = new QueryDataset();
        ds.setId(1L);
        ds.setName("Test");
        when(service.getById(1L)).thenReturn(ds);

        mockMvc.perform(get("/api/query-datasets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test"));
    }

    @Test
    void updateReturnsSuccess() throws Exception {
        mockMvc.perform(put("/api/query-datasets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).update(any(QueryDataset.class));
    }

    @Test
    void deleteReturnsSuccess() throws Exception {
        mockMvc.perform(delete("/api/query-datasets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("success"));
        verify(service).delete(1L);
    }

    @Test
    void validateReturnsErrorCodes() throws Exception {
        when(service.validate(1L)).thenReturn(List.of(ErrorCode.DS_NO_FIELDS));

        mockMvc.perform(post("/api/query-datasets/1/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value("DS_NO_FIELDS"));
    }

    @Test
    void publishReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/query-datasets/1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).publish(1L);
    }

    @Test
    void disableReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/query-datasets/1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).disable(1L);
    }

    @Test
    void copyReturnsNewId() throws Exception {
        when(service.copy(1L, "Copy")).thenReturn(2L);

        mockMvc.perform(post("/api/query-datasets/1/copy")
                        .param("name", "Copy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void previewReturnsResult() throws Exception {
        PreviewResult preview = PreviewResult.builder()
                .rows(List.of())
                .total(0L)
                .pageNo(1)
                .pageSize(20)
                .columns(List.of("col1"))
                .build();
        when(service.preview(1L, 1, 20)).thenReturn(preview);

        mockMvc.perform(get("/api/query-datasets/1/preview")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void serviceNotFoundMapsTo404() throws Exception {
        doThrow(new QueryExcelException(ErrorCode.DS_NOT_FOUND.getCode(), ErrorCode.DS_NOT_FOUND.getMessage()))
                .when(service).getById(999L);

        mockMvc.perform(get("/api/query-datasets/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceClientErrorMapsTo400() throws Exception {
        doThrow(new QueryExcelException(ErrorCode.DS_NO_FIELDS.getCode(), ErrorCode.DS_NO_FIELDS.getMessage()))
                .when(service).create(any(QueryDataset.class));

        mockMvc.perform(post("/api/query-datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedExceptionMapsTo500() throws Exception {
        when(service.getById(1L)).thenThrow(new RuntimeException("Unexpected"));

        mockMvc.perform(get("/api/query-datasets/1"))
                .andExpect(status().isInternalServerError());
    }
}