package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;

public interface IReportBundleService {

    PageResponse<ReportBundle> list(Long workspaceId, int pageNo, int pageSize, String searchKey);

    ReportBundle getById(Long workspaceId, Long bundleId);

    Long create(ReportBundle bundle);

    void update(Long workspaceId, ReportBundle bundle);

    void delete(Long workspaceId, Long bundleId);

    List<ReportBundleVersion> listVersions(Long workspaceId, Long bundleId);

    ReportBundleVersion getVersion(Long workspaceId, Long bundleId, Long versionId);

    ReportBundleVersion saveAsNewVersion(Long workspaceId, Long bundleId, String versionName,
                                         List<ExcelColumnBinding> boundFieldsSnapshot,
                                         List<ViewFilter> presetRowFiltersSnapshot,
                                         List<ViewFilter> rowFilter,
                                         List<String> selectedRowKeys);

    void updatePresetFilters(Long workspaceId, Long bundleId, List<ViewFilter> presetRowFilters);

    void deleteVersion(Long workspaceId, Long bundleId, Long versionId);
}
