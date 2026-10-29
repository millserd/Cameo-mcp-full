package com.cameomcp.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.InputStream;

/** The tool manifest (schemas/mcp-tools-manifest.json) is the single source of tool names, descriptions and schemas. */
public final class Catalog {

    public static final ObjectMapper JSON = new ObjectMapper();
    private static volatile JsonNode manifest;

    private Catalog() {}

    public static JsonNode manifest() {
        JsonNode m = manifest;
        if (m == null) {
            try (InputStream is = Catalog.class.getResourceAsStream("/schemas/mcp-tools-manifest.json")) {
                if (is == null) throw new IllegalStateException("schemas/mcp-tools-manifest.json missing from the jar");
                m = manifest = JSON.readTree(is);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot read tool manifest", e);
            }
        }
        return m;
    }

    public static ArrayNode tools() {
        return (ArrayNode) manifest().get("tools");
    }

    public static JsonNode tool(String name) {
        for (JsonNode t : tools()) if (name.equals(t.path("name").asText())) return t;
        return null;
    }

    public static String version() {
        return manifest().path("version").asText("dev");
    }
}
