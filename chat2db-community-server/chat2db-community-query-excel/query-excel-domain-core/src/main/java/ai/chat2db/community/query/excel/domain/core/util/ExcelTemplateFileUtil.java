package ai.chat2db.community.query.excel.domain.core.util;

import java.io.File;

import ai.chat2db.community.tools.util.ConfigUtils;

/**
 * Resolves the directory where uploaded Excel template files are stored:
 * {@code {ConfigUtils.getEnvBasePath()}/excel-templates/}.
 * <p>Mirrors the storage path pattern used by {@code SmallDataStorage}, which
 * joins {@link ConfigUtils#getEnvBasePath()} with a sub-directory name.</p>
 */
public final class ExcelTemplateFileUtil {

    private ExcelTemplateFileUtil() {
        // utility class
    }

    /** Sub-directory (relative to the env base path) holding template files. */
    public static final String TEMPLATE_DIR_NAME = "excel-templates";

    /**
     * Returns the template storage directory, creating it (including parents)
     * when it does not exist yet.
     */
    public static File getTemplatesDir() {
        File dir = new File(ConfigUtils.getEnvBasePath() + File.separator + TEMPLATE_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Returns the storage file for a template id: {@code {dir}/{id}.xlsx}.
     */
    public static File getTemplateFile(Long id) {
        return new File(getTemplatesDir(), id + ".xlsx");
    }
}