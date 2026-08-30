package ai.chat2db.community.query.excel.web.api.controller;

import java.io.IOException;
import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ExcelReportTemplate;
import ai.chat2db.community.query.excel.domain.api.model.SheetConfig;
import ai.chat2db.community.query.excel.domain.api.service.IExcelReportTemplateService;
import ai.chat2db.community.tools.annotation.NotCliRuntime;
import ai.chat2db.community.tools.wrapper.result.ActionResult;
import ai.chat2db.community.tools.wrapper.result.DataResult;
import ai.chat2db.community.tools.wrapper.result.web.WebPageResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST endpoints for {@link ExcelReportTemplate} management (requirements §10.3).
 */
@RestController
@RequestMapping("/api")
@NotCliRuntime
public class ExcelReportTemplateController {

    private final IExcelReportTemplateService excelReportTemplateService;

    public ExcelReportTemplateController(IExcelReportTemplateService excelReportTemplateService) {
        this.excelReportTemplateService = excelReportTemplateService;
    }

    @GetMapping("/excel-report-templates")
    public WebPageResult<ExcelReportTemplate> list(@RequestParam(value = "workspaceId", required = false) Long workspaceId,
                                                   @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                   @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                                   @RequestParam(value = "searchKey", required = false) String searchKey) {
        PageResponse<ExcelReportTemplate> pageResult = excelReportTemplateService.list(workspaceId, pageNo, pageSize, searchKey);
        return WebPageResult.of(pageResult.getData(), pageResult.getTotal(), pageResult.getPageNo(),
                pageResult.getPageSize());
    }

    @PostMapping("/excel-report-templates")
    public DataResult<Long> create(@RequestBody ExcelReportTemplate template) {
        return DataResult.of(excelReportTemplateService.create(template));
    }

    @GetMapping("/excel-report-templates/{id}")
    public DataResult<ExcelReportTemplate> getById(@PathVariable("id") Long id) {
        return DataResult.of(excelReportTemplateService.getById(id));
    }

    @PutMapping("/excel-report-templates/{id}")
    public ActionResult update(@PathVariable("id") Long id, @RequestBody ExcelReportTemplate template) {
        template.setId(id);
        excelReportTemplateService.update(template);
        return ActionResult.isSuccess();
    }

    @DeleteMapping("/excel-report-templates/{id}")
    public DataResult<String> delete(@PathVariable("id") Long id) {
        excelReportTemplateService.delete(id);
        return DataResult.of("success");
    }

    @PostMapping("/excel-report-templates/{id}/validate")
    public DataResult<List<ErrorCode>> validate(@PathVariable("id") Long id) {
        return DataResult.of(excelReportTemplateService.validate(id));
    }

    @PostMapping("/excel-report-templates/{id}/copy")
    public DataResult<Long> copy(@PathVariable("id") Long id,
                                 @RequestParam(value = "name", required = false) String name) {
        return DataResult.of(excelReportTemplateService.copy(id, name));
    }

    @PostMapping("/excel-report-templates/upload")
    public DataResult<Long> upload(@RequestParam("workspaceId") Long workspaceId,
                                   @RequestParam("name") String name,
                                   @RequestParam(value = "description", required = false) String description,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam("queryViewId") Long queryViewId) throws IOException {
        return DataResult.of(excelReportTemplateService.upload(workspaceId, name, description,
                file.getBytes(), queryViewId));
    }

    @GetMapping("/excel-report-templates/{id}/sheet-names")
    public DataResult<List<String>> getSheetNames(@PathVariable("id") Long id) {
        return DataResult.of(excelReportTemplateService.getSheetNames(id));
    }

    @PutMapping("/excel-report-templates/{id}/sheet-configs")
    public ActionResult updateSheetConfigs(@PathVariable("id") Long id,
                                           @RequestBody List<SheetConfig> sheetConfigs) {
        excelReportTemplateService.updateSheetConfigs(id, sheetConfigs);
        return ActionResult.isSuccess();
    }

    @PutMapping("/excel-report-templates/{id}/field-bindings")
    public ActionResult updateFieldBindings(@PathVariable("id") Long id,
                                            @RequestParam("sheetName") String sheetName,
                                            @RequestBody List<ExcelColumnBinding> bindings) {
        excelReportTemplateService.updateFieldBindings(id, sheetName, bindings);
        return ActionResult.isSuccess();
    }
}