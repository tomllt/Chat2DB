package ai.chat2db.community.query.excel.domain.api.service;

import java.util.List;

import ai.chat2db.community.query.excel.domain.api.ErrorCode;
import ai.chat2db.community.query.excel.domain.api.model.DatasetFilter;
import ai.chat2db.community.query.excel.domain.api.model.QueryDataset;
import ai.chat2db.community.query.excel.domain.api.model.QueryDatasetField;
import ai.chat2db.community.query.excel.domain.api.model.SavedQueryView;
import ai.chat2db.community.query.excel.domain.api.model.ViewSort;

/**
 * Validation service for {@link QueryDataset} definitions (requirements §5.4/§5.6)
 * and {@link SavedQueryView} definitions (requirements §6.4).
 * <p>Encapsulates all business rules that govern whether a dataset is structurally
 * sound and safe to publish, and whether a saved query view is valid for preview
 * or execution. Callers obtain a full list of {@link ErrorCode} violations by
 * calling {@link #validateDatasetForPublish(QueryDataset)} or
 * {@link #validateView(SavedQueryView)}; individual rules are exposed as discrete
 * methods for fine-grained checks.</p>
 */
public interface IQueryValidationService {

    /**
     * Runs every validation rule against the given dataset and returns the
     * collected error codes. An empty list means the dataset is publishable.
     */
    List<ErrorCode> validateDatasetForPublish(QueryDataset dataset);

    /**
     * Returns {@code true} when the field's aggregation is compatible with its
     * actual column data type (e.g. text fields reject SUM/AVG).
     */
    boolean validateField(QueryDatasetField field, String actualColumnType);

    /**
     * Validates that every filter references a field that exists in the available
     * fields list and is marked filterable.
     */
    List<ErrorCode> validateFilters(List<DatasetFilter> filters, List<QueryDatasetField> availableFields);

    /**
     * Validates that every sort entry references a field that exists in the available
     * fields list and is marked sortable.
     */
    List<ErrorCode> validateSort(List<ViewSort> sort, List<QueryDatasetField> availableFields);

    /**
     * Checks that the dataset's source table and columns still exist in the
     * datasource, using the {@link Chat2DBMetadataProvider}.
     */
    List<ErrorCode> validateStructure(QueryDataset dataset);

    /**
     * Runs every §6.4 view rule against the given saved query view and returns
     * the collected error codes. An empty list means the view is valid.
     * <p>Checks: referenced dataset exists and is PUBLISHED, every referenced
     * field (dimensions, measures, filters, sort) exists in the dataset, filter
     * operators match field types per §6.4.4, sort fields are sortable,
     * pageSize does not exceed {@link ai.chat2db.community.query.excel.domain.api.QueryExcelConstants#MAX_PAGE_SIZE},
     * and the datasource plugin supports the view's aggregation/pagination needs.</p>
     */
    List<ErrorCode> validateView(SavedQueryView view);

    /**
     * Standalone §6.4.4 filter validation: every filter must reference an
     * existing dataset field and the operator must be compatible with the field
     * type. Returns all collected errors (empty list when valid).
     */
    List<ErrorCode> validateViewFilters(SavedQueryView view, List<QueryDatasetField> datasetFields);

    /**
     * Standalone sort validation: every sort entry must reference an existing
     * dataset field and that field must be sortable. Returns all collected
     * errors (empty list when valid).
     */
    List<ErrorCode> validateViewSort(SavedQueryView view, List<QueryDatasetField> datasetFields);

    /**
     * Checks version compatibility between a saved view and the currently
     * published dataset. When the dataset version differs from the version the
     * view was saved against, every field referenced by the view must still
     * exist in the dataset; otherwise the view becomes invalid.
     *
     * @return {@code true} when the view remains compatible with the dataset
     */
    boolean validateViewCompatibility(SavedQueryView view, QueryDataset dataset);
}