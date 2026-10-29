# Cameo MCP

A Model Context Protocol (MCP) server that runs as a plugin inside **Cameo Systems Modeler / CATIA Magic / Magic Systems of Systems Architect (2024x and 2026x)**, so AI agents and MCP clients can read and edit SysML v1, UML and SysML v2 models. Pure Java; it uses only Cameo's own libraries.

- **134 tools** in 8 families: `server`, `model`, `diagrams`, `artifacts`, `advanced`, `analysis`, `methodology`, `sysml2`
- **SysML v2** (Cameo 2026x): create, edit, connect, import and export SysML v2 text, validate, and evaluate parametrics with Cameo's own expression evaluator
- **Analysis without extra licences**: parametric what-if evaluation and weighted trade studies on SysML v2 models; Cameo Simulation Toolkit is used when installed
- **Methodology packs, review packets and text proofing** for both SysML v1 and v2 models
- **Two transports**: Streamable HTTP on `127.0.0.1:18740/mcp`, or stdio via `cameo-mcp-stdio.jar`
- **In-app `MCP` menu** with a status window, self-test and copy-ready client config
- **Guarded access**: localhost only, Origin check, optional Bearer token, macros off by default, every write is one undoable step

## How It Works

![Cameo MCP request flow: an MCP client connects over stdio (via StdioLauncher) or HTTP to the plugin's embedded server on 127.0.0.1:18740; CallGuard checks Origin and Bearer token; the dispatcher routes JSON-RPC methods; tools/call runs each tool on Cameo's Swing thread, writes inside one SessionManager session, against the UML/SysML v1 or the SysML v2 API; CameoLog records every step and the result returns to the client.](docs/mcp-flow.svg)

## Repository Structure

```text
cameo-mcp/
├── core/src/main/java/com/cameomcp/
│   ├── catalog/
│   │   ├── Catalog.java              # Loads the tool manifest (Jackson)
│   │   └── CatalogPrompts.java       # 6 ready-made MBSE workflow prompts
│   └── mcp/CallGuard.java            # Origin check, Bearer token, macro switch
├── plugin/
│   ├── plugin.xml                    # id="com.cameomcp.plugin"
│   └── src/main/java/com/cameomcp/plugin/
│       ├── CameoMcpPlugin.java       # Plugin lifecycle
│       ├── McpRuntime.java           # Embedded HTTP + JSON-RPC 2.0 server (MCP 2025-06-18)
│       ├── CameoLog.java, Settings.java, Resources.java
│       ├── tools/
│       │   ├── Tools.java            # Tool table; runs tools on the Swing thread, writes in one session
│       │   ├── ServerTools.java      # server
│       │   ├── ModelTools.java       # model (UML / SysML v1)
│       │   ├── DiagramTools.java     # diagrams
│       │   ├── ArtifactTools.java    # artifacts
│       │   ├── AdvancedTools.java    # advanced
│       │   ├── AnalysisTools.java    # analysis: traceability, coverage, metrics
│       │   ├── StudyTools.java       # analysis: parametrics, trade studies, simulation
│       │   ├── MethodTools.java      # methodology: packs, review packets, proofing
│       │   ├── ExtraTools.java       # relation maps, Report Wizard, sequence messages
│       │   ├── Sysml2Tools.java      # sysml2 (registered only when Cameo has SysML v2)
│       │   └── V2*.java              # SysML v2 (KerML) access, isolated so the plugin loads without it
│       └── ui/                       # MCP menu, status window, client configs, self-test
├── stdio/.../StdioLauncher.java      # Stdio bridge + CLI (--diagnose, --list, --describe, --call)
├── schemas/mcp-tools-manifest.json   # Every tool's description and JSON Schema
├── skills/cameo-mcp/SKILL.md         # Agent workflow guide (also served as cameo://guide)
├── CAMEO_MCP_TOOL_MAP.md             # All tools with their arguments
├── AGENTS.md                         # Entry point for coding agents
└── build.gradle
```

## Quick Start

### 1. Build and install

You need Gradle 8 or later, a JDK (21 for Cameo 2026x, 17 for 2024x) and a Cameo install. Nothing is downloaded: the build compiles against Cameo's own jars, including its copies of Jackson and JUnit, so it works offline.

```bash
# Builds build/dist/cameo-mcp-4.0.0.zip (Resource Manager) and cameo-mcp-4.0.0-manual.zip
./build.sh                    # or build.ps1 on Windows; asks for the Cameo install folder
gradle test -PcameoHome="/path/to/Cameo"

# Or copy straight into <CAMEO_HOME>/plugins/com.cameomcp.plugin/
gradle deploy -PcameoHome="/path/to/Cameo"
```

Install `cameo-mcp-4.0.0.zip` with **Help > Resource/Plugin Manager > Import**, or extract the manual zip into `<CAMEO_HOME>/plugins/`. Restart Cameo.

### 2. Check it inside Cameo

