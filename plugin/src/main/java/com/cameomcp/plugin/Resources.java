package com.cameomcp.plugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Extracts embedded AI discovery resources (SKILL.md, AGENTS.md, README.md, mcp-tools-manifest.json)
 * from the installed plugin to ~/.cameo-mcp/ on startup so any AI agent can read them from a fixed path.
 */
public final class Resources {

    private static final String[] BUNDLED_FILES = {
        "skills/cameo-mcp/SKILL.md",
        "AGENTS.md",
        "README.md",
        "schemas/mcp-tools-manifest.json"
    };

    private Resources() {}

    public static File syncToUserHome() {
        File baseDir = new File(System.getProperty("user.home"), ".cameo-mcp");
        for (String relPath : BUNDLED_FILES) {
            try (InputStream is = Resources.class.getResourceAsStream("/" + relPath)) {
                if (is != null) {
                    File out = new File(baseDir, relPath);
                    out.getParentFile().mkdirs();
                    Files.copy(is, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {}
        }
        return baseDir;
    }
}
