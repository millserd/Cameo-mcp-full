---
name: cameo-mcp
description: Connect to and command Cameo Systems Modeler / CATIA Magic (2024x and 2026x) via the 365-tool Java Cameo MCP Server (com.cameomcp.plugin) across server, model, diagrams, artifacts, advanced, analysis, and sysml2 families.
---
# Cameo MCP Skill (`skills/cameo-mcp/SKILL.md`)

## 1. Endpoint & Handshake
- **Default HTTP MCP Endpoint**: `http://127.0.0.1:18740/mcp` (check **MCP > MCP Server Status...** in Cameo if modified).
- **Stdio Bridge**: `java -jar <CAMEO_HOME>/plugins/com.cameomcp.plugin/cameo-mcp-stdio.jar --port 18740`
- **First Call**: Always invoke `cameo_status` and `cameo_get_project` first. Use `cameo_log` to publish milestone updates to Cameo's Notification window so the user can follow your progress in real time.

## 2. Core Modeling Workflows
- **System Structure & BDD (`model_system_structure` prompt)**:
  1. Create packages, blocks, value types, flow properties, and ports in a single undoable step via `cameo_create_elements`.
  2. Set property types, multiplicities, default values, and composite aggregations via `cameo_set_property_type`, `cameo_set_property_multiplicity`, `cameo_set_property_default_value`, and `cameo_set_property_aggregation`.
  3. Create a BDD (`cameo_create_diagram`), populate symbols (`cameo_add_symbols_to_diagram`), draw paths (`cameo_add_path_to_diagram`), and run `cameo_layout_diagram` + `cameo_resize_symbols_to_fit`.
- **Requirements & Traceability (`trace_requirements` prompt)**:
  1. Import or create requirements (`cameo_apply_requirements_import` or `cameo_create_elements`).
  2. Link `Satisfy`, `Verify`, `Derive`, and `Refine` relationships and build a native matrix (`cameo_create_matrix`) or Relation Map (`cameo_create_relation_map`).
  3. Audit coverage with `cameo_trace_coverage_analysis` and `cameo_get_unlinked_requirements`.
- **State Machines & Simulation (`build_state_machine` prompt)**:
  1. Create a `StateMachine`, `InitialState`, `State` elements, and `Transition` links.
  2. Configure triggers/guards (`cameo_set_transition_trigger`) and entry/do/exit behaviors (`cameo_set_state_behaviors`).
  3. Verify reachability (`cameo_check_state_reachability`, `cameo_find_unreachable_states`) and execute simulation (`cameo_run_simulation`, `cameo_step_simulation`, `cameo_get_simulation_variables`).
- **SysML v2 (Cameo 2026x `sysml2` family)**:
  1. Parse or import textual SysML v2 (`cameo_sysml2_parse_text`, `cameo_sysml2_import`) or create SysML v2 definitions/usages (`cameo_sysml2_create_part`, `cameo_sysml2_create_port`, `cameo_sysml2_create_connection`).
  2. Convert between SysML v1.x and SysML v2 branches using `cameo_sysml2_convert_v1_to_v2` and `cameo_sysml2_convert_v2_to_v1`.
