package ai.chat2db.community.query.excel.web.api.controller;

import java.util.List;

import com.alibaba.fastjson2.JSON;
import ai.chat2db.community.query.excel.domain.api.model.ExportResult;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.service.IExcelExportService;
import ai.chat2db.community.tools.annotation.NotCliRuntime;
import ai.chat2db.community.tools.wrapper.result.DataResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for Excel export (requirements §10.4).
 */
@RestController
@RequestMapping("/api")
@NotCliRuntime
public class ExcelExportController {

    private final IExcelExportService excelExportService;

    public ExcelExportController(IExcelExportService excelExportService) {
        this.excelExportService = excelExportService;
    }

    @PostMapping("/saved-query-views/{queryViewId}/export/excel")
    public DataResult<ExportResult> export(@PathVariable("queryViewId") Long queryViewId,
                                           @RequestParam("templateId") Long templateId,
                                           @RequestParam(value = "filterOverrides", required = false) String filterOverridesJson) {
        return DataResult.of(excelExportService.export(templateId, queryViewId,
                SavedQueryViewController.parseFilterOverrides(filterOverridesJson)));
    }

    @GetMapping("/excel-exports/{id}")
    public DataResult<ExportResult> getExportStatus(@PathVariable("id") Long id) {
        // The current service interface does not provide a get-by-id method for
        // export records. The export result is returned synchronously by the
        // export endpoint. This endpoint is reserved for future async support.
        return DataResult.empty();
    }

    @GetMapping("/excel-exports/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable("id") Long id,
                                           @RequestParam("token") String downloadToken) {
        byte[] content = excelExportService.download(downloadToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "report.xlsx");
        headers.setContentLength(content.length);
        return ResponseEntity.ok().headers(headers).body(content);
    }
}