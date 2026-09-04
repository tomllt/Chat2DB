package ai.chat2db.community.query.excel.domain.api.model;

import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * Draft configuration for an application-defined Excel report bundle.
 *
 * <p>{@code workspaceId} is mandatory for every persisted bundle. Bundle and
 * version services must reject access when the requested workspace does not
 * match the record workspace; cross-workspace records are not exposed.</p>
 *
 * <p>{@code boundFields} and {@code presetRowFilters} are draft state. A
 * version service owns defensive copying these nested values when creating a
 * {@link ReportBundleVersion}; later draft changes must not mutate a saved
 * version snapshot.</p>
 */
@Data
public class ReportBundle {

    private Long id;

    private Long workspaceId;

    private String name;

    private String description;

    private Long queryViewId;

    private List<ExcelColumnBinding> boundFields;

    private List<ViewFilter> presetRowFilters;

    private Long activeVersionId;

    private Long ownerId;

    private Date gmtCreate;

    private Date gmtModified;
}
