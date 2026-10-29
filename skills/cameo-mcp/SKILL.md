---
name: cameo-mcp
description: Read, build and analyse SysML v1, UML and SysML v2 models in Cameo Systems Modeler / CATIA Magic (2024x and 2026x) through the Cameo MCP server (134 tools in server, model, diagrams, artifacts, advanced, analysis, methodology and sysml2 families).
---
# Cameo MCP

## Start
- Endpoint `http://127.0.0.1:18740/mcp`; stdio bridge `java -jar <CAMEO_HOME>/plugins/com.cameomcp.plugin/cameo-mcp-stdio.jar --port 18740`.
- First call `cameo_status`. `project.kind` is `uml` or `sysml2`; with no project open, `cameo_create_new_project` (kind `sysml2` or `uml`; the SysML v2 Community edition only opens SysML v2 projects).
- Use `cameo_log` for milestones so the user can follow along in Cameo. Every write tool is one undo step (`cameo_undo`).

## SysML v1 / UML projects
- Structure: `cameo_create_elements` in one call (packages, blocks, part/value properties with `properties.type`, `aggregation`, ports), then `cameo_create_diagram` (BDD/IBD), `cameo_add_to_diagram`, `cameo_layout_diagram`, `cameo_get_diagram_image` to check.
- Requirements: create with `cameo_create_elements` (type Requirement, tagged values Id/Text), link with `cameo_create_relationship` (Satisfy, Verify, DeriveReqt, Refine, Trace), audit with `cameo_trace_coverage_analysis`; show with `cameo_create_matrix` or `cameo_create_relation_map`.
- Behaviour: state machines via `cameo_create_elements`, `cameo_set_transition_trigger`, `cameo_set_state_behaviors`, `cameo_check_state_reachability`; sequence diagrams via `cameo_add_sequence_message`.
- Documents: `cameo_list_report_templates`, then `cameo_generate_report`.

## SysML v2 projects
1. Look: `cameo_sysml2_list_roots`, `cameo_sysml2_get_tree`, `cameo_sysml2_find_elements`, `cameo_sysml2_get_element` (shows evaluated values).
2. Write SysML v2 text and check it with `cameo_sysml2_check_text` (pass `ownerId` so names resolve as inside that owner).
3. Add it with `cameo_sysml2_add_text` (fails without changes if the text has errors). For single changes: `cameo_sysml2_create_element` (kind = `part def`, `part`, `attribute`, `port`, `requirement def`, `constraint`, `state`, `action`, ...), `cameo_sysml2_set_property` (name, doc, type, specializes, multiplicity, value, valueExpression), `cameo_sysml2_connect` (connection, interface, binding, flow, succession, allocation, satisfy, dependency).
4. Qualified names quote non-identifiers: `Model::'My Package'::Part`.
5. Check: `cameo_sysml2_validate`, `cameo_evaluate_parametrics`, `cameo_sysml2_export_text`.

## Analysis
- `cameo_evaluate_parametrics`: values of attribute expressions and pass/fail of constraints. SysML v2: Cameo's evaluator, plus `inputs` for what-if (rolled back). SysML v1: Simulation Toolkit if installed.
- `cameo_run_trade_study`: alternatives + criteria (`name`, `weight`, `goal` min/max, optional `limit`) → ranking and recommendation.
- `cameo_get_simulation_status` / `cameo_run_simulation` need Cameo Simulation Toolkit.

## Review
- `cameo_proof_model_text` → findings + `patchPlan`; apply what the user accepts with `cameo_apply_proofing_patch` (`dryRun` first).
- `cameo_list_methodology_packs`, `cameo_apply_methodology_pack`, `cameo_check_methodology_conformance`.
- `cameo_generate_review_packet` writes review.md, findings.json and model.sysml (v2) or diagram PNGs (v1) to a folder.
