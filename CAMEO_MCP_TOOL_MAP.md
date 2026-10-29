# Cameo MCP tool map

Generated from `schemas/mcp-tools-manifest.json`. 134 tools in 8 families. Every tool also takes `timeoutSeconds` (default 120).

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
| **Total** | **134** | |

## Server (`server`, 16 tools)

Connection, discovery, logging, undo/redo, UI actions.

| Tool | What it does | Arguments (* = required) |
|---|---|---|
| `cameo_status` | Check that Cameo and the MCP plugin are up: plugin and Cameo version, open project (name, dirty, Teamwork Cloud or local, whether an editing session is open) and number of open projects. Call this first. | — |
| `cameo_get_capabilities` | Tool counts per family and every installed Cameo plugin (id, name, version), to see which Cameo features are available. | — |
| `cameo_list_tools` | Search this server's tools by family and/or a word in the name or description. | `family`, `query` |
| `cameo_get_last_error` | Stack trace of the last unexpected plugin error, for debugging a failed call. | — |
| `cameo_get_system_logs` | Recent plugin log lines (every tool call and error is logged). | `lines` |
| `cameo_log` | Write a message to Cameo's Notification window so the user can follow what the agent is doing. | `message`*, `level` |
| `cameo_reset_session` | Cancel a Cameo editing session left open (for example by a failed script). Unsaved edits inside that session are discarded; only use it when no one is editing. | — |
| `cameo_undo` | Undo the last change in Cameo (each write tool call is one undoable step). | — |
| `cameo_redo` | Redo the last undone change. | — |
| `cameo_get_command_history` | Names of recent undoable commands and what undo/redo would do next. | `limit` |
| `cameo_clear_command_history` | Clear Cameo's undo history. | — |
| `cameo_get_ui_state` | What the user is looking at: the active diagram, its selected symbols, and the elements selected in the containment tree. | — |
| `cameo_select_in_containment_tree` | Reveal and select an element in Cameo's containment tree. | `elementId`* |
| `cameo_list_ui_actions` | Search Cameo main-menu actions (id and label) for use with cameo_invoke_ui_action. | `query`, `limit` |
| `cameo_invoke_ui_action` | Run a Cameo main-menu action by id. Actions that open dialogs wait for the user. | `actionId`* |
| `cameo_poll_model_change_events` | Model changes (element and property changes) since a sequence number. The first call starts recording. | `sinceSeq` |

## Model (`model`, 29 tools)

Projects, elements, relationships, stereotypes and properties (UML / SysML v1).

