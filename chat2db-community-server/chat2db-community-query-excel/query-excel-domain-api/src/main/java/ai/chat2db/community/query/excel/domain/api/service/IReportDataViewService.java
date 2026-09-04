package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.ReportDataViewPreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;

/**
 * Provides read-only data views for saved report versions.
 */
public interface IReportDataViewService {

    /**
     * Previews a workspace-owned report version using its immutable snapshots.
     * Runtime filters replace the version filter for the same field.
     */
    ReportDataViewPreviewResult preview(Long workspaceId, Long versionId, int pageNo, int pageSize,
                                       List<ViewFilter> runtimeFilters);
}
