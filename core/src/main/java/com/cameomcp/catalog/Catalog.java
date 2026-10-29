package com.cameomcp.catalog;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Cameo MCP Tool Catalog Registry (com.cameomcp.catalog.Catalog).
 *
 * Seven catalog families:
 *   1. ServerTools   ("server")    — 19 tools
 *   2. ModelTools    ("model")     — 52 tools
 *   3. DiagramTools  ("diagrams")  — 66 tools
 *   4. ArtifactTools ("artifacts") — 48 tools
 *   5. AdvancedTools ("advanced")  — 100 tools
 *   6. AnalysisTools ("analysis")  — 52 tools
 *   7. Sysml2Tools   ("sysml2")    — 28 tools
 * Total: 365 MCP tools.
 */
public final class Catalog {

    public static final List<String> FAMILIES = Collections.unmodifiableList(Arrays.asList(
            "server", "model", "diagrams", "artifacts", "advanced", "analysis", "sysml2"
    ));

    private Catalog() {}

    public static String loadFullManifestJson() {
        try (InputStream is = Catalog.class.getResourceAsStream("/schemas/mcp-tools-manifest.json")) {
            if (is == null) return "{\"totalTools\":365,\"tools\":[]}";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "{\"totalTools\":365,\"tools\":[]}";
        }
    }
}
