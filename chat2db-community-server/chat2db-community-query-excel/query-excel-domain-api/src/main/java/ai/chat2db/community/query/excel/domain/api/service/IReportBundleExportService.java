package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.query.excel.domain.api.model.ExportResult;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleExportRequest;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;

/** Exports an immutable report-bundle version without changing legacy export APIs. */
public interface IReportBundleExportService {

    ExportResult export(Long workspaceId, Long bundleId, Long versionId, Long templateId,
                        List<ViewFilter> runtimeFilters);

    ExportResult exportSnapshot(Long workspaceId, Long bundleId, Long versionId,
                                ReportBundleExportRequest request);

    /**
     * Returns the stored rendered .xlsx bytes for a download token previously
     * issued by {@link #exportSnapshot(Long, Long, Long, ReportBundleExportRequest)}.
     *
     * @param downloadToken token from a successful export snapshot result
     * @return the rendered .xlsx file bytes
     */
    byte[] download(Long workspaceId, String downloadToken);
}
