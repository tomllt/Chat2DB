package ai.chat2db.community.query.excel.web.api.controller;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.model.ExportResult;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleExportRequest;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.query.excel.domain.api.model.ReportDataViewPreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.service.IReportBundleExportService;
import ai.chat2db.community.query.excel.domain.api.service.IReportBundleService;
import ai.chat2db.community.query.excel.domain.api.service.IReportDataViewService;
import ai.chat2db.community.tools.annotation.NotCliRuntime;
import ai.chat2db.community.tools.wrapper.result.ActionResult;
import ai.chat2db.community.tools.wrapper.result.DataResult;
import ai.chat2db.community.tools.wrapper.result.web.WebPageResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for report bundles and immutable report versions. */
@RestController
@RequestMapping("/api")
@NotCliRuntime
public class ReportBundleController {

    private final IReportBundleService reportBundleService;
    private final IReportDataViewService reportDataViewService;
    private final IReportBundleExportService reportBundleExportService;

    public ReportBundleController(IReportBundleService reportBundleService,
                                  IReportDataViewService reportDataViewService,
                                  IReportBundleExportService reportBundleExportService) {
        this.reportBundleService = reportBundleService;
        this.reportDataViewService = reportDataViewService;
        this.reportBundleExportService = reportBundleExportService;
    }

    @GetMapping("/report-bundles")
    public WebPageResult<ReportBundle> list(@RequestParam("workspaceId") Long workspaceId,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                            @RequestParam(value = "searchKey", required = false) String searchKey) {
        PageResponse<ReportBundle> page = reportBundleService.list(workspaceId, pageNo, pageSize, searchKey);
        return WebPageResult.of(page.getData(), page.getTotal(), page.getPageNo(), page.getPageSize());
    }

    @PostMapping("/report-bundles")
    public DataResult<Long> create(@RequestBody ReportBundle bundle) {
        return DataResult.of(reportBundleService.create(bundle));
    }

    @GetMapping("/report-bundles/{id}")
    public DataResult<ReportBundle> get(@PathVariable("id") Long id, @RequestParam("workspaceId") Long workspaceId) {
        return DataResult.of(reportBundleService.getById(workspaceId, id));
    }

    @PutMapping("/report-bundles/{id}")
    public ActionResult update(@PathVariable("id") Long id, @RequestParam("workspaceId") Long workspaceId,
                               @RequestBody ReportBundle bundle) {
        bundle.setId(id);
        bundle.setWorkspaceId(workspaceId);
        reportBundleService.update(workspaceId, bundle);
        return ActionResult.isSuccess();
    }

    @DeleteMapping("/report-bundles/{id}")
    public DataResult<String> delete(@PathVariable("id") Long id, @RequestParam("workspaceId") Long workspaceId) {
        reportBundleService.delete(workspaceId, id);
        return DataResult.of("success");
    }

    @GetMapping("/report-bundles/{bundleId}/versions")
    public DataResult<List<ReportBundleVersion>> listVersions(@PathVariable Long bundleId,
                                                               @RequestParam("workspaceId") Long workspaceId) {
        return DataResult.of(reportBundleService.listVersions(workspaceId, bundleId));
    }

    @PutMapping("/report-bundles/{bundleId}/preset-filters")

    public ActionResult updatePresetFilters(@PathVariable Long bundleId,
                                            @RequestParam("workspaceId") Long workspaceId,
                                            @RequestBody List<ViewFilter> filters) {
        reportBundleService.updatePresetFilters(workspaceId, bundleId, filters);
        return ActionResult.isSuccess();
    }

    @PostMapping("/report-bundles/{bundleId}/versions")
    public DataResult<ReportBundleVersion> createVersion(@PathVariable Long bundleId,
                                                         @RequestParam("workspaceId") Long workspaceId,
                                                         @RequestBody ReportBundleVersion request) {
        return DataResult.of(reportBundleService.saveAsNewVersion(workspaceId, bundleId, request.getVersionName(),
                request.getBoundFieldsSnapshot(), request.getPresetRowFiltersSnapshot(), request.getRowFilter(),
                request.getSelectedRowKeys()));
    }

    @GetMapping("/report-bundles/{bundleId}/versions/{versionId}")
    public DataResult<ReportBundleVersion> getVersion(@PathVariable Long bundleId, @PathVariable Long versionId,
                                                      @RequestParam("workspaceId") Long workspaceId) {
        return DataResult.of(reportBundleService.getVersion(workspaceId, bundleId, versionId));
    }

    @DeleteMapping("/report-bundles/{bundleId}/versions/{versionId}")
    public DataResult<String> deleteVersion(@PathVariable Long bundleId, @PathVariable Long versionId,
                                            @RequestParam("workspaceId") Long workspaceId) {
        reportBundleService.deleteVersion(workspaceId, bundleId, versionId);
        return DataResult.of("success");
    }

    @GetMapping("/report-bundle-versions/{versionId}/preview")
    public DataResult<ReportDataViewPreviewResult> preview(@PathVariable Long versionId,
                                                           @RequestParam("workspaceId") Long workspaceId,
                                                           @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                           @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                                           @RequestParam(value = "filterOverrides", required = false) String filterOverrides) {
        return DataResult.of(reportDataViewService.preview(workspaceId, versionId, pageNo, pageSize,
                SavedQueryViewController.parseFilterOverrides(filterOverrides)));
    }

    @PostMapping("/report-bundles/{bundleId}/versions/{versionId}/export")
    public DataResult<ExportResult> export(@PathVariable Long bundleId, @PathVariable Long versionId,
                                           @RequestParam("workspaceId") Long workspaceId,
                                           @RequestParam("templateId") Long templateId,
                                           @RequestBody(required = false) List<ViewFilter> runtimeFilters) {
        ReportBundleExportRequest request = ReportBundleExportRequest.builder()
                .templateId(templateId)
                .runtimeFilters(runtimeFilters)
                .build();
        return DataResult.of(reportBundleExportService.exportSnapshot(workspaceId, bundleId, versionId, request));
    }

    /**
     * Resolves a previously issued snapshot download token to the rendered .xlsx
     * bytes. The response envelope mirrors the legacy
     * {@link ExcelExportController#download} endpoint
     * ({@code application/octet-stream}, attachment disposition, content length).
     */
    @GetMapping("/report-bundle-version-exports/download")
    public ResponseEntity<byte[]> download(@RequestParam("workspaceId") Long workspaceId,
                                           @RequestParam("token") String downloadToken) {
        byte[] content = reportBundleExportService.download(workspaceId, downloadToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "report-bundle-version.xlsx");
        headers.setContentLength(content.length);
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
