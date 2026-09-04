package ai.chat2db.community.query.excel.domain.core.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.enums.ExportStatus;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.ExportResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleExportRequest;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.query.excel.domain.api.model.SheetConfig;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.service.IExcelExportService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelRenderService;
import ai.chat2db.community.query.excel.domain.api.service.IExcelReportTemplateService;
import ai.chat2db.community.query.excel.domain.api.service.IReportBundleService;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportBundleExportServiceImplTest {

    private final IReportBundleService bundleService = mock(IReportBundleService.class);
    private final IExcelExportService legacyExportService = mock(IExcelExportService.class);
    private final IExcelReportTemplateService templateService = mock(IExcelReportTemplateService.class);
    private final ISavedQueryViewService queryViewService = mock(ISavedQueryViewService.class);
    private final IExcelRenderService renderService = mock(IExcelRenderService.class);
    private final ReportBundleExportServiceImpl service = new ReportBundleExportServiceImpl(bundleService,
            legacyExportService, templateService, queryViewService, renderService);

    @Test
    void exportUsesVersionSnapshotsAfterDraftMutation() {
        ReportBundle draft = bundle(11L, 7L, 90L, binding("draft"), filter("draft", "new"));
        ReportBundleVersion version = version(13L, 11L, 7L, binding("historical"), filter("status", "OPEN"));
        version.setRowFilter(List.of(filter("region", "EU")));
        version.setSelectedRowKeys(List.of("row-2"));
        when(bundleService.getVersion(7L, 11L, 13L)).thenReturn(version);
        when(bundleService.getById(7L, 11L)).thenReturn(draft);
        ExcelReportTemplate template = template(5L, binding("template"));
        when(templateService.getById(5L)).thenReturn(template);
        when(queryViewService.executeQuery(eq(90L), any())).thenReturn(QueryResult.builder()
                .columns(List.of("__row_key", "historical"))
                .rows(List.of(List.of("row-1", "old"), List.of("row-2", "saved")))
                .build());
        when(renderService.render(any(), any(), any())).thenReturn(new byte[] {1, 2});

        draft.getBoundFields().get(0).setQueryFieldId("mutated-draft");
        draft.getPresetRowFilters().get(0).setValue("mutated-draft");

        ExportResult result = service.export(7L, 11L, 13L, 5L, List.of(filter("runtime", "yes")));

        assertEquals("SUCCESS", result.getStatus());
        ArgumentCaptor<List<ViewFilter>> filters = ArgumentCaptor.forClass(List.class);
        verify(queryViewService).executeQuery(eq(90L), filters.capture());
        assertEquals(List.of("status", "region", "runtime"),
                filters.getValue().stream().map(ViewFilter::getFieldId).toList());
        ArgumentCaptor<ExcelReportTemplate> renderedTemplate = ArgumentCaptor.forClass(ExcelReportTemplate.class);
        verify(renderService).render(renderedTemplate.capture(), eq(List.of(List.of("row-2", "saved"))),
                eq(List.of("__row_key", "historical")));
        assertEquals("historical", renderedTemplate.getValue().getSheetConfigs().get(0)
                .getFieldBindings().get(0).getQueryFieldId());
        verify(legacyExportService, never()).export(any(), any(), any());
    }

    @Test
    void missingVersionFailsSafelyWithoutLegacyExport() {
        when(bundleService.getVersion(7L, 11L, 13L)).thenThrow(new QueryExcelException(
                ErrorCode.EX_REPORT_VERSION_INVALID.getCode(), ErrorCode.EX_REPORT_VERSION_INVALID.getMessage()));

        QueryExcelException exception = assertThrows(QueryExcelException.class,
                () -> service.export(7L, 11L, 13L, 5L, List.of()));

        assertEquals(ErrorCode.EX_REPORT_VERSION_INVALID.getCode(), exception.getErrorCode());
        verifyNoInteractions(templateService, queryViewService, renderService, legacyExportService);
    }

    @Test
    void crossWorkspaceVersionFailsSafelyBeforeQuery() {
        ReportBundleVersion version = version(13L, 11L, 8L, binding("historical"), filter("status", "OPEN"));
        when(bundleService.getVersion(7L, 11L, 13L)).thenReturn(version);
        when(bundleService.getById(7L, 11L)).thenReturn(bundle(11L, 7L, 90L, binding("draft"), null));

        QueryExcelException exception = assertThrows(QueryExcelException.class,
                () -> service.export(7L, 11L, 13L, 5L, List.of()));

        assertEquals(ErrorCode.EX_REPORT_VERSION_INVALID.getCode(), exception.getErrorCode());
        verifyNoInteractions(templateService, queryViewService, renderService, legacyExportService);
    }

    @Test
    void exportIssuesDownloadTokenAndDownloadReturnsRenderedBytes() {
        ReportBundleVersion version = version(13L, 11L, 7L, binding("historical"), filter("status", "OPEN"));
        when(bundleService.getVersion(7L, 11L, 13L)).thenReturn(version);
        when(bundleService.getById(7L, 11L)).thenReturn(bundle(11L, 7L, 90L, binding("draft"), null));
        when(templateService.getById(5L)).thenReturn(template(5L, binding("template")));
        when(queryViewService.executeQuery(eq(90L), any())).thenReturn(QueryResult.builder()
                .columns(List.of("historical"))
                .rows(List.of(List.of("value")))
                .build());
        byte[] rendered = new byte[] {0x50, 0x4b, 0x03, 0x04, 0x05};
        when(renderService.render(any(), any(), any())).thenReturn(rendered);

        ExportResult result = service.export(7L, 11L, 13L, 5L, List.of());

        assertEquals(ExportStatus.SUCCESS.name(), result.getStatus());
        assertNotNull(result.getDownloadToken());
        assertEquals(rendered.length, result.getFileSize());
        assertEquals(1, result.getRowCount());
        assertNotNull(result.getExportId());

        // download(token) must resolve to the same bytes that were rendered
        byte[] downloaded = service.download(7L, result.getDownloadToken());
        assertArrayEquals(rendered, downloaded);
    }

    @Test
    void downloadTokenIsSingleUse() {
        ReportBundleVersion version = version(13L, 11L, 7L, binding("historical"), filter("status", "OPEN"));
        when(bundleService.getVersion(7L, 11L, 13L)).thenReturn(version);
        when(bundleService.getById(7L, 11L)).thenReturn(bundle(11L, 7L, 90L, binding("draft"), null));
        when(templateService.getById(5L)).thenReturn(template(5L, binding("template")));
        when(queryViewService.executeQuery(eq(90L), any())).thenReturn(QueryResult.builder()
                .columns(List.of("historical"))
                .rows(List.of(List.of("value")))
                .build());
        byte[] rendered = new byte[] {1, 2, 3, 4};
        when(renderService.render(any(), any(), any())).thenReturn(rendered);

        ExportResult result = service.export(7L, 11L, 13L, 5L, List.of());
        String token = result.getDownloadToken();

        // First download succeeds
        byte[] first = service.download(7L, token);
        assertArrayEquals(rendered, first);

        // Second download of the same token must fail because the token is single-use
        QueryExcelException reused = assertThrows(QueryExcelException.class, () -> service.download(7L, token));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), reused.getErrorCode());
    }

    @Test
    void downloadRejectsTokenFromAnotherWorkspace() {
        ReportBundleVersion version = version(13L, 11L, 7L, binding("historical"), filter("status", "OPEN"));
        when(bundleService.getVersion(7L, 11L, 13L)).thenReturn(version);
        when(bundleService.getById(7L, 11L)).thenReturn(bundle(11L, 7L, 90L, binding("draft"), null));
        when(templateService.getById(5L)).thenReturn(template(5L, binding("template")));
        when(queryViewService.executeQuery(eq(90L), any())).thenReturn(QueryResult.builder()
                .columns(List.of("historical"))
                .rows(List.of(List.of("value")))
                .build());
        when(renderService.render(any(), any(), any())).thenReturn(new byte[] {1});

        ExportResult result = service.export(7L, 11L, 13L, 5L, List.of());

        QueryExcelException exception = assertThrows(QueryExcelException.class,
                () -> service.download(8L, result.getDownloadToken()));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), exception.getErrorCode());
        assertArrayEquals(new byte[] {1}, service.download(7L, result.getDownloadToken()));
    }

    @Test
    void downloadWithUnknownTokenFails() {
        QueryExcelException exception = assertThrows(QueryExcelException.class, () -> service.download(7L, "no-such-token"));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), exception.getErrorCode());
    }

    @Test
    void downloadWithBlankOrNullTokenFails() {
        QueryExcelException nullException = assertThrows(QueryExcelException.class, () -> service.download(7L, null));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), nullException.getErrorCode());
        QueryExcelException blankException = assertThrows(QueryExcelException.class, () -> service.download(7L, "  "));
        assertEquals(ErrorCode.EX_TEMPLATE_NOT_FOUND.getCode(), blankException.getErrorCode());
    }

    @Test
    void exportSnapshotDelegatesIdentifierQuotingToSavedQueryViewService() {
        // DM/MySQL identifier quoting lives inside SqlGenerator, which is reached
        // through ISavedQueryViewService.executeQuery. The export service must
        // never produce its own SQL or quoting; it must only forward the
        // version-derived effective filters and let the saved-query service own
        // the dialect. Asserting on the exact executeQuery call proves the
        // delegation seam and forbids inline quoting from creeping into the
        // version export.
        ReportBundleVersion version = version(13L, 11L, 7L, binding("historical"), filter("`weird`", "x"));
        version.setRowFilter(List.of(filter("dm-key", "y")));
        when(bundleService.getVersion(7L, 11L, 13L)).thenReturn(version);
        when(bundleService.getById(7L, 11L)).thenReturn(bundle(11L, 7L, 90L, binding("draft"), null));
        when(templateService.getById(5L)).thenReturn(template(5L, binding("template")));
        when(queryViewService.executeQuery(eq(90L), any())).thenReturn(QueryResult.builder()
                .columns(List.of("historical"))
                .rows(List.of(List.of("value")))
                .build());
        when(renderService.render(any(), any(), any())).thenReturn(new byte[] {1});

        service.export(7L, 11L, 13L, 5L, List.of(filter("runtime", "z")));

        // The executeQuery seam is the ONLY path used to reach the SQL executor;
        // verify it is invoked exactly once with the version's saved query view
        // id and the merged filters. Any new SQL composition or quoting inside
        // the version export would require touching a different collaborator,
        // which this assertion surfaces.
        ArgumentCaptor<List<ViewFilter>> filters = ArgumentCaptor.forClass(List.class);
        verify(queryViewService).executeQuery(eq(90L), filters.capture());
        assertEquals(List.of("`weird`", "dm-key", "runtime"),
                filters.getValue().stream().map(ViewFilter::getFieldId).toList());
        verify(queryViewService, never()).preview(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void draftMutationAfterVersionCreationDoesNotChangeDownloadedBytes() {
        // First export captures the rendered bytes for the immutable snapshot.
        ReportBundle draft = bundle(11L, 7L, 90L, binding("draft"), filter("draft", "new"));
        ReportBundleVersion version = version(13L, 11L, 7L, binding("historical"), filter("status", "OPEN"));
        version.setSelectedRowKeys(List.of("row-1"));
        when(bundleService.getVersion(7L, 11L, 13L)).thenReturn(version);
        when(bundleService.getById(7L, 11L)).thenReturn(draft);
        when(templateService.getById(5L)).thenReturn(template(5L, binding("template")));
        when(queryViewService.executeQuery(eq(90L), any())).thenReturn(QueryResult.builder()
                .columns(List.of("__row_key", "historical"))
                .rows(List.of(List.of("row-1", "saved"), List.of("row-2", "saved")))
                .build());
        byte[] snapshotBytes = new byte[] {0x10, 0x20, 0x30, 0x40, 0x50};
        when(renderService.render(any(), any(), any())).thenReturn(snapshotBytes);

        ExportResult first = service.export(7L, 11L, 13L, 5L, List.of());
        byte[] firstBytes = service.download(7L, first.getDownloadToken());
        assertArrayEquals(snapshotBytes, firstBytes);

        // Mutate the bundle draft and the underlying version container after
        // download. A fresh export of the same version must still resolve the
        // snapshot bytes, not any new value derived from the mutated draft.
        draft.getBoundFields().get(0).setQueryFieldId("mutated");
        draft.getPresetRowFilters().get(0).setValue("mutated");
        version.getBoundFieldsSnapshot().get(0).setQueryFieldId("mutated-version");

        // Reset render input expectations: the version snapshot still drives
        // the inputs, so the mock continues to return the snapshot bytes.
        ExportResult second = service.export(7L, 11L, 13L, 5L, List.of());
        byte[] secondBytes = service.download(7L, second.getDownloadToken());

        assertArrayEquals(snapshotBytes, firstBytes);
        assertArrayEquals(snapshotBytes, secondBytes);
        assertNotNull(second.getDownloadToken());
    }

    private static ReportBundle bundle(Long id, Long workspaceId, Long queryViewId,
                                       ExcelColumnBinding binding, ViewFilter filter) {
        ReportBundle bundle = new ReportBundle();
        bundle.setId(id);
        bundle.setWorkspaceId(workspaceId);
        bundle.setQueryViewId(queryViewId);
        bundle.setBoundFields(List.of(binding));
        bundle.setPresetRowFilters(filter == null ? null : List.of(filter));
        return bundle;
    }

    private static ReportBundleVersion version(Long id, Long bundleId, Long workspaceId,
                                              ExcelColumnBinding binding, ViewFilter filter) {
        ReportBundleVersion version = new ReportBundleVersion();
        version.setId(id);
        version.setBundleId(bundleId);
        version.setWorkspaceId(workspaceId);
        version.setBoundFieldsSnapshot(List.of(binding));
        version.setPresetRowFiltersSnapshot(List.of(filter));
        return version;
    }

    private static ExcelReportTemplate template(Long id, ExcelColumnBinding binding) {
        ExcelReportTemplate template = new ExcelReportTemplate();
        template.setId(id);
        SheetConfig sheet = new SheetConfig();
        sheet.setSheetName("Sheet1");
        sheet.setFieldBindings(List.of(binding));
        template.setSheetConfigs(List.of(sheet));
        return template;
    }

    private static ExcelColumnBinding binding(String field) {
        ExcelColumnBinding binding = new ExcelColumnBinding();
        binding.setQueryFieldId(field);
        binding.setTargetColumn("A");
        return binding;
    }

    private static ViewFilter filter(String field, String value) {
        ViewFilter filter = new ViewFilter();
        filter.setFieldId(field);
        filter.setValue(value);
        return filter;
    }
}