| Tool | What it does | Arguments (* = required) |
|---|---|---|
| `cameo_get_project` | Active project: name, file or server location, dirty flag, primary model and top-level models, diagram count. | — |
| `cameo_list_open_projects` | All projects open in Cameo, which one is active, and whether each has unsaved changes. | — |
| `cameo_set_active_project` | Make another open project the active one (all tools work on the active project). | `project`* |
| `cameo_create_new_project` | Create a new project and make it active: a SysML v2 project from Cameo's SysML v2 template (default in the SysML v2 Community edition), an empty UML project, or any template; optionally save it to a file. | `kind`, `templatePath`, `filePath` |
| `cameo_open_project_file` | Open a local project file (.mdzip, .xml) and make it active. | `filePath`* |
| `cameo_save_project` | Save the active local project, or save it to a new file. Teamwork Cloud projects are saved with cameo_commit_project. | `filePath` |
| `cameo_close_project` | Close a project. Refuses if it has unsaved changes unless discardChanges is true. | `project`, `discardChanges` |
| `cameo_list_attached_modules` | Used projects/modules attached to the active project, and its shared packages. | — |
| `cameo_query_elements` | Find elements by type, stereotype and/or name inside a scope. Returns ids, names, types and qualified names. | `type`, `stereotype`, `name`, `regex`, `scopeId`, `recursive`, `limit` |
| `cameo_get_element` | Full details of one element: name, type, owner, stereotypes, documentation, all UML property values and tagged values. | `elementId`*, `includeRelationships` |
| `cameo_get_containment_tree` | Nested children of an element down to a depth. | `rootId`, `depth`, `limit` |
| `cameo_get_relationships` | Relationships attached to an element, with source and target. | `elementId`*, `direction`, `type` |
| `cameo_create_element` | Create one element. SysML types (Block, Requirement...) get their stereotype automatically; states go into the state machine's region. | `type`*, `name`, `parentId`, `documentation`, `stereotypes`, `properties` |
| `cameo_create_elements` | Create many elements and relationships in ONE undoable step. Entries can refer to each other with 'ref'. Use this to build a structure in one call. | `elements`*, `relationships` |
| `cameo_modify_element` | Change an element: rename, documentation, stereotypes, or any property/tagged value (type, multiplicity, aggregation, defaultValue, isAbstract...). | `elementId`*, `name`, `documentation`, `stereotypes`, `properties` |
| `cameo_batch_modify_elements` | Apply several modify_element changes in one undoable step. | `modifications`* |
| `cameo_move_element` | Move an element to a new owner. | `elementId`*, `newParentId`* |
| `cameo_delete_element` | Delete elements (and everything they own) from the model. | `elementId`, `elementIds` |
| `cameo_clone_element` | Deep-copy an element with its owned content. | `elementId`*, `targetParentId`, `increaseElementNames` |
| `cameo_convert_element_type` | Convert an element to another metaclass (e.g. Class to Interface), keeping its relationships. | `elementId`*, `targetType`* |
| `cameo_replace_element` | Replace every use of one element with another element. | `elementId`*, `replacementId`* |
| `cameo_reverse_relationship` | Swap source and target of a directed relationship. | `relationshipId`* |
| `cameo_reconnect_relationship` | Change the source and/or target of a relationship. | `relationshipId`*, `sourceId`, `targetId` |
| `cameo_create_relationship` | Create a relationship between two elements; the owner is chosen the way Cameo does (e.g. transitions go into the region, control flows into the activity). | `type`*, `sourceId`*, `targetId`*, `name`, `properties` |
| `cameo_apply_stereotype` | Apply a stereotype to an element. | `elementId`*, `stereotype`* |
| `cameo_remove_stereotype` | Remove a stereotype from an element. | `elementId`*, `stereotype`* |
| `cameo_set_tagged_values` | Set tagged values of the element's stereotypes (e.g. a requirement's Id and Text). | `elementId`*, `values`* |
| `cameo_set_transition_trigger` | Set a state machine transition's trigger (signal, call, change, time or any event), guard and effect. | `transitionId`*, `kind`, `signalId`, `operationId`, `expression`, `relative`, `guard`, `effect`, `replace` |
| `cameo_set_state_behaviors` | Set a state's entry, do and exit behaviors (existing behavior id, or {name, body, language} to create an opaque behavior). | `stateId`*, `entry`, `doActivity`, `exit` |

## Diagrams (`diagrams`, 27 tools)

Diagrams, symbols, paths, layout, export, relation maps, sequence messages.

