package ai.chat2db.community.query.excel.domain.api.model;

import lombok.Builder;
import lombok.Data;

/**
 * A single validation finding for an Excel report template
 * (requirements §8.6-8.9, §8.11, §8.12).
 * <p>An empty validation result means the template is valid.
 * Non-fatal findings (e.g. font fallback) carry {@code warning = true}
 * so callers can decide whether to block on them.</p>
 */
@Data
@Builder
public class ValidationError {

    /** Stable error code, see {@link ai.chat2db.community.query.excel.domain.api.ErrorCode}. */
    private String errorCode;

    /** Human-readable description of the problem. */
    private String message;

    /** Sheet the finding refers to, or {@code null} when not sheet-scoped. */
    private String sheetName;

    /** Cell range the finding refers to, or {@code null} when not range-scoped. */
    private String cellRange;

    /** {@code true} for non-fatal findings (e.g. font fallback), {@code false} by default. */
    @Builder.Default
    private boolean warning = false;
}