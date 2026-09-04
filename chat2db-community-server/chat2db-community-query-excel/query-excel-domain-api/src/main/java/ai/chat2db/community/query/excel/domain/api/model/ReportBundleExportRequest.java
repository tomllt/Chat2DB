package ai.chat2db.community.query.excel.domain.api.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/** Immutable inputs for exporting one saved report-bundle version. */
@Data
@Builder
public class ReportBundleExportRequest {

    private Long templateId;

    private Long queryViewId;

    private List<ExcelColumnBinding> boundFieldsSnapshot;

    private List<ViewFilter> presetRowFiltersSnapshot;

    private List<ViewFilter> rowFilter;

    private List<String> selectedRowKeys;

    private List<ViewFilter> runtimeFilters;
}