| Tool | What it does | Arguments (* = required) |
|---|---|---|
| `cameo_list_diagram_types` | Diagram types this Cameo can create (UML, SysML, tables, matrices...) and the short aliases (bdd, ibd, act, stm, sd, uc, req, pkg, par). | — |
| `cameo_list_diagrams` | Diagrams in the project, optionally of one type or inside one owner. | `type`, `ownerId` |
| `cameo_create_diagram` | Create a diagram under an owner. | `type`*, `ownerId`, `name` |
| `cameo_open_diagram` | Open a diagram in Cameo. | `diagramId`* |
| `cameo_close_diagram` | Close the open tab of a diagram in Cameo. | `diagramId`* |
| `cameo_get_diagram_contents` | Every symbol on a diagram: presentationId, type, model element, bounds, parent, and path ends. | `diagramId`* |
| `cameo_add_to_diagram` | Show model elements on a diagram as symbols (one, or many via items). Use nestedPath for deep parts in an IBD. | `diagramId`*, `elementId`, `x`, `y`, `parentPresentationId`, `createRelationships`, `items` |
| `cameo_add_path_to_diagram` | Draw relationships on a diagram between symbols (found automatically if their elements are shown once). | `diagramId`*, `relationshipId`, `sourcePresentationId`, `targetPresentationId`, `paths` |
| `cameo_delete_shapes` | Remove symbols from a diagram (model elements stay). | `diagramId`*, `presentationIds`* |
| `cameo_move_shapes` | Move and/or resize symbols. | `diagramId`*, `moves`* |
| `cameo_reparent_shapes` | Move symbols inside another symbol (e.g. into a package or block frame). | `diagramId`*, `presentationIds`*, `parentPresentationId`*, `x`, `y`, `changeElementOwner` |
| `cameo_align_symbols` | Align or distribute symbols relative to the first one. | `diagramId`*, `presentationIds`*, `alignment`*, `spacing` |
| `cameo_layout_diagram` | Apply Cameo automatic layout. | `diagramId`*, `style` |
| `cameo_route_paths` | Set the bend points of paths. | `diagramId`*, `routes`* |
| `cameo_reset_label_positions` | Put path labels back to their default positions (all paths, or the given ones). | `diagramId`*, `presentationIds` |
| `cameo_get_shape_properties` | Display properties of a symbol (colors, fonts, compartments, show options) with their ids and values. | `diagramId`*, `presentationId`* |
| `cameo_set_shape_properties` | Change display properties of symbols. Keys are property ids or names from cameo_get_shape_properties; colors are "#RRGGBB". | `diagramId`*, `presentationIds`*, `properties`* |
| `cameo_copy_symbol_style` | Copy the display style of one symbol onto others. | `diagramId`*, `sourcePresentationId`*, `targetPresentationIds`* |
| `cameo_create_note` | Add a note, text box, rectangle or separator to a diagram, optionally anchored to symbols. | `diagramId`*, `kind`, `text`, `x`, `y`, `anchorTo` |
| `cameo_create_swimlanes` | Add activity partitions as swimlanes on an activity diagram (creates partitions that do not exist). | `diagramId`*, `partitions`*, `vertical` |
| `cameo_select_diagram_symbols` | Open a diagram and select symbols in it, to point the user at them. | `diagramId`*, `presentationIds`* |
| `cameo_set_diagram_grid` | Show/hide the grid, set its size, and snapping. | `diagramId`*, `show`, `size`, `snapShapes`, `snapPaths` |
| `cameo_export_diagram` | Export a diagram image to a file. | `diagramId`*, `filePath`*, `format`, `dpi`, `scalePercent` |
| `cameo_get_diagram_image` | Render a diagram and return it as an image so you can look at it. | `diagramId`*, `maxWidth`, `maxHeight`, `scalePercent` |
| `cameo_create_relation_map` | Create a Relation Map diagram centred on a context element, showing the elements related to it up to a depth (UML/SysML v1 projects; needs the Relation Map plugin). | `contextId`*, `ownerId`, `name`, `depth`, `scopeIds`, `elementTypeIds` |
| `cameo_refresh_relation_map` | Rebuild a Relation Map from the current model, optionally with a new context element or depth. | `diagramId`*, `contextId`, `depth`, `scopeIds`, `elementTypeIds` |
| `cameo_add_sequence_message` | Draw a message between two lifelines on a sequence diagram (creates the Message, its occurrences and the symbol). | `diagramId`*, `fromLifelineId`*, `toLifelineId`*, `name`, `sort`, `signatureId`, `afterMessageId`, `verticalGap` |

