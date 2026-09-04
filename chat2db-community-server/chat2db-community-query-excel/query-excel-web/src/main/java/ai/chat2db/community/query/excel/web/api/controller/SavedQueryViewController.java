package ai.chat2db.community.query.excel.web.api.controller;

import java.util.List;

import com.alibaba.fastjson2.JSON;
import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;

import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
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
 * REST endpoints for {@link SavedQueryView} management (requirements §10.2).
 */
@RestController
@RequestMapping("/api")
@NotCliRuntime
public class SavedQueryViewController {

    private final ISavedQueryViewService savedQueryViewService;

    public SavedQueryViewController(ISavedQueryViewService savedQueryViewService) {
        this.savedQueryViewService = savedQueryViewService;
    }

    @GetMapping("/saved-query-views")
    public WebPageResult<SavedQueryView> list(@RequestParam(value = "workspaceId", required = false) Long workspaceId,
                                              @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                              @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                              @RequestParam(value = "searchKey", required = false) String searchKey) {
        PageResponse<SavedQueryView> pageResult = savedQueryViewService.list(workspaceId, pageNo, pageSize, searchKey);
        return WebPageResult.of(pageResult.getData(), pageResult.getTotal(), pageResult.getPageNo(),
                pageResult.getPageSize());
    }

    @PostMapping("/saved-query-views")
    public DataResult<Long> create(@RequestBody SavedQueryView view) {
        return DataResult.of(savedQueryViewService.create(view));
    }

    @GetMapping("/saved-query-views/{id}")
    public DataResult<SavedQueryView> getById(@PathVariable("id") Long id) {
        return DataResult.of(savedQueryViewService.getById(id));
    }

    @PutMapping("/saved-query-views/{id}")
    public ActionResult update(@PathVariable("id") Long id, @RequestBody SavedQueryView view) {
        view.setId(id);
        savedQueryViewService.update(view);
        return ActionResult.isSuccess();
    }

    @DeleteMapping("/saved-query-views/{id}")
    public DataResult<String> delete(@PathVariable("id") Long id) {
        savedQueryViewService.delete(id);
        return DataResult.of("success");
    }

    @PostMapping("/saved-query-views/{id}/validate")
    public DataResult<List<ErrorCode>> validate(@PathVariable("id") Long id) {
        return DataResult.of(savedQueryViewService.validate(id));
    }

    @PostMapping("/saved-query-views/{id}/publish")
    public ActionResult publish(@PathVariable("id") Long id) {
        savedQueryViewService.publish(id);
        return ActionResult.isSuccess();
    }

    @PostMapping("/saved-query-views/{id}/disable")
    public ActionResult disable(@PathVariable("id") Long id) {
        savedQueryViewService.disable(id);
        return ActionResult.isSuccess();
    }

    @PostMapping("/saved-query-views/{id}/copy")
    public DataResult<Long> copy(@PathVariable("id") Long id,
                                 @RequestParam(value = "name", required = false) String name) {
        return DataResult.of(savedQueryViewService.copy(id, name));
    }

    @GetMapping("/saved-query-views/{id}/preview")
    public DataResult<PreviewResult> preview(@PathVariable("id") Long id,
                                             @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                             @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                             @RequestParam(value = "filterOverrides", required = false) String filterOverridesJson) {
        return DataResult.of(savedQueryViewService.preview(id, pageNo, pageSize, parseFilterOverrides(filterOverridesJson)));
    }

    /**
     * Parses a JSON-encoded array of ViewFilter from the request parameter.
     * The frontend sends {@code JSON.stringify(filters)} as a single query param.
     */
    static List<ViewFilter> parseFilterOverrides(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.parseArray(json, ViewFilter.class);
        } catch (RuntimeException ex) {
            throw new QueryExcelException(ErrorCode.EX_INVALID_FILTER_OVERRIDES.getCode(),
                    ErrorCode.EX_INVALID_FILTER_OVERRIDES.getMessage());
        }
    }
}