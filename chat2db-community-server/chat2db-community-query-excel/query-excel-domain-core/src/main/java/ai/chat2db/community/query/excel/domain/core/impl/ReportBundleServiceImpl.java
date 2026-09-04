package ai.chat2db.community.query.excel.domain.core.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.ExcelColumnBinding;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundle;
import ai.chat2db.community.query.excel.domain.api.model.ReportBundleVersion;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;
import ai.chat2db.community.query.excel.domain.api.service.IReportBundleService;
import ai.chat2db.community.query.excel.storage.ReportBundleStorage;
import ai.chat2db.community.query.excel.storage.ReportBundleVersionStorage;
import org.springframework.stereotype.Service;

/** File-backed report bundle and immutable version service. */
@Service
public class ReportBundleServiceImpl implements IReportBundleService {

    private final ReportBundleStorage bundleStorage;
    private final ReportBundleVersionStorage versionStorage;
    private final ReentrantLock versionCreationLock = new ReentrantLock();

    public ReportBundleServiceImpl() {
        this(ReportBundleStorage.INSTANCE, ReportBundleVersionStorage.INSTANCE);
    }

    ReportBundleServiceImpl(ReportBundleStorage bundleStorage, ReportBundleVersionStorage versionStorage) {
        this.bundleStorage = bundleStorage;
        this.versionStorage = versionStorage;
    }

    @Override
    public PageResponse<ReportBundle> list(Long workspaceId, int pageNo, int pageSize, String searchKey) {
        int safePage = pageNo <= 0 ? 1 : pageNo;
        int safeSize = pageSize <= 0 ? 10 : pageSize;
        List<ReportBundle> matches = bundleStorage.getDataList(workspaceId).stream()
                .filter(bundle -> searchKey == null || searchKey.isBlank()
                        || bundle.getName() != null && bundle.getName().toLowerCase(Locale.ROOT).contains(searchKey.toLowerCase(Locale.ROOT)))
                .toList();
        int from = Math.min((safePage - 1) * safeSize, matches.size());
        int to = Math.min(from + safeSize, matches.size());
        return PageResponse.of(new ArrayList<>(matches.subList(from, to)), (long) matches.size(), safePage, safeSize);
    }

    @Override
    public ReportBundle getById(Long workspaceId, Long bundleId) {
        return requireBundle(workspaceId, bundleId);
    }

    @Override
    public Long create(ReportBundle bundle) {
        if (bundle == null || bundle.getWorkspaceId() == null || isBlank(bundle.getName())) {
            throw invalidVersion("Bundle workspace and name are required");
        }
        bundle.setName(bundle.getName().trim());
        Date now = new Date();
        bundle.setGmtCreate(now);
        bundle.setGmtModified(now);
        return bundleStorage.save(bundle);
    }

    @Override
    public void update(Long workspaceId, ReportBundle bundle) {
        requireBundle(workspaceId, bundle == null ? null : bundle.getId());
        if (isBlank(bundle.getName())) {
            throw invalidVersion("Bundle name is required");
        }
        bundle.setName(bundle.getName().trim());
        bundle.setGmtModified(new Date());
        bundleStorage.update(workspaceId, bundle);
    }

    @Override
    public void delete(Long workspaceId, Long bundleId) {
        requireBundle(workspaceId, bundleId);
        bundleStorage.delete(workspaceId, bundleId);
    }

    @Override
    public List<ReportBundleVersion> listVersions(Long workspaceId, Long bundleId) {
        requireBundle(workspaceId, bundleId);
        return versionStorage.queryByWorkspaceIdAndBundleId(workspaceId, bundleId);
    }

    @Override
    public ReportBundleVersion getVersion(Long workspaceId, Long bundleId, Long versionId) {
        requireBundle(workspaceId, bundleId);
        ReportBundleVersion version = versionStorage.getById(workspaceId, versionId);
        if (version == null || !Objects.equals(bundleId, version.getBundleId())) {
            throw invalidVersion("Report version is invalid");
        }
        return version;
    }