## Artifacts (`artifacts`, 16 tools)

Generic tables, dependency matrices, reports, attachments, hyperlinks.

| Tool | What it does | Arguments (* = required) |
|---|---|---|
| `cameo_create_table` | Create a Generic Table, optionally with row element types, scope and columns. | `name`, `ownerId`, `elementTypes`, `scopeIds`, `columnIds` |
| `cameo_get_table_contents` | Rows and cell values of a Generic Table. | `tableId`*, `limit` |
| `cameo_list_table_columns` | Column ids available for rows of a type. | `elementType`* |
| `cameo_add_table_columns` | Add columns to a Generic Table. | `tableId`*, `columnIds`* |
| `cameo_add_table_rows` | Add elements as rows of a Generic Table. | `tableId`*, `elementIds`* |
| `cameo_remove_table_rows` | Remove rows from a Generic Table (elements stay in the model). | `tableId`*, `elementIds`* |
| `cameo_export_table_csv` | Write a Generic Table's visible columns and rows to a CSV file. | `tableId`*, `filePath`* |
| `cameo_create_matrix` | Create a dependency matrix and set its row/column types, scopes and criteria. | `type`, `name`, `ownerId`, `rowTypes`, `columnTypes`, `rowScopeIds`, `columnScopeIds`, `dependencyCriteria`, `direction` |
| `cameo_get_matrix_contents` | Non-empty cells of a dependency matrix: row, column and the relationships behind each cell. | `matrixId`*, `limit` |
| `cameo_list_report_templates` | Report Wizard templates installed in Cameo. | `reload` |
| `cameo_attach_file` | Embed a file in the model as an «AttachedFile» comment under an owner. | `ownerId`*, `filePath`* |
| `cameo_extract_attached_file` | Write an embedded file back to a folder. | `attachedFileId`*, `directory`* |
| `cameo_add_hyperlink` | Add a hyperlink (web/file URL or link to another element) to an element. | `elementId`*, `url`, `targetElementId`, `text` |
| `cameo_list_hyperlinks` | Hyperlinks on an element. | `elementId`* |
| `cameo_remove_hyperlink` | Remove a hyperlink from an element. | `elementId`*, `url`* |
| `cameo_generate_report` | Generate a document with a Report Wizard template (docx, html, xlsx... as the template defines) for packages of a UML/SysML v1 project. | `template`*, `report`, `scopeIds`, `recursive`, `outputFile`* |

## Advanced (`advanced`, 14 tools)

Macros, validation, profiles, Teamwork Cloud.

| Tool | What it does | Arguments (* = required) |
|---|---|---|
| `cameo_execute_macro` | Run a Groovy, JavaScript, Python (Jython) or BeanShell script inside Cameo with 'project' and 'application' bound. Disabled unless the user allows macros in MCP > MCP Server Status. | `script`*, `language`, `session` |
| `cameo_list_validation_suites` | Validation suites available in the project (packages with «validationSuite») and their rule counts. | — |
| `cameo_run_validation` | Run a validation suite on the whole project or on scope elements; returns violations with severity, message, rule and element. | `suite`*, `scopeIds`, `minSeverity`, `limit` |
| `cameo_get_validation_results` | Results of one of the last 10 validation runs. | `runId`* |
| `cameo_list_profiles` | Profiles in the project (own and used) with stereotype counts. | — |
| `cameo_get_profile_details` | Stereotypes of a profile with their metaclasses and tag definitions. | `profileId`* |
| `cameo_create_stereotype` | Create a stereotype in a profile (or package) extending metaclasses. | `profileId`*, `name`*, `metaclasses`* |
| `cameo_create_tag_definition` | Add a tag definition (property) to a stereotype. | `stereotypeId`*, `name`*, `type`, `typeId`, `multiplicity` |
| `cameo_get_teamwork_project` | Teamwork Cloud info for the active project: branch, latest revision, number of elements locked by you. | — |
| `cameo_lock_elements` | Lock elements for editing in a Teamwork Cloud project. | `elementIds`*, `recursive` |
| `cameo_unlock_elements` | Release locks in a Teamwork Cloud project. | `elementIds`*, `recursive` |
| `cameo_get_locks` | Elements locked in the Teamwork Cloud project and by whom. | `limit` |
| `cameo_commit_project` | Commit the active Teamwork Cloud project to the server with a comment. | `comment`*, `tags` |
| `cameo_update_project` | Update the active Teamwork Cloud project to the latest server version. | — |

