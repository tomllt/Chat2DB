package ai.chat2db.community.query.excel.domain.core.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.query.excel.domain.api.model.ReportDataViewPreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.service.IReportDataViewService;
import ai.chat2db.community.query.excel.domain.api.service.ISavedQueryViewService;
import ai.chat2db.community.query.excel.storage.ReportBundleStorage;
import ai.chat2db.community.query.excel.storage.ReportBundleVersionStorage;
import org.springframework.stereotype.Service;

/** Version-backed, read-only report data view service. */
@Service
public class ReportDataViewServiceImpl implements IReportDataViewService {

    private final ReportBundleVersionStorage versionStorage;
    private final ReportBundleStorage bundleStorage;
    private final ISavedQueryViewService savedQueryViewService;

    public ReportDataViewServiceImpl() {
        this(ReportBundleVersionStorage.INSTANCE, ReportBundleStorage.INSTANCE, new SavedQueryViewServiceImpl());
    }

    ReportDataViewServiceImpl(ReportBundleVersionStorage versionStorage,
                              ReportBundleStorage bundleStorage,
                              ISavedQueryViewService savedQueryViewService) {
        this.versionStorage = versionStorage;
        this.bundleStorage = bundleStorage;
        this.savedQueryViewService = savedQueryViewService;
    }

    @Override
    public ReportDataViewPreviewResult preview(Long workspaceId, Long versionId, int pageNo, int pageSize,
                                               List<ViewFilter> runtimeFilters) {
        ReportBundleVersion version = requireVersion(workspaceId, versionId);
        ReportBundle bundle = requireBundle(workspaceId, version.getBundleId());
        SavedQueryView view = requireView(bundle.getQueryViewId());

        List<ViewFilter> effectiveFilters = mergeFilters(version.getPresetRowFiltersSnapshot(), runtimeFilters);
        PreviewResult preview = savedQueryViewService.preview(view.getId(), pageNo, pageSize, effectiveFilters);
        List<Map<String, Object>> rows = projectRows(preview, version.getBoundFieldsSnapshot());
        List<String> rowKeys = actualRowKeys(preview.getRows());

        return ReportDataViewPreviewResult.builder()
                .columns(columns(preview, version.getBoundFieldsSnapshot()))
                .rows(rows)
                .total(preview.getTotal())
                .pageNo(preview.getPageNo())
                .pageSize(preview.getPageSize())
                .rowKeys(rowKeys)
                .build();
    }

    private ReportBundleVersion requireVersion(Long workspaceId, Long versionId) {
        ReportBundleVersion version = versionStorage.getById(workspaceId, versionId);
        if (version == null || !Objects.equals(workspaceId, version.getWorkspaceId())) {
            throw notFound("Report bundle version");
        }
        return version;
    }

    private ReportBundle requireBundle(Long workspaceId, Long bundleId) {
        ReportBundle bundle = bundleStorage.getById(workspaceId, bundleId);
        if (bundle == null || !Objects.equals(workspaceId, bundle.getWorkspaceId())) {
            throw notFound("Report bundle");
        }
        return bundle;
    }

    private SavedQueryView requireView(Long viewId) {
        SavedQueryView view = savedQueryViewService.getById(viewId);
        if (view == null) {
            throw notFound("Saved query view");
        }
        return view;
    }

    private static List<ViewFilter> mergeFilters(List<ViewFilter> presetFilters, List<ViewFilter> runtimeFilters) {
        Map<String, ViewFilter> merged = new LinkedHashMap<>();
        copyFilters(presetFilters).forEach(filter -> merged.put(filter.getFieldId(), filter));
        copyFilters(runtimeFilters).forEach(filter -> merged.put(filter.getFieldId(), filter));
        return new ArrayList<>(merged.values());
    }

    private static List<Map<String, Object>> projectRows(PreviewResult preview, List<ExcelColumnBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return new ArrayList<>(preview.getRows());
        }
        return preview.getRows().stream().map(row -> {
            Map<String, Object> projected = new LinkedHashMap<>();
            for (ExcelColumnBinding binding : bindings) {
                if (binding == null || binding.getTargetColumn() == null) {
                    continue;
                }
                String sourceName = binding.getDisplayName() != null
                        ? binding.getDisplayName() : binding.getQueryFieldId();
                projected.put(binding.getTargetColumn(), row.get(sourceName));
            }
            return projected;
        }).collect(Collectors.toList());
    }

    private static List<String> columns(PreviewResult preview, List<ExcelColumnBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return new ArrayList<>(preview.getColumns());
        }
        return bindings.stream().filter(Objects::nonNull).map(ExcelColumnBinding::getTargetColumn)
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static List<String> actualRowKeys(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .map(row -> row == null ? null : row.get("__row_key"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList());
    }

    private static List<ViewFilter> copyFilters(List<ViewFilter> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream().filter(Objects::nonNull).filter(filter -> filter.getFieldId() != null)
                .map(ReportDataViewServiceImpl::copyFilter).collect(Collectors.toList());
    }

    private static ViewFilter copyFilter(ViewFilter source) {
        ViewFilter copy = new ViewFilter();
        copy.setFieldId(source.getFieldId());
        copy.setFilterType(source.getFilterType());
        copy.setOperator(source.getOperator());
        copy.setValue(source.getValue());
        copy.setValues(source.getValues() == null ? null : new ArrayList<>(source.getValues()));
        return copy;
    }

    private static QueryExcelException notFound(String resource) {
        return new QueryExcelException(ErrorCode.QV_NOT_FOUND.getCode(), resource + " not found");
    }
}