    @Override
    public ReportBundleVersion saveAsNewVersion(Long workspaceId, Long bundleId, String versionName,
                                                 List<ExcelColumnBinding> boundFieldsSnapshot,
                                                 List<ViewFilter> presetRowFiltersSnapshot,
                                                 List<ViewFilter> rowFilter,
                                                 List<String> selectedRowKeys) {
        ReportBundle bundle = requireBundle(workspaceId, bundleId);
        if (isBlank(versionName)) {
            throw invalidVersion("Report version name is required");
        }
        versionCreationLock.lock();
        try {
            List<ReportBundleVersion> existing = versionStorage.queryByWorkspaceIdAndBundleId(workspaceId, bundleId);
            String normalizedName = versionName.trim();
            if (existing.stream().anyMatch(version -> normalizedName.equals(version.getVersionName()))) {
                throw new QueryExcelException(ErrorCode.EX_REPORT_VERSION_DUPLICATE.getCode(),
                        ErrorCode.EX_REPORT_VERSION_DUPLICATE.getMessage());
            }
            int nextNo = existing.stream().map(ReportBundleVersion::getVersionNo).filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).max().orElse(0) + 1;
            ReportBundleVersion version = new ReportBundleVersion();
            version.setWorkspaceId(workspaceId);
            version.setBundleId(bundleId);
            version.setVersionName(normalizedName);
            version.setVersionNo(nextNo);
            version.setBoundFieldsSnapshot(copyBindings(boundFieldsSnapshot == null ? bundle.getBoundFields() : boundFieldsSnapshot));
            version.setPresetRowFiltersSnapshot(copyFilters(presetRowFiltersSnapshot == null
                    ? bundle.getPresetRowFilters() : presetRowFiltersSnapshot));
            version.setRowFilter(copyFilters(rowFilter));
            version.setSelectedRowKeys(selectedRowKeys == null ? null : new ArrayList<>(selectedRowKeys));
            Date now = new Date();
            version.setGmtCreate(now);
            version.setGmtModified(now);
            Long versionId = versionStorage.save(version);
            version.setId(versionId);
            ReportBundle active = copyBundle(bundle);
            active.setActiveVersionId(versionId);
            active.setGmtModified(new Date());
            bundleStorage.update(workspaceId, active);
            return version;
        } finally {
            versionCreationLock.unlock();
        }
    }

    @Override
    public void updatePresetFilters(Long workspaceId, Long bundleId, List<ViewFilter> presetRowFilters) {
        ReportBundle bundle = requireBundle(workspaceId, bundleId);
        ReportBundle update = copyBundle(bundle);
        update.setPresetRowFilters(copyFilters(presetRowFilters));
        update.setGmtModified(new Date());
        bundleStorage.update(workspaceId, update);
    }

    @Override
    public void deleteVersion(Long workspaceId, Long bundleId, Long versionId) {
        getVersion(workspaceId, bundleId, versionId);
        versionStorage.delete(workspaceId, versionId);
    }

    private ReportBundle requireBundle(Long workspaceId, Long bundleId) {
        ReportBundle bundle = bundleStorage.getById(workspaceId, bundleId);
        if (bundle == null) {
            throw new QueryExcelException(ErrorCode.EX_REPORT_BUNDLE_NOT_FOUND.getCode(),
                    ErrorCode.EX_REPORT_BUNDLE_NOT_FOUND.getMessage());
        }
        return bundle;
    }

    private static QueryExcelException invalidVersion(String message) {
        return new QueryExcelException(ErrorCode.EX_REPORT_VERSION_INVALID.getCode(), message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<ExcelColumnBinding> copyBindings(List<ExcelColumnBinding> source) {
        if (source == null) return null;
        return source.stream().filter(Objects::nonNull).map(binding -> {
            ExcelColumnBinding copy = new ExcelColumnBinding();
            copy.setQueryFieldId(binding.getQueryFieldId());
            copy.setTargetColumn(binding.getTargetColumn());
            copy.setDisplayName(binding.getDisplayName());
            copy.setNumberFormat(binding.getNumberFormat());
            copy.setNullDisplay(binding.getNullDisplay());
            copy.setAlignment(binding.getAlignment());
            copy.setExportEnabled(binding.getExportEnabled());
            return copy;
        }).collect(Collectors.toList());
    }

    private static List<ViewFilter> copyFilters(List<ViewFilter> source) {
        if (source == null) return null;
        return source.stream().filter(Objects::nonNull).map(filter -> {
            ViewFilter copy = new ViewFilter();
            copy.setFieldId(filter.getFieldId());
            copy.setFilterType(filter.getFilterType());
            copy.setOperator(filter.getOperator());
            copy.setValue(filter.getValue());
            copy.setValues(filter.getValues() == null ? null : new ArrayList<>(filter.getValues()));
            return copy;
        }).collect(Collectors.toList());
    }

    private record BundleKey(Long workspaceId, Long bundleId) {
    }

    private static ReportBundle copyBundle(ReportBundle source) {
        ReportBundle copy = new ReportBundle();
        copy.setId(source.getId());
        copy.setWorkspaceId(source.getWorkspaceId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setQueryViewId(source.getQueryViewId());
        copy.setBoundFields(copyBindings(source.getBoundFields()));
        copy.setPresetRowFilters(copyFilters(source.getPresetRowFilters()));
        copy.setActiveVersionId(source.getActiveVersionId());
        copy.setOwnerId(source.getOwnerId());
        copy.setGmtCreate(source.getGmtCreate());
        copy.setGmtModified(source.getGmtModified());
        return copy;
    }
}