## Analysis (`analysis`, 11 tools)

Traceability, coverage, metrics, parametrics, trade studies, simulation.

| Tool | What it does | Arguments (* = required) |
|---|---|---|
| `cameo_get_traceability_graph` | Nodes and edges reachable from an element through relationships, up to a depth. | `elementId`*, `direction`, `depth`, `relationshipTypes` |
| `cameo_trace_coverage_analysis` | Requirement coverage in a scope: which requirements are satisfied and verified, percentages, and the unlinked ones. | `scopeId`, `includeDetails` |
| `cameo_find_circular_dependencies` | Cycles of dependencies/generalizations/directed relationships between elements in a scope. | `scopeId`, `relationshipTypes` |
| `cameo_get_orphan_elements` | Classifiers and packages in a scope that appear on no diagram and have no relationships. | `scopeId`, `limit` |
| `cameo_find_dead_end_flows` | Activity nodes with no outgoing flow (other than final nodes) or no incoming flow (other than initial nodes). | `activityId`* |
| `cameo_check_state_reachability` | States not reachable from the initial pseudostate, and non-final states with no way out. | `stateMachineId`* |
| `cameo_calculate_metrics` | Element counts by type and stereotype, relationships, diagrams and nesting depth in a scope. | `scopeId` |
| `cameo_evaluate_parametrics` | Evaluate parametrics: every attribute value expression and constraint under an element, with pass/fail per constraint. SysML v2 uses Cameo's built-in evaluator (no Simulation Toolkit needed) and accepts what-if inputs that are rolled back afterwards; SysML v1 uses the Simulation Toolkit when installed, otherwise lists equations and current values. | `elementId`*, `inputs`, `outputs` |
| `cameo_run_trade_study` | Rank alternatives by weighted, min-max normalized criteria read from their attribute values (v2 evaluated values or v1 default values). Supports limits that make an alternative infeasible. | `alternatives`*, `criteria`* |
| `cameo_get_simulation_status` | Whether Cameo Simulation Toolkit is installed, whether SysML v2 evaluation is available, and the SimulationConfig elements of a v1 project. | — |
| `cameo_run_simulation` | Run a Cameo Simulation Toolkit simulation (activity, state machine, block or SimulationConfig) and return output values. Needs Simulation Toolkit; runs off the UI thread. | `targetId`*, `configId`, `inputs`, `outputs` |

## Methodology (`methodology`, 6 tools)

Methodology packs, conformance, review packets, text proofing.

| Tool | What it does | Arguments (* = required) |
|---|---|---|
| `cameo_list_methodology_packs` | Built-in methodology packs (MagicGrid, SysML v2 starter, OOSEM): their package layout, the purpose of each package and the element kinds expected in it. | — |
| `cameo_apply_methodology_pack` | Create a methodology pack's package skeleton (with documented purposes) in the model; works for SysML v1 and v2 projects. | `pack`*, `ownerId`, `rootName` |
| `cameo_check_methodology_conformance` | Check a model against a methodology pack: which packages exist, which expected element kinds are missing, and a conformance score. | `pack`*, `rootId` |
| `cameo_proof_model_text` | Proofread element names and documentation: whitespace, repeated words, common misspellings, TODO/TBD placeholders, default names, naming conventions and missing documentation. Returns findings and a patch plan. | `scopeId`, `checkNaming`, `limit` |
| `cameo_apply_proofing_patch` | Apply name/documentation fixes (for example the patchPlan from cameo_proof_model_text) in one undoable step. | `patches`*, `dryRun` |
| `cameo_generate_review_packet` | Write a review packet to a folder: review.md (contents, validation, parametric results, proofing), findings.json, the SysML v2 text (v2) or diagram PNGs (v1). | `directory`*, `scopeId`, `maxDiagrams` |