**MCP > MCP Server Status...** shows the endpoint (`http://127.0.0.1:18740/mcp`), lets you change the port and Bearer token, allow macros, run a self-test and copy client configuration.

### 3. Connect an MCP client

```json
{ "mcpServers": { "cameo": { "url": "http://127.0.0.1:18740/mcp" } } }
```

Stdio clients:

```json
{ "mcpServers": { "cameo": { "command": "java",
  "args": ["-jar", "/path/to/Cameo/plugins/com.cameomcp.plugin/cameo-mcp-stdio.jar", "--port", "18740"] } } }
```

Command line checks:

```bash
java -jar cameo-mcp-stdio.jar --diagnose
java -jar cameo-mcp-stdio.jar --list
java -jar cameo-mcp-stdio.jar --describe cameo_sysml2_add_text
java -jar cameo-mcp-stdio.jar --call cameo_status '{}'
```

## Using it

1. Call `cameo_status` first. `project.kind` is `uml` (UML / SysML v1) or `sysml2`; `communityMode` is true in the SysML v2 Community edition, which opens SysML v2 projects only.
2. Find tools with `cameo_list_tools(family, query)` or read the `cameo://guide` and `cameo://tools` resources.
3. Prompts: `model_system_structure`, `trace_requirements`, `review_model_quality`, `build_state_machine`, `model_sysml2_system`, `run_trade_study`.
4. Every write tool is one undoable step (`cameo_undo` / `cameo_redo`). Prefer the batch tools (`cameo_create_elements`, `cameo_batch_modify_elements`, `cameo_sysml2_add_text`) for many changes.

### SysML v2

SysML v2 projects (`cameo_create_new_project kind=sysml2`) are edited through Cameo's own SysML v2 text importer: tools generate SysML v2 text, Cameo parses it with full name resolution, and the new elements are placed under the owner you name. So anything you can write in the SysML v2 text editor can be added with `cameo_sysml2_add_text`, and `cameo_sysml2_check_text` reports syntax and name-resolution errors before anything changes.

- Read: `cameo_sysml2_list_roots`, `cameo_sysml2_get_tree`, `cameo_sysml2_find_elements`, `cameo_sysml2_get_element`, `cameo_sysml2_get_relationships`
- Write: `cameo_sysml2_create_element` (any definition or usage), `cameo_sysml2_add_text`, `cameo_sysml2_set_property`, `cameo_sysml2_connect`, `cameo_sysml2_move_element`, `cameo_sysml2_delete_element`, `cameo_sysml2_import_file`
- Check and export: `cameo_sysml2_validate`, `cameo_sysml2_export_text`. The export writes plain names where they resolve in context, and qualified names only where needed. It reports `complete: false` with a list of `gaps` for anything it cannot write exactly; unnamed decision/merge/fork/join nodes get generated names (`_decide1`) so successions can refer to them.
- Analyse: `cameo_evaluate_parametrics` evaluates every attribute expression and constraint; with `inputs` it applies what-if values and rolls them back. `cameo_run_trade_study` ranks alternatives by weighted criteria read from their (evaluated) attributes.

Adding text takes two undo steps in Cameo's history (the import, then the move into the owner); the tools say so with `undoSteps: 2`, and `cameo_undo` takes `steps`. Writes are refused while Cameo has an editing session open (for example a Specification dialog), so nothing is left half-done.

### Optional Cameo features

- `cameo_run_simulation`, and SysML v1 parametric solving, need **Cameo Simulation Toolkit**; without it they say so, and `cameo_get_simulation_status` reports what is available.
- Relation maps need the **Relation Map** plugin; reports need **Report Wizard** templates (`cameo_list_report_templates`).
- `cameo_execute_macro` runs Groovy (or another installed JSR-223 script language) inside Cameo and is off until the user enables macros in the status window.

<!-- catalog:start -->
## Tool catalog

134 tools in 8 families. The full argument list of every tool is in [CAMEO_MCP_TOOL_MAP.md](CAMEO_MCP_TOOL_MAP.md) and in `schemas/mcp-tools-manifest.json` (what `tools/list` returns).

| Family | Tools | Covers |
|---|---:|---|
| `server` | 16 | Connection, discovery, logging, undo/redo, UI actions |
| `model` | 29 | Projects, elements, relationships, stereotypes and properties (UML / SysML v1) |
| `diagrams` | 27 | Diagrams, symbols, paths, layout, export, relation maps, sequence messages |
| `artifacts` | 16 | Generic tables, dependency matrices, reports, attachments, hyperlinks |
| `advanced` | 14 | Macros, validation, profiles, Teamwork Cloud |
| `analysis` | 11 | Traceability, coverage, metrics, parametrics, trade studies, simulation |
| `methodology` | 6 | Methodology packs, conformance, review packets, text proofing |
| `sysml2` | 15 | SysML v2 (KerML) models in Cameo 2026x: text import/export, create, edit, connect, validate |

