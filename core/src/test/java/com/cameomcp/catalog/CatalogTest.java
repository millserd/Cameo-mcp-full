package com.cameomcp.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/** The manifest is what agents read; keep it complete and useful. */
public class CatalogTest {

    @Test
    public void everyToolIsWellDescribed() {
        Set<String> names = new HashSet<>();
        for (JsonNode t : Catalog.tools()) {
            String name = t.path("name").asText();
            assertTrue(name, name.startsWith("cameo_"));
            assertTrue("duplicate " + name, names.add(name));
            assertTrue(name + " description", t.path("description").asText().length() > 20);
            assertTrue(name + " family", Set.of("server", "model", "diagrams", "artifacts", "advanced", "analysis", "methodology", "sysml2")
                    .contains(t.path("family").asText()));
            JsonNode schema = t.path("inputSchema");
            assertEquals(name, "object", schema.path("type").asText());
            for (JsonNode req : schema.path("required")) assertTrue(name + " requires undefined " + req, schema.path("properties").has(req.asText()));
            for (Map.Entry<String, JsonNode> p : (Iterable<Map.Entry<String, JsonNode>>) schema.path("properties")::fields) {
                String d = p.getValue().path("description").asText();
                assertTrue(name + "." + p.getKey() + " needs a real description", d.length() > 3 && !d.matches("\\w+ \\((required|optional)\\)"));
                if ("array".equals(p.getValue().path("type").asText())) assertTrue(name + "." + p.getKey() + " items", p.getValue().has("items"));
            }
        }
        assertEquals(Catalog.manifest().path("totalTools").asInt(), names.size());
    }

    @Test
    public void promptsCanBeFetched() {
        for (JsonNode p : CatalogPrompts.list()) {
            assertNotNull(CatalogPrompts.get(p.path("name").asText(), "x"));
        }
        assertNull(CatalogPrompts.get("nope", null));
        // every tool a prompt tells the agent to call must exist
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode t : Catalog.tools()) names.add(t.path("name").asText());
        for (JsonNode p : CatalogPrompts.list()) {
            String text = CatalogPrompts.get(p.path("name").asText(), "").toString();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("cameo_[a-z0-9_]*[a-z0-9](?![a-z0-9_*])").matcher(text);
            while (m.find()) assertTrue(p.path("name").asText() + " names unknown tool " + m.group(), names.contains(m.group()));
        }
    }
}
