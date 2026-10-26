# AGENTS.md — Cameo MCP Agent Instructions

Cameo MCP (`com.cameomcp.plugin`) runs inside Cameo Systems Modeler / CATIA Magic (2024x and 2026x) on `http://127.0.0.1:18740/mcp` (configurable under Cameo's top-level **MCP > MCP Server Status...** menu).

## Quick Agent Checklist

1. **Verify Connection**: Call `cameo_status` (or run `java -jar cameo-mcp-stdio.jar --diagnose`). If unreachable, ask the user to check **MCP > MCP Server Status...** in Cameo's main menu bar.
2. **Load Skill Instructions**: Read `skills/cameo-mcp/SKILL.md` (or MCP resource `cameo://guide`) before executing complex SysML v1/v2 modeling workflows.
3. **Discover Tools by Family**: Call `cameo_list_families` and `cameo_list_tools` across the 7 catalog families:
   - `server` (19 tools): Status, capabilities, tool search, session recovery, `cameo_undo`, `cameo_redo`, `cameo_log`.
   - `model` (52 tools): Project info, element CRUD, bulk `cameo_create_elements`, reparenting (`cameo_move_element`), stereotypes, properties (`type`, `multiplicity`, `default_value`, `aggregation`), relationships, state behaviors/triggers, cloning, and refactoring.
   - `diagrams` (66 tools): Diagram creation, symbol placement, bulk `cameo_add_symbols_to_diagram`, auto-layout, routing, alignment (`cameo_align_symbols`), sizing (`cameo_resize_symbols_to_fit`), legends, compartments, sequence messages, swimlanes, and image export (`cameo_export_diagram` / `cameo_get_diagram_image_base64`).
   - `artifacts` (48 tools): Generic tables, dependency/allocation/satisfy matrices, Relation Maps, Report Wizard, XMI import/export, file attachments, and snapshots.
   - `advanced` (100 tools): Groovy/Jython macros, OCL evaluation/search, native validation + quick-fixes, custom profiles/stereotypes/tags, Smart Packages, hyperlinks, 2-way/3-way project diff & merge, Teamwork Cloud (lock/unlock/commit/update), derived properties, and DSL customizations.
   - `analysis` (52 tools): Cameo Simulation Toolkit (run/pause/resume/step/stop/variables), parametric solver, upward/downward dependency & impact analysis, requirement coverage, orphan/circular/dead-end detection, metrics, trade studies, mass/power/cost roll-ups, state reachability, and OOSEM recipes.
   - `sysml2` (28 tools): Cameo 2026x KerML / SysML v2 textual notation import/export/parse, SysML v2 definitions/usages (part, port, item, action, state, requirement, constraint, calculation, case, connection, interface, allocation), v1↔v2 conversion, and 2026x data markings.
