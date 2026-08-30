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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SavedQueryViewControllerTest {

    @Mock
    private ISavedQueryViewService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SavedQueryViewController(service))
                .setControllerAdvice(new QueryExcelExceptionHandler())
                .build();
    }

    @Test
    void listReturnsPage() throws Exception {
        SavedQueryView view = new SavedQueryView();
        view.setId(1L);
        view.setName("Test View");
        PageResponse<SavedQueryView> page = PageResponse.of(List.of(view), 1L, 1, 20);
        when(service.list(nullable(Long.class), anyInt(), anyInt(), nullable(String.class))).thenReturn(page);

        mockMvc.perform(get("/api/saved-query-views")
                        .param("workspaceId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.data[0].name").value("Test View"));
    }

    @Test
    void createReturnsId() throws Exception {
        when(service.create(any(SavedQueryView.class))).thenReturn(42L);

        mockMvc.perform(post("/api/saved-query-views")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New View\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(42));
    }

    @Test
    void getByIdReturnsView() throws Exception {
        SavedQueryView view = new SavedQueryView();
        view.setId(1L);
        view.setName("Test View");
        when(service.getById(1L)).thenReturn(view);

        mockMvc.perform(get("/api/saved-query-views/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test View"));
    }

    @Test
    void updateReturnsSuccess() throws Exception {
        mockMvc.perform(put("/api/saved-query-views/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).update(any(SavedQueryView.class));
    }

    @Test
    void deleteReturnsSuccess() throws Exception {
        mockMvc.perform(delete("/api/saved-query-views/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("success"));
        verify(service).delete(1L);
    }

    @Test
    void validateReturnsErrorCodes() throws Exception {
        when(service.validate(1L)).thenReturn(List.of(ErrorCode.QV_NO_DIMENSION_OR_MEASURE));

        mockMvc.perform(post("/api/saved-query-views/1/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("QV_NO_DIMENSION_OR_MEASURE"));
    }

    @Test
    void publishReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/saved-query-views/1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).publish(1L);
    }

    @Test
    void disableReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/saved-query-views/1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(service).disable(1L);
    }

    @Test
    void copyReturnsNewId() throws Exception {
        when(service.copy(1L, "Copy")).thenReturn(2L);

        mockMvc.perform(post("/api/saved-query-views/1/copy")
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
        when(service.preview(nullable(Long.class), anyInt(), anyInt(), nullable(List.class))).thenReturn(preview);

        mockMvc.perform(get("/api/saved-query-views/1/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void previewWithFilterOverridesAsJsonString() throws Exception {
        // The current controller binds List<ViewFilter> from @RequestParam which cannot
        // deserialize a JSON-encoded string. This test proves the JSON-string representation
        // is needed and will be fixed by switching to String + JSON.parseArray.
        PreviewResult preview = PreviewResult.builder()
                .rows(List.of())
                .total(0L)
                .pageNo(1)
                .pageSize(20)
                .columns(List.of("col1"))
                .build();
        when(service.preview(nullable(Long.class), anyInt(), anyInt(), nullable(List.class))).thenReturn(preview);

        String filterJson = "[{\"fieldId\":\"status\",\"filterType\":\"CATEGORICAL\",\"operator\":\"eq\",\"value\":\"ACTIVE\"}]";

        mockMvc.perform(get("/api/saved-query-views/1/preview")
                        .param("pageNo", "1")
                        .param("pageSize", "20")
                        .param("filterOverrides", filterJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void serviceNotFoundMapsTo404() throws Exception {
        doThrow(new QueryExcelException(ErrorCode.QV_NOT_FOUND.getCode(), ErrorCode.QV_NOT_FOUND.getMessage()))
                .when(service).getById(999L);

        mockMvc.perform(get("/api/saved-query-views/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceClientErrorMapsTo400() throws Exception {
        doThrow(new QueryExcelException(ErrorCode.QV_NO_DIMENSION_OR_MEASURE.getCode(),
                ErrorCode.QV_NO_DIMENSION_OR_MEASURE.getMessage()))
                .when(service).create(any(SavedQueryView.class));

        mockMvc.perform(post("/api/saved-query-views")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}