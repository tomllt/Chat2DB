package ai.chat2db.community.query.excel.storage;

import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.storage.small.SmallDataStorage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class ReportBundleStorage extends SmallDataStorage<ReportBundle> {

    public static final ReportBundleStorage INSTANCE = new ReportBundleStorage();

    protected ReportBundleStorage() {
        super("report-bundle", ReportBundle.class);
    }

    public List<ReportBundle> getDataList(Long workspaceId) {
        return dataMap.values().stream()
                .filter(bundle -> Objects.equals(workspaceId, bundle.getWorkspaceId()))
                .map(this::copy)
                .toList();
    }

    public ReportBundle getById(Long workspaceId, Long id) {
        ReportBundle bundle = dataMap.get(id);
        return bundle != null && Objects.equals(workspaceId, bundle.getWorkspaceId()) ? copy(bundle) : null;
    }

    public Long save(ReportBundle bundle) {
        return bundle == null || bundle.getWorkspaceId() == null ? null : super.save(copy(bundle));
    }

    public void update(Long workspaceId, ReportBundle update) {
        if (update == null || update.getId() == null) {
            return;
        }
        ReportBundle current = dataMap.get(update.getId());
        if (current == null || !Objects.equals(workspaceId, current.getWorkspaceId())
                || !Objects.equals(workspaceId, update.getWorkspaceId())) {
            return;
        }
        super.update(copy(update));
    }

    public void delete(Long workspaceId, Long id) {
        ReportBundle current = dataMap.get(id);
        if (current != null && Objects.equals(workspaceId, current.getWorkspaceId())) {
            super.delete(id);
        }
    }

    @Override
    public ReportBundle getById(Long id) {
        throw new UnsupportedOperationException("workspaceId is required for report bundle access");
    }

    @Override
    public List<ReportBundle> getDataList() {
        throw new UnsupportedOperationException("workspaceId is required for report bundle access");
    }

    @Override
    protected synchronized void saveDataList() {
        super.saveDataList(dataMap.values().stream().map(this::copy).toList());
    }

    @Override
    public synchronized void update(ReportBundle update) {
        throw new UnsupportedOperationException("workspaceId is required for report bundle access");
    }

    @Override
    public synchronized void delete(Long id) {
        throw new UnsupportedOperationException("workspaceId is required for report bundle access");
    }

    private ReportBundle copy(ReportBundle source) {
        if (source == null) {
            return null;
        }
        ReportBundle target = new ReportBundle();
        target.setId(source.getId());
        target.setWorkspaceId(source.getWorkspaceId());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setQueryViewId(source.getQueryViewId());
        target.setBoundFields(copyBindings(source.getBoundFields()));
        target.setPresetRowFilters(copyFilters(source.getPresetRowFilters()));
        target.setActiveVersionId(source.getActiveVersionId());
        target.setOwnerId(source.getOwnerId());
        target.setGmtCreate(copyDate(source.getGmtCreate()));
        target.setGmtModified(copyDate(source.getGmtModified()));
        return target;
    }

    static List<ExcelColumnBinding> copyBindings(List<ExcelColumnBinding> source) {
        if (source == null) {
            return null;
        }
        List<ExcelColumnBinding> result = new ArrayList<>();
        for (ExcelColumnBinding binding : source) {
            if (binding == null) {
                continue;
            }
            ExcelColumnBinding target = new ExcelColumnBinding();
            target.setQueryFieldId(binding.getQueryFieldId());
            target.setTargetColumn(binding.getTargetColumn());
            target.setDisplayName(binding.getDisplayName());
            target.setNumberFormat(binding.getNumberFormat());
            target.setNullDisplay(binding.getNullDisplay());
            target.setAlignment(binding.getAlignment());
            target.setExportEnabled(binding.getExportEnabled());
            result.add(target);
        }
        return result;
    }

    static List<ViewFilter> copyFilters(List<ViewFilter> source) {
        if (source == null) {
            return null;
        }
        List<ViewFilter> result = new ArrayList<>();
        for (ViewFilter filter : source) {
            if (filter == null) {
                continue;
            }
            ViewFilter target = new ViewFilter();
            target.setFieldId(filter.getFieldId());
            target.setFilterType(filter.getFilterType());
            target.setOperator(filter.getOperator());
            target.setValue(filter.getValue());
            target.setValues(filter.getValues() == null ? null : new ArrayList<>(filter.getValues()));
            result.add(target);
        }
        return result;
    }

    static Date copyDate(Date source) {
        return source == null ? null : new Date(source.getTime());
    }
}
