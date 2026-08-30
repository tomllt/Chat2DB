package ai.chat2db.community.query.excel.web.api.controller;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.service.IQueryDatasetService;
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

/**
 * REST endpoints for {@link QueryDataset} management (requirements §10.1).
 */
@RestController
@RequestMapping("/api")
@NotCliRuntime
public class QueryDatasetController {

    private final IQueryDatasetService queryDatasetService;

    public QueryDatasetController(IQueryDatasetService queryDatasetService) {
        this.queryDatasetService = queryDatasetService;
    }

    @GetMapping("/query-datasets")
    public WebPageResult<QueryDataset> list(@RequestParam(value = "workspaceId", required = false) Long workspaceId,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                            @RequestParam(value = "searchKey", required = false) String searchKey) {
        PageResponse<QueryDataset> pageResult = queryDatasetService.list(workspaceId, pageNo, pageSize, searchKey);
        return WebPageResult.of(pageResult.getData(), pageResult.getTotal(), pageResult.getPageNo(),
                pageResult.getPageSize());
    }

    @PostMapping("/query-datasets")
    public DataResult<Long> create(@RequestBody QueryDataset dataset) {
        return DataResult.of(queryDatasetService.create(dataset));
    }

    @GetMapping("/query-datasets/{id}")
    public DataResult<QueryDataset> getById(@PathVariable("id") Long id) {
        return DataResult.of(queryDatasetService.getById(id));
    }

    @PutMapping("/query-datasets/{id}")
    public ActionResult update(@PathVariable("id") Long id, @RequestBody QueryDataset dataset) {
        dataset.setId(id);
        queryDatasetService.update(dataset);
        return ActionResult.isSuccess();
    }

    @DeleteMapping("/query-datasets/{id}")
    public DataResult<String> delete(@PathVariable("id") Long id) {
        queryDatasetService.delete(id);
        return DataResult.of("success");
    }

    @PostMapping("/query-datasets/{id}/validate")
    public DataResult<List<ErrorCode>> validate(@PathVariable("id") Long id) {
        return DataResult.of(queryDatasetService.validate(id));
    }

    @PostMapping("/query-datasets/{id}/publish")
    public ActionResult publish(@PathVariable("id") Long id) {
        queryDatasetService.publish(id);
        return ActionResult.isSuccess();
    }

    @PostMapping("/query-datasets/{id}/disable")
    public ActionResult disable(@PathVariable("id") Long id) {
        queryDatasetService.disable(id);
        return ActionResult.isSuccess();
    }

    @PostMapping("/query-datasets/{id}/copy")
    public DataResult<Long> copy(@PathVariable("id") Long id,
                                 @RequestParam(value = "name", required = false) String name) {
        return DataResult.of(queryDatasetService.copy(id, name));
    }

    @GetMapping("/query-datasets/{id}/preview")
    public DataResult<PreviewResult> preview(@PathVariable("id") Long id,
                                             @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                             @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return DataResult.of(queryDatasetService.preview(id, pageNo, pageSize));
    }
}