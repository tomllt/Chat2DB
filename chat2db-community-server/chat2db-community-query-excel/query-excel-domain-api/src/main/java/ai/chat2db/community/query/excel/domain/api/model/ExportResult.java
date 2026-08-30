package ai.chat2db.community.query.excel.domain.api.model;

import lombok.Builder;
import lombok.Data;

/**
 * Result of an Excel export operation (requirements §8.10, §8.11).
 * <p>Carries the download token, export record id, row count, file size,
 * and status so callers can track the export and initiate a download.</p>
 */
@Data
@Builder
public class ExportResult {

    /** Token used to download the generated .xlsx file. */
    private String downloadToken;

    /** ID of the persisted export record. */
    private Long exportId;

    /** Number of data rows exported. */
    private int rowCount;

    /** Size of the generated .xlsx file in bytes. */
    private long fileSize;

    /** Export status: RUNNING, SUCCESS, or FAILED. */
    private String status;
}