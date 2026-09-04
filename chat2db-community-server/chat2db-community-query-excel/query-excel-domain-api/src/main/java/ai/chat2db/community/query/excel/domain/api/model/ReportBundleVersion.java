package ai.chat2db.community.query.excel.domain.api.model;

import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * A named, self-contained export version of a {@link ReportBundle}.
 *
 * <p>{@code workspaceId} and {@code bundleId} are mandatory persistence
 * identity fields. Access is valid only when both the requested workspace and
 * bundle relationship match; cross-workspace access is rejected and not
 * exposed.</p>
 *
 * <p>The three filter/selection properties are version-owned values. Services
 * must defensively deep-copy {@code boundFieldsSnapshot},
 * {@code presetRowFiltersSnapshot}, {@code rowFilter}, and
 * {@code selectedRowKeys} when the version is created and when it crosses a
 * persistence boundary. Historical versions are never updated from draft
 * state.</p>
 *
 * <p>{@code selectedRowKeys} contains stable source-row identifiers for whole
 * rows, not cell coordinates. Export consumes this saved selection together
 * with the version snapshots and effective filters.</p>
 */
@Data
public class ReportBundleVersion {

    private Long id;

    private Long workspaceId;

    private Long bundleId;

    private String versionName;

    private Integer versionNo;

    private List<ExcelColumnBinding> boundFieldsSnapshot;

    private List<ViewFilter> presetRowFiltersSnapshot;

    private List<ViewFilter> rowFilter;

    private List<String> selectedRowKeys;

    private Long ownerId;

    private Date gmtCreate;

    private Date gmtModified;
}
