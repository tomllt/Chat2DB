package ai.chat2db.community.query.excel.domain.api.permission;

import org.springframework.stereotype.Component;

/**
 * Single-user (Community mode) permission checker.
 *
 * <p>In the current Community edition every user is effectively an owner,
 * so all permission checks return {@code true}. When multi-user support is
 * added, replace the body of each method with a real lookup against the
 * owner of the resource identified by the {@code *Id} parameter.</p>
 */
@Component
public class LocalPermissionChecker implements QueryExcelPermissionChecker {

    /** Singleton instance. */
    public static final LocalPermissionChecker INSTANCE = new LocalPermissionChecker();

    /**
     * Package-private constructor; use {@link #INSTANCE}.
     */
    LocalPermissionChecker() {
    }

    @Override
    public boolean canViewDataset(Long userId, Long datasetId) {
        // TODO: implement real permission checks when multi-user mode is added
        return true;
    }

    @Override
    public boolean canEditDataset(Long userId, Long datasetId) {
        return true;
    }

    @Override
    public boolean canPublishDataset(Long userId, Long datasetId) {
        return true;
    }

    @Override
    public boolean canDeleteDataset(Long userId, Long datasetId) {
        return true;
    }

    @Override
    public boolean canExecuteView(Long userId, Long viewId) {
        return true;
    }

    @Override
    public boolean canExportTemplate(Long userId, Long templateId) {
        return true;
    }

    @Override
    public boolean canAccessField(Long userId, String fieldId) {
        return true;
    }

    @Override
    public boolean canAccessDatasource(Long userId, Long datasourceId) {
        return true;
    }

    @Override
    public boolean canAccessWorkspace(Long userId, Long workspaceId) {
        return true;
    }
}