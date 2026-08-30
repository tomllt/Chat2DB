package ai.chat2db.community.query.excel.domain.api.permission;

/**
 * Permission checker for query-excel operations.
 */
public interface QueryExcelPermissionChecker {

    boolean canViewDataset(Long userId, Long datasetId);

    boolean canEditDataset(Long userId, Long datasetId);

    boolean canPublishDataset(Long userId, Long datasetId);

    boolean canDeleteDataset(Long userId, Long datasetId);

    boolean canExecuteView(Long userId, Long viewId);

    boolean canExportTemplate(Long userId, Long templateId);

    boolean canAccessField(Long userId, String fieldId);

    boolean canAccessDatasource(Long userId, Long datasourceId);

    boolean canAccessWorkspace(Long userId, Long workspaceId);
}