## SysML v2 (`sysml2`, 15 tools)

SysML v2 (KerML) models in Cameo 2026x: text import/export, create, edit, connect, validate.

| Tool | What it does | Arguments (* = required) |
|---|---|---|
| `cameo_sysml2_list_roots` | Root namespaces of the SysML v2 project (one per imported text file plus the template's) with their top-level members. | `includeLibraries` |
| `cameo_sysml2_get_element` | Details of a SysML v2 element: kind, keyword, owner, documentation, types, specializations, multiplicity, value expression and its evaluated value, constraint result, members. | `elementId`*, `evaluate` |
| `cameo_sysml2_get_tree` | Containment tree of a SysML v2 model or element. | `rootId`, `depth` |
| `cameo_sysml2_find_elements` | Find SysML v2 elements by kind (keyword like 'part def', 'requirement' or metaclass like PartUsage) and/or name regex. | `kind`, `name`, `scopeId`, `limit` |
| `cameo_sysml2_create_element` | Create a SysML v2 element (any definition or usage: part def, part, port def, port, attribute, item, action, state, requirement, constraint, calc, connection, interface, allocation, use case, analysis, verification, package...) under an owner. Cameo parses the generated SysML text, so references resolve like in the text editor. | `kind`*, `name`, `ownerId`, `type`, `specializes`, `redefines`, `multiplicity`, `value`, `direction`, `isAbstract`, `shortName`, `documentation`, `body` |
| `cameo_sysml2_add_text` | Add any SysML v2 text (several elements, nested bodies, connections, states...) inside an owner. Names resolve as if the text were written in the owner. Fails without changing the model if the text has errors. | `text`*, `ownerId`, `allowErrors` |
| `cameo_sysml2_check_text` | Parse SysML v2 text and return syntax and name-resolution diagnostics without changing the model. | `text`*, `ownerId` |
| `cameo_sysml2_import_file` | Import a .sysml file into the project as a new root namespace. | `filePath`*, `allowErrors` |
| `cameo_sysml2_export_text` | SysML v2 textual notation of elements (default: the whole project model), optionally written to a file. | `elementIds`, `filePath` |
| `cameo_sysml2_set_property` | Change a SysML v2 element: name, short name, documentation, abstract, direction, types, add specializations, multiplicity, literal value or value expression. | `elementId`*, `name`, `shortName`, `documentation`, `isAbstract`, `direction`, `type`, `specializes`, `multiplicity`, `value`, `valueExpression` |
| `cameo_sysml2_delete_element` | Delete SysML v2 elements (with their memberships). | `elementIds`* |
| `cameo_sysml2_move_element` | Move a SysML v2 element to a new owner. | `elementId`*, `newOwnerId`* |
| `cameo_sysml2_get_relationships` | Relationships of a SysML v2 element: owned (typing, specialization, subsetting...) and incoming (who types, specializes, references or satisfies it). | `elementId`* |
| `cameo_sysml2_connect` | Connect two features in a SysML v2 owner: connection, interface, binding, flow, succession, allocation, satisfy or dependency. Ends are feature names/paths as seen from the owner (e.g. engine.shaft). | `ownerId`*, `kind`*, `source`*, `target`*, `name`, `type`, `itemType` |
| `cameo_sysml2_validate` | Check a SysML v2 model: unnamed definitions, untyped usages, duplicate names, requirements without satisfy or text, and constraints that evaluate to false. | `scopeId`, `limit` |
