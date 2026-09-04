package ai.chat2db.community.query.excel.web.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.model.ExportResult;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.query.excel.domain.api.model.ReportDataViewPreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.service.IReportBundleExportService;
import ai.chat2db.community.query.excel.domain.api.service.IReportBundleService;
import ai.chat2db.community.query.excel.domain.api.service.IReportDataViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReportBundleControllerTest {

    @Mock
    private IReportBundleService bundleService;

    @Mock
    private IReportDataViewService dataViewService;

    @Mock
    private IReportBundleExportService exportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReportBundleController(bundleService, dataViewService, exportService))
                .setControllerAdvice(new QueryExcelExceptionHandler())
                .build();
    }

    @Test
    void listReturnsPage() throws Exception {
        ReportBundle bundle = new ReportBundle();
        bundle.setName("Sales");
        when(bundleService.list(7L, 1, 20, null)).thenReturn(PageResponse.of(List.of(bundle), 1L, 1, 20));

        mockMvc.perform(get("/api/report-bundles").param("workspaceId", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.data[0].name").value("Sales"));
    }

    @Test
    void crudDelegatesWorkspaceIdentity() throws Exception {
        when(bundleService.create(org.mockito.ArgumentMatchers.any(ReportBundle.class))).thenReturn(11L);
        mockMvc.perform(post("/api/report-bundles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceId\":7,\"name\":\"Sales\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(11));

        mockMvc.perform(put("/api/report-bundles/11").param("workspaceId", "7")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk());
        verify(bundleService).update(eq(7L), org.mockito.ArgumentMatchers.any(ReportBundle.class));

        mockMvc.perform(get("/api/report-bundles/11").param("workspaceId", "7")).andExpect(status().isOk());
        verify(bundleService).getById(7L, 11L);

        mockMvc.perform(delete("/api/report-bundles/11").param("workspaceId", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("success"));
        verify(bundleService).delete(7L, 11L);
    }

    @Test
    void versionsAndPresetFiltersDelegate() throws Exception {
        when(bundleService.listVersions(7L, 11L)).thenReturn(List.of(new ReportBundleVersion()));
        mockMvc.perform(get("/api/report-bundles/11/versions").param("workspaceId", "7"))
                .andExpect(status().isOk());
        verify(bundleService).listVersions(7L, 11L);

        mockMvc.perform(put("/api/report-bundles/11/preset-filters").param("workspaceId", "7")
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"fieldId\":\"region\"}]"))
                .andExpect(status().isOk());
        verify(bundleService).updatePresetFilters(eq(7L), eq(11L), anyList());
    }

    @Test
    void versionCreateGetAndDeleteDelegate() throws Exception {
        ReportBundleVersion version = new ReportBundleVersion();
        version.setVersionName("v1");
        when(bundleService.saveAsNewVersion(eq(7L), eq(11L), eq("v1"), nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class))).thenReturn(version);
        mockMvc.perform(post("/api/report-bundles/11/versions").param("workspaceId", "7")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versionName\":\"v1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.versionName").value("v1"));

        when(bundleService.getVersion(7L, 11L, 13L)).thenReturn(version);
        mockMvc.perform(get("/api/report-bundles/11/versions/13").param("workspaceId", "7"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/report-bundles/11/versions/13").param("workspaceId", "7"))
                .andExpect(status().isOk());
        verify(bundleService).deleteVersion(7L, 11L, 13L);
    }

    @Test
    void blankFilterOverridesAreNotMutationAndDelegateAsNull() throws Exception {
        when(dataViewService.preview(eq(7L), eq(13L), eq(1), eq(20), nullable(List.class)))
                .thenReturn(ReportDataViewPreviewResult.builder().total(0).pageNo(1).pageSize(20).build());

        mockMvc.perform(get("/api/report-bundle-versions/13/preview").param("workspaceId", "7")
                        .param("filterOverrides", "  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(dataViewService).preview(eq(7L), eq(13L), eq(1), eq(20), nullable(List.class));
        verifyNoInteractions(bundleService, exportService);
    }

    @Test
    void previewPassesVersionAndFilterOverrides() throws Exception {
        when(dataViewService.preview(eq(7L), eq(13L), eq(2), eq(50), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(ReportDataViewPreviewResult.builder().total(1).pageNo(2).pageSize(50).build());
        mockMvc.perform(get("/api/report-bundle-versions/13/preview").param("workspaceId", "7")
                        .param("pageNo", "2").param("pageSize", "50")
                        .param("filterOverrides", "[{\"fieldId\":\"region\",\"value\":\"EU\"}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void malformedFilterOverridesReturnsBadRequestWithoutMutation() throws Exception {
        mockMvc.perform(get("/api/report-bundle-versions/13/preview").param("workspaceId", "7")
                        .param("filterOverrides", "[{malformed}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EX_021"));

        verifyNoInteractions(bundleService, dataViewService, exportService);
    }

    @Test
    void blankVersionNameReturnsBadRequestWithoutMutation() throws Exception {
        doThrow(new QueryExcelException(ErrorCode.EX_REPORT_VERSION_INVALID.getCode(),
                ErrorCode.EX_REPORT_VERSION_INVALID.getMessage()))
                .when(bundleService).saveAsNewVersion(eq(7L), eq(11L), eq(" "), nullable(List.class),
                        nullable(List.class), nullable(List.class), nullable(List.class));

        mockMvc.perform(post("/api/report-bundles/11/versions").param("workspaceId", "7")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versionName\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EX_019"));
        verifyNoInteractions(dataViewService, exportService);
    }

    @Test
    void duplicateVersionNameReturnsBadRequestWithoutMutation() throws Exception {
        doThrow(new QueryExcelException(ErrorCode.EX_REPORT_VERSION_DUPLICATE.getCode(),
                ErrorCode.EX_REPORT_VERSION_DUPLICATE.getMessage()))
                .when(bundleService).saveAsNewVersion(eq(7L), eq(11L), eq("v1"), nullable(List.class),
                        nullable(List.class), nullable(List.class), nullable(List.class));

        mockMvc.perform(post("/api/report-bundles/11/versions").param("workspaceId", "7")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versionName\":\"v1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EX_020"));
        verifyNoInteractions(dataViewService, exportService);
    }

    @Test
    void exportUsesSelectedVersionQueryViewAndFilters() throws Exception {
        when(exportService.exportSnapshot(eq(7L), eq(11L), eq(13L), any()))
                .thenReturn(ExportResult.builder().status("SUCCESS").build());

        mockMvc.perform(post("/api/report-bundles/11/versions/13/export").param("workspaceId", "7")
                        .param("templateId", "5").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"fieldId\":\"region\",\"value\":\"EU\"}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCESS"));
        verify(exportService).exportSnapshot(eq(7L), eq(11L), eq(13L), any());
    }

    @Test
    void downloadReturnsXlsxBytesWithAttachmentEnvelope() throws Exception {
        byte[] content = new byte[] {0x50, 0x4b, 0x03, 0x04, 0x05, 0x06};
        when(exportService.download(7L, "tok_xyz")).thenReturn(content);

mockMvc.perform(get("/api/report-bundle-version-exports/download")
                         .param("workspaceId", "7").param("token", "tok_xyz"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(content));
        verify(exportService).download(7L, "tok_xyz");
    }

    @Test
    void downloadWithUnknownTokenReturnsTemplateNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new QueryExcelException(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(),
                        ErrorCode.EX_TEMPLATE_NOT_FOUND.getMessage()))
                .when(exportService).download(7L, "missing");

mockMvc.perform(get("/api/report-bundle-version-exports/download")
                         .param("workspaceId", "7").param("token", "missing"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.errorCode").value("EX_001"));
        verify(exportService).download(7L, "missing");
    }
}
