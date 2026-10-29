package com.cameomcp.catalog;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/** MCP prompts: ready-made multi-step modeling workflows. */
public final class CatalogPrompts {

    private record Prompt(String description, String text) {}

    private static final Map<String, Prompt> PROMPTS = new LinkedHashMap<>();
    static {
        PROMPTS.put("model_system_structure", new Prompt(
                "Build a SysML block hierarchy and BDD for a system from a description.",
                "Model the structure of the system described below in the open Cameo project.\n"
                + "1. cameo_get_project, then cameo_query_elements to find an existing package to work in.\n"
                + "2. cameo_create_elements in ONE call: a package, the blocks, part properties (type=Property, properties.type = the part block, properties.aggregation = composite), value properties and ports.\n"
                + "3. cameo_create_diagram (type BDD) in that package, cameo_add_to_diagram with all blocks, cameo_layout_diagram.\n"
                + "4. cameo_get_diagram_image to check the result, then summarize what you created.\n\nSystem: "));
        PROMPTS.put("trace_requirements", new Prompt(
                "Create or import requirements and connect them to the design with satisfy/verify links.",
                "Trace requirements to the design in the open Cameo project.\n"
                + "1. cameo_query_elements type=Requirement to find existing requirements; create missing ones with cameo_create_elements (type Requirement, properties Id/Text as tagged values).\n"
                + "2. cameo_create_relationship type Satisfy (design element -> requirement) and Verify (test case -> requirement).\n"
                + "3. cameo_trace_coverage_analysis on the requirements package and report unlinked requirements.\n\nRequirements / scope: "));
        PROMPTS.put("review_model_quality", new Prompt(
                "Run validation and structural checks and produce a prioritized findings list.",
                "Review the quality of the model scope given below.\n"
                + "1. cameo_list_validation_suites, then cameo_run_validation on the scope.\n"
                + "2. cameo_get_orphan_elements, cameo_find_circular_dependencies, cameo_calculate_metrics.\n"
                + "3. Report findings grouped by severity with element ids so they can be fixed.\n\nScope (package id or name): "));
        PROMPTS.put("build_state_machine", new Prompt(
                "Create a state machine with states, transitions, triggers and behaviors plus its diagram.",
                "Build the state machine described below.\n"
                + "1. cameo_create_elements: a StateMachine under the owning block, an InitialState, the States and a FinalState (parent = the state machine), and Transition relationships.\n"
                + "2. cameo_set_transition_trigger for each event, cameo_set_state_behaviors for entry/do/exit.\n"
                + "3. cameo_check_state_reachability, then cameo_create_diagram type STM and cameo_add_to_diagram.\n\nBehavior: "));
    }

    private CatalogPrompts() {}

    public static ArrayNode list() {
        ArrayNode arr = Catalog.JSON.createArrayNode();
        PROMPTS.forEach((name, p) -> arr.addObject().put("name", name).put("description", p.description())
                .putArray("arguments").addObject().put("name", "input")
                .put("description", "What to model or review").put("required", false));
        return arr;
    }

    /** Returns the prompts/get result, or null if the prompt does not exist. */
    public static ObjectNode get(String name, String input) {
        Prompt p = PROMPTS.get(name);
        if (p == null) return null;
        ObjectNode r = Catalog.JSON.createObjectNode().put("description", p.description());
        r.putArray("messages").addObject().put("role", "user")
                .putObject("content").put("type", "text").put("text", p.text() + (input == null ? "" : input));
        return r;
    }
}
