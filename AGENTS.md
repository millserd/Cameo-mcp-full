# AGENTS.md — Cameo MCP

Cameo MCP (`com.cameomcp.plugin`) runs inside Cameo Systems Modeler / CATIA Magic / Magic Systems of Systems Architect (2024x and 2026x) and serves MCP on `http://127.0.0.1:18740/mcp` (port and token under Cameo's **MCP > MCP Server Status...** menu).

## Checklist

1. Call `cameo_status` (or `java -jar cameo-mcp-stdio.jar --diagnose`). If it does not answer, ask the user to open **MCP > MCP Server Status...** in Cameo.
2. Read `skills/cameo-mcp/SKILL.md` (also the MCP resource `cameo://guide`) before multi-step modeling.
3. `project.kind` in `cameo_status` decides the toolset: `uml` → the model/diagrams/artifacts/advanced tools; `sysml2` → the `cameo_sysml2_*` tools. Analysis and methodology tools work on both.
4. Find tools with `cameo_list_tools(family, query)`. Families:
   - `server`: status, capabilities, logs, undo/redo, command history, UI state and actions, model change events.
   - `model`: projects (including new SysML v2 projects), element and relationship CRUD, batch create/modify, stereotypes, tagged values, state machine semantics.
   - `diagrams`: create/open diagrams, add symbols and paths, layout, routing, styles, image export, relation maps, sequence messages.
   - `artifacts`: generic tables, dependency matrices, Report Wizard templates and report generation, attached files, hyperlinks.
   - `advanced`: macros (off unless the user allows them), validation suites, profiles and stereotypes, Teamwork Cloud.
   - `analysis`: traceability graph, requirement coverage, cycles, orphans, dead ends, state reachability, metrics, parametric evaluation, trade studies, simulation.
   - `methodology`: MagicGrid / SysML v2 starter / OOSEM packs and conformance checks, text proofing with patch plans, review packets.
   - `sysml2`: SysML v2 read, create, text add/check/import/export, set properties, connect, move, delete, validate.
5. Every write is one undoable step; use `cameo_log` to tell the user what you are doing.
