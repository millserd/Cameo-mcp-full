package com.cameomcp.catalog;

/**
 * Pre-packaged multi-step MCP Prompts registered into the Cameo MCP Server
 * (aligned with millserd/Cameo-mcp CatalogPrompts.java).
 */
public final class CatalogPrompts {

    private CatalogPrompts() {}

    public static String listPromptsJson() {
        return "[" +
            "{"name":"model_system_structure","description":"Build a SysML block hierarchy and BDD for a system from a description."}," +
            "{"name":"trace_requirements","description":"Create or import requirements and connect them to the design with satisfy/verify links."}," +
            "{"name":"review_model_quality","description":"Run validation, semantic checks and proofing and produce a prioritized findings list."}," +
            "{"name":"build_state_machine","description":"Create a state machine with states, transitions, triggers and behaviors plus its diagram."}" +
        "]";
    }
}