**Server** — `cameo_status`, `cameo_get_capabilities`, `cameo_list_tools`, `cameo_get_last_error`, `cameo_get_system_logs`, `cameo_log`, `cameo_reset_session`, `cameo_undo`, `cameo_redo`, `cameo_get_command_history`, `cameo_clear_command_history`, `cameo_get_ui_state`, `cameo_select_in_containment_tree`, `cameo_list_ui_actions`, `cameo_invoke_ui_action`, `cameo_poll_model_change_events`

**Model** — `cameo_get_project`, `cameo_list_open_projects`, `cameo_set_active_project`, `cameo_create_new_project`, `cameo_open_project_file`, `cameo_save_project`, `cameo_close_project`, `cameo_list_attached_modules`, `cameo_query_elements`, `cameo_get_element`, `cameo_get_containment_tree`, `cameo_get_relationships`, `cameo_create_element`, `cameo_create_elements`, `cameo_modify_element`, `cameo_batch_modify_elements`, `cameo_move_element`, `cameo_delete_element`, `cameo_clone_element`, `cameo_convert_element_type`, `cameo_replace_element`, `cameo_reverse_relationship`, `cameo_reconnect_relationship`, `cameo_create_relationship`, `cameo_apply_stereotype`, `cameo_remove_stereotype`, `cameo_set_tagged_values`, `cameo_set_transition_trigger`, `cameo_set_state_behaviors`

**Diagrams** — `cameo_list_diagram_types`, `cameo_list_diagrams`, `cameo_create_diagram`, `cameo_open_diagram`, `cameo_close_diagram`, `cameo_get_diagram_contents`, `cameo_add_to_diagram`, `cameo_add_path_to_diagram`, `cameo_delete_shapes`, `cameo_move_shapes`, `cameo_reparent_shapes`, `cameo_align_symbols`, `cameo_layout_diagram`, `cameo_route_paths`, `cameo_reset_label_positions`, `cameo_get_shape_properties`, `cameo_set_shape_properties`, `cameo_copy_symbol_style`, `cameo_create_note`, `cameo_create_swimlanes`, `cameo_select_diagram_symbols`, `cameo_set_diagram_grid`, `cameo_export_diagram`, `cameo_get_diagram_image`, `cameo_create_relation_map`, `cameo_refresh_relation_map`, `cameo_add_sequence_message`

**Artifacts** — `cameo_create_table`, `cameo_get_table_contents`, `cameo_list_table_columns`, `cameo_add_table_columns`, `cameo_add_table_rows`, `cameo_remove_table_rows`, `cameo_export_table_csv`, `cameo_create_matrix`, `cameo_get_matrix_contents`, `cameo_list_report_templates`, `cameo_attach_file`, `cameo_extract_attached_file`, `cameo_add_hyperlink`, `cameo_list_hyperlinks`, `cameo_remove_hyperlink`, `cameo_generate_report`

**Advanced** — `cameo_execute_macro`, `cameo_list_validation_suites`, `cameo_run_validation`, `cameo_get_validation_results`, `cameo_list_profiles`, `cameo_get_profile_details`, `cameo_create_stereotype`, `cameo_create_tag_definition`, `cameo_get_teamwork_project`, `cameo_lock_elements`, `cameo_unlock_elements`, `cameo_get_locks`, `cameo_commit_project`, `cameo_update_project`

**Analysis** — `cameo_get_traceability_graph`, `cameo_trace_coverage_analysis`, `cameo_find_circular_dependencies`, `cameo_get_orphan_elements`, `cameo_find_dead_end_flows`, `cameo_check_state_reachability`, `cameo_calculate_metrics`, `cameo_evaluate_parametrics`, `cameo_run_trade_study`, `cameo_get_simulation_status`, `cameo_run_simulation`

**Methodology** — `cameo_list_methodology_packs`, `cameo_apply_methodology_pack`, `cameo_check_methodology_conformance`, `cameo_proof_model_text`, `cameo_apply_proofing_patch`, `cameo_generate_review_packet`

**SysML v2** — `cameo_sysml2_list_roots`, `cameo_sysml2_get_element`, `cameo_sysml2_get_tree`, `cameo_sysml2_find_elements`, `cameo_sysml2_create_element`, `cameo_sysml2_add_text`, `cameo_sysml2_check_text`, `cameo_sysml2_import_file`, `cameo_sysml2_export_text`, `cameo_sysml2_set_property`, `cameo_sysml2_delete_element`, `cameo_sysml2_move_element`, `cameo_sysml2_get_relationships`, `cameo_sysml2_connect`, `cameo_sysml2_validate`
<!-- catalog:end -->

## Security

- The server binds to `127.0.0.1` only (a non-loopback `host` in the settings file is ignored) and rejects requests whose `Origin` is not a localhost address.
- If `~/.cameo-mcp/settings.properties` cannot be read, the server stays off rather than run without the token it may contain.
- Set a Bearer token in the status window to require `Authorization: Bearer <token>`.
- Macros are disabled by default (`allowMacros=false` in `~/.cameo-mcp/settings.properties`).

## License

MIT. See [LICENSE](LICENSE).
