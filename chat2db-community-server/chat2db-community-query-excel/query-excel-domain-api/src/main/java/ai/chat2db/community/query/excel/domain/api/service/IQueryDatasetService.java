package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.PreviewResult;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryExcelException;

/**
 * Domain service for managing {@link QueryDataset} definitions (requirements §5.1/§5.4).
 */
public interface IQueryDatasetService {

    /**
     * Lists datasets, optionally filtered by workspace and search key, paginated.
     */
    PageResponse<QueryDataset> list(Long workspaceId, int pageNo, int pageSize, String searchKey);

    /**
     * Returns a dataset by id, or {@code null} when it does not exist.
     */
    QueryDataset getById(Long id);

    /**
     * Creates a new draft dataset; returns the generated id.
     */
    Long create(QueryDataset dataset);

    /**
     * Updates an existing dataset, enforcing optimistic locking.
     */
    void update(QueryDataset dataset);

    /**
     * Deletes a dataset by id.
     */
    void delete(Long id);

    /**
     * Validates a dataset; returns collected error codes (empty list = valid).
     */
    List<ErrorCode> validate(Long id);

    /**
     * Publishes a valid dataset: status PUBLISHED, version increment, source schema hash.
     */
    void publish(Long id);

    /**
     * Disables a published dataset.
     */
    void disable(Long id);

    /**
     * Deep-copies a dataset into a new draft; returns the new id.
     */
    Long copy(Long id, String newName);

    /**
     * Previews dataset rows; SQL generation lands in T8, shape returned here.
     */
    PreviewResult preview(Long id, int pageNo, int pageSize);

    /**
     * Checks whether the source table structure has changed since the dataset
     * was last published. Recomputes the source schema hash from the current
     * metadata and compares it with the stored hash.
     * <p>If the hash has changed, the stored hash is updated so subsequent
     * checks are stable (callers should re-validate downstream views).</p>
     *
     * @param datasetId the dataset to check
     * @return {@code true} when the source structure has changed
     * @throws QueryExcelException with DS_NOT_FOUND when the dataset does not exist
     */
    boolean checkSourceChanged(Long datasetId);
}