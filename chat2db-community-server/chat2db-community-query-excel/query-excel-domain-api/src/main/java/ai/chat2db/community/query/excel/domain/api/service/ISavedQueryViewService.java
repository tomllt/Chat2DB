package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryResult;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.ViewFilter;

/**
 * Domain service for managing {@link SavedQueryView} definitions (requirements §6.1-6.4, 6.6).
 */
public interface ISavedQueryViewService {

    /**
     * Lists saved query views, optionally filtered by workspace and search key, paginated.
     */
    PageResponse<SavedQueryView> list(Long workspaceId, int pageNo, int pageSize, String searchKey);

    /**
     * Returns a saved query view by id, or {@code null} when it does not exist.
     */
    SavedQueryView getById(Long id);

    /**
     * Creates a new draft saved query view; returns the generated id.
     */
    Long create(SavedQueryView view);

    /**
     * Updates an existing saved query view, enforcing optimistic locking.
     */
    void update(SavedQueryView view);

    /**
     * Deletes a saved query view by id.
     */
    void delete(Long id);

    /**
     * Validates a saved query view; returns collected error codes (empty list = valid).
     */
    List<ErrorCode> validate(Long id);

    /**
     * Publishes a valid saved query view: status PUBLISHED, version increment.
     */
    void publish(Long id);

    /**
     * Disables a published saved query view.
     */
    void disable(Long id);

    /**
     * Checks whether the view is still compatible with the current dataset
     * version. When the referenced dataset no longer exists, or the dataset
     * version changed and a field referenced by the view was removed, the view
     * is marked {@code INVALID} and persisted.
     *
     * @return {@code true} when the view remains compatible
     */
    boolean checkCompatibility(Long viewId);

    /**
     * Deep-copies a saved query view into a new draft; returns the new id.
     */
    Long copy(Long id, String newName);

    /**
     * Previews saved query view results with optional filter overrides.
     */
    PreviewResult preview(Long id, int pageNo, int pageSize, List<ViewFilter> filterOverrides);

    /**
     * Executes the full saved query view query without pagination; returns all rows.
     */
    QueryResult executeQuery(Long id, List<ViewFilter> filterOverrides);
}