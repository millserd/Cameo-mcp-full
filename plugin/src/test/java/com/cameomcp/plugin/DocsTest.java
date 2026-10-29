package com.cameomcp.plugin;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/** Every tool the docs tell an agent to call must exist, so docs and manifest cannot drift apart. */
public class DocsTest {

    @Test
    public void docsOnlyNameExistingTools() throws Exception {
        Set<String> names = new HashSet<>();
        for (JsonNode t : Catalog.tools()) names.add(t.path("name").asText());
        File root = new File("..").getCanonicalFile();
        for (String doc : new String[]{"README.md", "AGENTS.md", "skills/cameo-mcp/SKILL.md", "CAMEO_MCP_TOOL_MAP.md"}) {
            File f = new File(root, doc);
            if (!f.isFile()) continue;
            Matcher m = Pattern.compile("cameo_[a-z0-9_]*[a-z0-9](?![a-z0-9_*])").matcher(Files.readString(f.toPath()));
            while (m.find()) assertTrue(doc + " names unknown tool " + m.group(), names.contains(m.group()));
        }
    }
}
