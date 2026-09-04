package ai.chat2db.community.query.excel.storage;

import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.storage.small.SmallDataStorage;

import java.util.List;
import java.util.Objects;

public class ReportBundleVersionStorage extends SmallDataStorage<ReportBundleVersion> {

    public static final ReportBundleVersionStorage INSTANCE = new ReportBundleVersionStorage();

    protected ReportBundleVersionStorage() {
        super("report-bundle-version", ReportBundleVersion.class);
    }

    public List<ReportBundleVersion> getDataList(Long workspaceId) {
        return dataMap.values().stream()
                .filter(version -> Objects.equals(workspaceId, version.getWorkspaceId()))
                .map(this::copy)
                .toList();
    }

    public List<ReportBundleVersion> queryByWorkspaceIdAndBundleId(Long workspaceId, Long bundleId) {
        return dataMap.values().stream()
                .filter(version -> Objects.equals(workspaceId, version.getWorkspaceId())
                        && Objects.equals(bundleId, version.getBundleId()))
                .map(this::copy)
                .toList();
    }

    public ReportBundleVersion getById(Long workspaceId, Long id) {
        ReportBundleVersion version = dataMap.get(id);
        return version != null && Objects.equals(workspaceId, version.getWorkspaceId()) ? copy(version) : null;
    }

    public Long save(ReportBundleVersion version) {
        return version == null || version.getWorkspaceId() == null || version.getBundleId() == null
                ? null : super.save(copy(version));
    }

    public void update(Long workspaceId, ReportBundleVersion update) {
        if (update == null || update.getId() == null) {
            return;
        }
        ReportBundleVersion current = dataMap.get(update.getId());
        if (current == null || !Objects.equals(workspaceId, current.getWorkspaceId())
                || !Objects.equals(workspaceId, update.getWorkspaceId())
                || !Objects.equals(current.getBundleId(), update.getBundleId())) {
            return;
        }
        super.update(copy(update));
    }

    public void delete(Long workspaceId, Long id) {
        ReportBundleVersion current = dataMap.get(id);
        if (current != null && Objects.equals(workspaceId, current.getWorkspaceId())) {
            super.delete(id);
        }
    }

    @Override
    public ReportBundleVersion getById(Long id) {
        throw new UnsupportedOperationException("workspaceId is required for report bundle version access");
    }

    @Override
    public List<ReportBundleVersion> getDataList() {
        throw new UnsupportedOperationException("workspaceId is required for report bundle version access");
    }

    @Override
    protected synchronized void saveDataList() {
        super.saveDataList(dataMap.values().stream().map(this::copy).toList());
    }

    @Override
    public synchronized void update(ReportBundleVersion update) {
        throw new UnsupportedOperationException("workspaceId is required for report bundle version access");
    }

    @Override
    public synchronized void delete(Long id) {
        throw new UnsupportedOperationException("workspaceId is required for report bundle version access");
    }

    private ReportBundleVersion copy(ReportBundleVersion source) {
        if (source == null) {
            return null;
        }
        ReportBundleVersion target = new ReportBundleVersion();
        target.setId(source.getId());
        target.setWorkspaceId(source.getWorkspaceId());
        target.setBundleId(source.getBundleId());
        target.setVersionName(source.getVersionName());
        target.setVersionNo(source.getVersionNo());
        target.setBoundFieldsSnapshot(ReportBundleStorage.copyBindings(source.getBoundFieldsSnapshot()));
        target.setPresetRowFiltersSnapshot(ReportBundleStorage.copyFilters(source.getPresetRowFiltersSnapshot()));
        target.setRowFilter(ReportBundleStorage.copyFilters(source.getRowFilter()));
        target.setSelectedRowKeys(source.getSelectedRowKeys() == null ? null : source.getSelectedRowKeys().stream()
                .filter(Objects::nonNull)
                .toList());
        target.setOwnerId(source.getOwnerId());
        target.setGmtCreate(ReportBundleStorage.copyDate(source.getGmtCreate()));
        target.setGmtModified(ReportBundleStorage.copyDate(source.getGmtModified()));
        return target;
    }
}
