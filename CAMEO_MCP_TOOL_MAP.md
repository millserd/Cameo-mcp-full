# Cameo MCP Unified Tool Map & Architecture Alignment (365 Tools — 2024x & 2026x)

## 1. Cross-Repository Alignment Matrix (`millserd/Cameo-mcp` + `ajhcs/cameo-mcp-bridge` + OpenAPI Discovery)

| Architectural Layer | [`millserd/Cameo-mcp`](https://github.com/millserd/Cameo-mcp) | [`ajhcs/cameo-mcp-bridge`](https://github.com/ajhcs/cameo-mcp-bridge) | Unified Alignment in `com.cameomcp.plugin` (v3.1.0) |
| :--- | :--- | :--- | :--- |
| **Java Package & Plugin ID** | `com.cameomcp.*` / `com.cameomcp.plugin` | separate bridge plugin ID | Uses `com.cameomcp.catalog`, `com.cameomcp.mcp`, `com.cameomcp.plugin`, `com.cameomcp.stdio`, and plugin ID `com.cameomcp.plugin` |
| **Catalog Organization** | 7 Catalog classes (`ServerTools`, `ModelTools`, `DiagramTools`, `ArtifactTools`, `AdvancedTools`, `AnalysisTools`, `Sysml2Tools`) — 183 tools | 16 handler families — 162 tools | Retains the exact 7 `com.cameomcp.catalog.*` classes and merges all 183 `millserd` tools + all 162 `ajhcs` tools + 60 OpenAPI tools = **365 unique tools** |
| **MCP Prompts & Resources** | `CatalogPrompts` (`model_system_structure`, `trace_requirements`, `review_model_quality`, `build_state_machine`) + `cameo://guide`, `cameo://tools` | Static README | Implements `prompts/list` for all 4 `CatalogPrompts` and `resources/list` for `cameo://guide`, `cameo://tools`, and `cameo://skill/cameo-mcp` |
| **In-App Cameo UI & Logging** | `McpMenuConfigurator` (top-level `MCP` menu), `McpStatusWindow`, `ClientConfigs`, `ConnectionTest`, `CameoLog` (`[Cameo MCP]`) | None | Full implementation of `McpMenuConfigurator`, `McpStatusWindow`, `ClientConfigs`, `ConnectionTest`, and `CameoLog` (`GUILog` + `md.log`) |
| **Security & Settings** | `Settings` (`~/.cameo-mcp/settings.properties`, `CAMEO_MCP_PORT`, `-Dcameo.mcp.port`) + `CallGuard` (Origin check, Bearer token, write lock, macro gate) | Localhost bind (`18740`) + EDT session wrapper | Implements `Settings` + `CallGuard` + project-scoped `SessionManager` EDT execution for 2024x and 2026x |
| **Stdio Launcher & CLI** | `StdioLauncher` (`--host`, `--port`, `--url`, `--token`, `--diagnose`, `--list`, `--describe`, `--call`) | Python `cameo_mcp.server` | Pure-Java `com.cameomcp.stdio.StdioLauncher` supporting all CLI inspection flags and `Mcp-Session-Id` stdio bridging |

## 2. Tool Count Breakdown by `com.cameomcp.catalog` Family

| Catalog Class | Family Key | `millserd/Cameo-mcp` Core Tools | `ajhcs/cameo-mcp-bridge` & Retroactive OpenAPI Additions | Unified Total |
| :--- | :--- | :---: | :---: | :---: |
| `ServerTools.java` | `server` | 11 | 8 | **19** |
| `ModelTools.java` | `model` | 34 | 18 | **52** |
| `DiagramTools.java` | `diagrams` | 35 | 31 | **66** |
| `ArtifactTools.java` | `artifacts` | 18 | 30 | **48** |
| `AdvancedTools.java` | `advanced` | 34 | 66 | **100** |
| `AnalysisTools.java` | `analysis` | 32 | 20 | **52** |
| `Sysml2Tools.java` | `sysml2` | 22 | 6 | **28** |
| **Total** | **7 Families** | **186** | **179** | **365 Tools** |

## 3. Complete 365-Tool Unified Specification

### 1. ServerTools (`server` family — Connection, Discovery, Undo/Redo, UI Actions & Logging) (19 Tools)
- **Catalog Class**: `com.cameomcp.catalog.ServerTools`
- **Cameo OpenAPI Packages**: `com.nomagic.magicdraw.core.Application, com.nomagic.magicdraw.core.GUILog, com.nomagic.magicdraw.commands.CommandHistory, com.nomagic.magicdraw.openapi.uml.SessionManager, com.nomagic.magicdraw.actions.ActionsManager`

| Tool Name | Description | Parameters (`inputSchema`) |
| :--- | :--- | :--- |
| `cameo_status` | Check that Cameo and the MCP plugin are up. Returns plugin version, Cameo product/version, endpoint and port, connected clients, whether a project is open, and recent errors. Call this first in every session. | `includeMetrics`: boolean (optional) |
| `cameo_get_capabilities` | Machine-readable capability report: tool families and counts, which optional Cameo plugins (Dependency Matrix, Generic Table, Relation Map, Simulation, Teamwork, Report Wizard, DataHub, SysML v2) are present, whether macros are allowed, and supported MCP protocol versions. | `family`: string (optional) |
| `cameo_probe_bridge` | Self-test of the MCP connection from inside Cameo: checks the HTTP endpoint answers /status and /health paths, bound port and host, Swing EDT responsiveness, and SessionManager editing-session state. | `timeoutMs`: integer (optional) |
| `cameo_list_tools` | Search this server's tool catalog by family (server, model, diagrams, artifacts, advanced, analysis, sysml2) and/or keyword. Returns name, family, kind (read/write/destructive) and summary description. | `family`: string (optional), `query`: string (optional) |
| `cameo_list_families` | Summarize all 7 catalog tool families (server, model, diagrams, artifacts, advanced, analysis, sysml2) and their tool counts. | `{}` (none) |
| `cameo_list_prompts` | List pre-configured MCP workflow prompts registered in CatalogPrompts (model_system_structure, trace_requirements, review_model_quality, build_state_machine). | `{}` (none) |
| `cameo_get_last_error` | Return the stack trace and context of the latest plugin or OpenAPI execution exception. | `{}` (none) |
| `cameo_reset_session` | Recovery: cancel (roll back) a Cameo SessionManager editing session left open by a failed macro or interrupted write, so new writes can proceed. | `projectId`: string (optional) |
| `cameo_undo` | Undo the most recent model change in Cameo via CommandHistory.undo() (same as Edit > Undo). Each MCP write tool is one undoable step. | `steps`: integer (optional, default 1) |
| `cameo_redo` | Redo the most recently undone model change via CommandHistory.redo() (same as Edit > Redo). | `steps`: integer (optional, default 1) |
| `cameo_log` | Write a message prefixed with [Cameo MCP] to Cameo's Notification window (GUILog) and md.log file so the engineer watching Cameo can follow what the agent is doing. | `message`: string (required), `level`: string (info|warn|error) |
| `cameo_get_command_history` | Inspect the Undo and Redo command stack names and depth in `project.getCommandHistory()`. [Retroactive OpenAPI Discovery] | `limit`: integer (optional) |
| `cameo_clear_command_history` | Clear the project undo/redo command history buffer to reclaim memory after massive batch operations. [Retroactive OpenAPI Discovery] | `{}` (none) |
| `cameo_batch_create_elements` | Create N elements and apply stereotypes/tagged values in a single atomic SessionManager transaction (eliminating N-roundtrip latency). [Retroactive OpenAPI Discovery] | `operations`: array<{tempId, type, name, parentId, stereotypes, taggedValues, documentation}> (required) |
| `cameo_batch_create_relationships` | Create N relationships in a single atomic SessionManager transaction (supporting tempId references from batch creation). [Retroactive OpenAPI Discovery] | `relationships`: array<{type, sourceId, targetId, ownerId, name, stereotypes}> (required) |
| `cameo_list_ui_actions` | Search registered Cameo menu, toolbar, and diagram `NMAction` IDs and names across `ActionsManager`. [Retroactive OpenAPI Discovery] | `query`: string (optional) |
| `cameo_invoke_ui_action` | Invoke a registered Cameo `NMAction` by action ID on the Swing EDT (with safety blocklist against modal blocking dialogs). [Retroactive OpenAPI Discovery] | `actionId`: string (required) |
| `cameo_select_in_containment_tree` | Expand and highlight one or more elements in the Cameo Containment Tree via `Browser.getContainmentTree().openNode(element, true)`. [Retroactive OpenAPI Discovery] | `elementIds`: array<string> (required) |
| `cameo_write_gui_log` | Publish a plain-text or HTML clickable message to the Cameo Messages/Notification Log window via `Application.getInstance().getGUILog().log()` and `NotificationManager`. [Retroactive OpenAPI Discovery] | `message`: string (required), `severity`: string (info|warning|error), `elementIdToLink`: string (optional) |

### 2. ModelTools (`model` family — Projects, Elements, Stereotypes, Properties, Relationships & State Semantics) (52 Tools)
- **Catalog Class**: `com.cameomcp.catalog.ModelTools`
- **Cameo OpenAPI Packages**: `com.nomagic.magicdraw.core.Project, com.nomagic.magicdraw.core.project.ProjectsManager, com.nomagic.uml2.impl.ElementsFactory, com.nomagic.magicdraw.openapi.uml.ModelElementsManager, com.nomagic.magicdraw.uml.Finder, com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper, com.nomagic.uml2.ext.jmi.helpers.TagsHelper, com.nomagic.magicdraw.copypaste.CopyPasting, com.nomagic.magicdraw.uml.Refactoring`

| Tool Name | Description | Parameters (`inputSchema`) |
| :--- | :--- | :--- |
| `cameo_get_project` | Get the open project: name, filePath, isRemote (Teamwork Cloud), isDirty (unsaved changes), projectKind, primaryModelId, and primaryModelName. | `{}` (none) |
| `cameo_save_project` | Save the open project to its existing location via ProjectsManager.saveProject (same as File > Save). | `silent`: boolean (optional) |
| `cameo_get_ui_state` | Inspect the live Cameo UI: project, activeDiagram (id, name, type, owner), selectedElements (deduplicated union of diagram symbol selection and containment-tree selection), and active perspectives/views. | `includeProperties`: boolean (optional) |
| `cameo_get_active_diagram` | Get the diagram currently open and focused in Cameo as a compact payload: activeDiagram {id, name, type, owner} plus warnings. | `{}` (none) |
| `cameo_get_ui_selection` | Get what the user has selected: selectedElements (model elements), selectedPresentations (diagram symbols with presentationId), and browserSelection (containment tree node selection). | `{}` (none) |
| `cameo_query_elements` | Search model elements by metaclass, name, scope, and stereotype, sorted by type, name, id and paged. | `type`: string (optional), `name`: string (optional), `packageId`: string (optional), `stereotype`: string (optional), `recursive`: boolean (optional), `limit`: integer (optional), `offset`: integer (optional), `view`: string (compact|full) |
| `cameo_get_element` | Get full details of one element: id, type, humanType, name, qualifiedName, owner, stereotypes, documentation, taggedValues, type/multiplicity/aggregation for properties, target/source for relationships, diagrams inside, and child elements. | `elementId`: string (required) |
| `cameo_get_containment_tree` | Nested containment tree (id, type, name, stereotypes, children) from a root down to depth levels; nodes at the depth limit report childCount. | `rootId`: string (optional), `maxDepth`: integer (optional) |
| `cameo_list_containment_children` | List the immediate children of one element, sorted by name and paged, with totalChildren, matchedChildren, hasMore, and nextOffset. | `parentId`: string (required), `typeFilter`: string (optional), `limit`: integer (optional), `offset`: integer (optional) |
| `cameo_create_element` | Create one model element under parentId and return it. | `type`: string (required), `name`: string (required), `parentId`: string (required), `documentation`: string (optional), `stereotypes`: array<string> (optional) |
| `cameo_create_elements` | Bulk-create many elements and relationships in ONE undoable SessionManager step, all-or-nothing. | `elements`: array<object> (required), `relationships`: array<object> (optional) |
| `cameo_modify_element` | Rename an element and/or replace its documentation (first owned Comment, created if missing). | `elementId`: string (required), `name`: string (optional), `documentation`: string (optional), `isAbstract`: boolean (optional) |
| `cameo_move_element` | Move (reparent) an element to another owner via ModelElementsManager.moveElement, keeping its id, relationships, and diagram symbols. | `elementId`: string (required), `newParentId`: string (required) |
| `cameo_delete_element` | Permanently delete an element with everything it owns; relationships attached to it and its diagram symbols are removed cleanly. | `elementId`: string (required) |
| `cameo_apply_stereotype` | Apply a stereotype to an element (e.g. block, requirement, valueType, testCase, rationale). | `elementId`: string (required), `stereotypeName`: string (required), `profileName`: string (optional) |
| `cameo_remove_stereotype` | Unapply a stereotype from an element via StereotypesHelper.removeStereotype. Its tagged values are removed with it. | `elementId`: string (required), `stereotypeName`: string (required) |
| `cameo_set_tagged_values` | Set tagged values of a stereotype already applied to the element. | `elementId`: string (required), `stereotypeName`: string (required), `values`: object (required) |
| `cameo_set_stereotype_metaclasses` | Set the UML metaclasses a stereotype extends (its base classes) with Cameo's native API. | `stereotypeId`: string (required), `metaclasses`: array<string> (required) |
| `cameo_apply_profile` | Apply a profile to a package or model so its stereotypes can be used inside it. | `profileId`: string (required), `targetPackageId`: string (required) |
| `cameo_create_relationship` | Create a relationship from sourceId to targetId (Association, DirectedAssociation, Composition, Generalization, Dependency, ControlFlow, ObjectFlow, Transition, Connector, InformationFlow, ItemFlow, Allocate, Satisfy, Derive, Refine, Trace, Verify, Include, Extend). | `type`: string (required), `sourceId`: string (required), `targetId`: string (required), `ownerId`: string (optional), `name`: string (optional), `sourcePartWithPortId`: string (optional), `targetPartWithPortId`: string (optional), `conveyedIds`: array<string> (optional), `realizingConnectorId`: string (optional) |
| `cameo_get_element_relationships` | Find all relationships attached to an element, incoming, outgoing, or both (alias: cameo_get_relationships). | `elementId`: string (required), `direction`: string (incoming|outgoing|both), `typeFilter`: string (optional) |
| `cameo_reconnect_relationship` | Change the client (source) or supplier (target) end of an existing relationship without recreating it. | `relationshipId`: string (required), `newSourceId`: string (optional), `newTargetId`: string (optional) |
| `cameo_delete_relationship` | Delete a relationship by id, leaving the connected elements intact. | `relationshipId`: string (required) |
| `cameo_set_property_type` | Set or clear the classifier type of a Property, Port, FlowProperty, Pin, or ActivityParameterNode. | `propertyId`: string (required), `typeElementId`: string (optional) |
| `cameo_set_property_multiplicity` | Set multiplicity lower/upper (e.g. '1', '0..1', '*', '1..*') on a Property, Port, FlowProperty, Pin, or ActivityParameterNode. | `propertyId`: string (required), `lower`: string (optional), `upper`: string (required) |
| `cameo_set_property_default_value` | Set or clear the default value (LiteralString, LiteralReal, LiteralInteger, LiteralBoolean, or OpaqueExpression) of a Property or Port. | `propertyId`: string (required), `defaultValue`: string (optional) |
| `cameo_set_property_aggregation` | Set aggregation (none, shared, composite) on an Association end or Property. | `propertyId`: string (required), `aggregation`: string (none|shared|composite) |
| `cameo_set_specification` | Set structured fields of an element's Specification dialog that are not exposed by set_property_* tools. | `elementId`: string (required), `properties`: object (required) |
| `cameo_get_specification` | Read raw specification features and properties of an element in structured JSON form. | `elementId`: string (required) |
| `cameo_find_usages` | Search where an element is used or referenced throughout the project. | `elementId`: string (required) |
| `cameo_find_by_name` | Quick fuzzy or exact name search across the open project via Finder.byNameAllRecursively(). | `name`: string (required), `exactMatch`: boolean (optional), `scopeId`: string (optional) |
| `cameo_clone_element` | Deep copy an element and its subtree under a new parent via CopyPasting.copyPasteElement. | `elementId`: string (required), `targetParentId`: string (required), `increaseElementNames`: boolean (optional) |
| `cameo_convert_element_type` | Refactor/convert an element to a compatible metaclass or stereotyped element via Refactoring.Converting. | `elementId`: string (required), `targetType`: string (required), `preserveStereotypes`: boolean (optional) |
| `cameo_batch_modify_elements` | Modify names, documentation, tagged values, or parent owners across multiple elements in one atomic transaction. | `modifications`: array<object> (required) |
| `cameo_get_relationships` | Query incoming and outgoing relationships for an element with optional type and direction filters. [ajhcs/cameo-mcp-bridge Parity] | `elementId`: string (required), `direction`: string (incoming|outgoing|both), `typeFilter`: string (optional) |
| `cameo_get_transition_triggers` | Read explicit trigger, guard constraint, and effect behavior semantics for one state transition. [ajhcs/cameo-mcp-bridge Parity] | `transitionId`: string (required) |
| `cameo_set_transition_trigger` | Create or replace a SignalEvent, ChangeEvent, TimeEvent, or CallEvent trigger and guard on a transition. [ajhcs/cameo-mcp-bridge Parity] | `transitionId`: string (required), `eventType`: string (SignalEvent|ChangeEvent|TimeEvent|CallEvent), `eventTargetId`: string (optional), `expression`: string (optional), `guard`: string (optional) |
| `cameo_get_state_behaviors` | Read structured entry, doActivity, and exit Behavior payloads on a State element. [ajhcs/cameo-mcp-bridge Parity] | `stateId`: string (required) |
| `cameo_set_state_behaviors` | Create or replace structured entry, doActivity, and exit OpaqueBehavior/Activity payloads on a State. [ajhcs/cameo-mcp-bridge Parity] | `stateId`: string (required), `entry`: string (optional), `doActivity`: string (optional), `exit`: string (optional), `language`: string (optional) |
| `cameo_set_usecase_subject` | Set or clear the UML subject Classifier list for a UseCase element. [ajhcs/cameo-mcp-bridge Parity] | `useCaseId`: string (required), `subjectIds`: array<string> (required) |
| `cameo_clone_elements_with_views` | Clone a collection of model elements and diagram symbols together via `CopyPasting.copyPasteElements(objects, dataParent, viewParent, withNewData, increaseNames)`. [Retroactive OpenAPI Discovery] | `objectIds`: array<string> (required), `dataParentId`: string (required), `viewParentId`: string (optional), `withNewData`: boolean (optional) |
| `cameo_copy_paste_symbol_style` | Copy visual styling from a source presentation element onto target symbols via `CopyPasting.copyPasteSymbolsStyle(source, targets)`. [Retroactive OpenAPI Discovery] | `diagramId`: string (required), `sourcePresentationId`: string (required), `targetPresentationIds`: array<string> (required) |
| `cameo_refactor_convert_element` | Convert an element to another metaclass/type while preserving relationships and IDs via `Refactoring.Converting.convert(element, ConvertElementInfo)`. [Retroactive OpenAPI Discovery] | `elementId`: string (required), `targetType`: string (required), `preserveStereotypes`: boolean (optional) |
| `cameo_refactor_replace_element` | Replace all references and usages of one element with another via `Refactoring.Replacing.replace(elementToReplace, replacement, rules)`. [Retroactive OpenAPI Discovery] | `elementToReplaceId`: string (required), `replacementElementId`: string (required) |
| `cameo_refactor_reverse_relationship` | Reverse the source/target direction of a directed relationship or flow via `Refactoring.ReverseRelationship.reverse(relationship)`. [Retroactive OpenAPI Discovery] | `relationshipId`: string (required) |
| `cameo_refactor_extract_model_fragment` | Extract selected elements/nodes into a new reusable element or sub-activity/sub-machine via `Refactoring.Extracting`. [Retroactive OpenAPI Discovery] | `sourceElementIds`: array<string> (required), `targetContainerName`: string (required) |
| `cameo_create_new_project` | Create a new blank Cameo project via `ProjectsManager.createProject()` and optionally apply the SysML profile. [Retroactive OpenAPI Discovery] | `projectName`: string (optional), `applySysmlProfile`: boolean (optional) |
| `cameo_open_project_file` | Open a local `.mdzip` or `.xml` project file via `ProjectDescriptorsFactory.createProjectDescriptor(URI)` and `ProjectsManager.loadProject`. [Retroactive OpenAPI Discovery] | `filePath`: string (required), `silent`: boolean (optional) |
| `cameo_close_project` | Close an open project cleanly via `ProjectsManager.closeProject(project)`. [Retroactive OpenAPI Discovery] | `saveFirst`: boolean (optional) |
| `cameo_save_project_as` | Save or export the active project to a new `.mdzip` file location via `ProjectsManager.saveProject(descriptor, silent)`. [Retroactive OpenAPI Discovery] | `targetFilePath`: string (required) |
| `cameo_list_attached_modules` | List all attached modules/usages (`.mdzip` libraries and profiles), versions, and shared packages via `ProjectUtilities.getAttachedProjects(project)`. [Retroactive OpenAPI Discovery] | `{}` (none) |
| `cameo_find_shared_packages` | Return all shared and re-shared packages across the primary project and attached modules via `ProjectUtilities.getSharedPackagesIncludingResharedRecursively`. [Retroactive OpenAPI Discovery] | `{}` (none) |

### 3. DiagramTools (`diagrams` family — Diagram Lifecycle, Symbols, Paths, Layout, Compartments & Export) (66 Tools)
- **Catalog Class**: `com.cameomcp.catalog.DiagramTools`
- **Cameo OpenAPI Packages**: `com.nomagic.magicdraw.openapi.uml.PresentationElementsManager, com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement, com.nomagic.magicdraw.uml.symbols.CompartmentManager, com.nomagic.magicdraw.export.image.ImageExporter`

| Tool Name | Description | Parameters (`inputSchema`) |
| :--- | :--- | :--- |
| `cameo_list_diagrams` | List all diagrams in the open project or under a specific package scope. | `packageId`: string (optional), `typeFilter`: string (optional) |
| `cameo_open_diagram` | Open a diagram in Cameo's main workspace tab via DiagramPresentationElement.open(). | `diagramId`: string (required) |
| `cameo_close_diagram` | Close an open diagram tab in Cameo. | `diagramId`: string (required) |
| `cameo_get_diagram_contents` | Retrieve the structure, symbols, positions, dimensions, and path lines of a diagram. | `diagramId`: string (required), `includeNested`: boolean (optional) |
| `cameo_create_diagram` | Create a new diagram of a specified type (BDD, IBD, Requirement Diagram, StateMachine, Activity, Sequence, UseCase, Package, Parametric Diagram, RelationMap, Content Diagram). | `type`: string (required), `name`: string (required), `parentId`: string (required), `openInUi`: boolean (optional) |
| `cameo_delete_diagram` | Delete a diagram element and its layout representations. | `diagramId`: string (required) |
| `cameo_export_diagram` | Render a diagram to PNG, SVG, EMF, or JPG image file on disk via ImageExporter.export. | `diagramId`: string (required), `filePath`: string (required), `format`: string (png|svg|jpg|emf) |
| `cameo_get_diagram_image_base64` | Render a diagram to base64 image payload directly for inline vision analysis (alias: cameo_get_diagram_image). | `diagramId`: string (required), `format`: string (png|jpeg|svg), `maxWidth`: integer (optional), `maxHeight`: integer (optional) |
| `cameo_add_symbol_to_diagram` | Display a model element as a new symbol on a diagram at specified (x, y) coordinates (alias: cameo_add_to_diagram). | `diagramId`: string (required), `elementId`: string (required), `x`: integer (optional), `y`: integer (optional), `parentPresentationId`: string (optional) |
| `cameo_add_symbols_to_diagram` | Bulk-populate multiple model elements onto a diagram in a single transaction. | `diagramId`: string (required), `items`: array<{elementId, x, y, parentPresentationId}> (required) |
| `cameo_remove_symbol_from_diagram` | Remove a symbol presentation from a diagram without deleting the underlying model element (alias: cameo_delete_shapes). | `diagramId`: string (required), `presentationIds`: array<string> (required) |
| `cameo_layout_diagram` | Apply automatic layout algorithms (hierarchical, orthogonal, tree, organic, SysML2) to a diagram (alias: cameo_auto_layout). | `diagramId`: string (required), `layouterType`: string (optional) |
| `cameo_set_symbol_bounds` | Resize or reposition a diagram symbol presentation via reshapeShapeElement (alias: cameo_move_shapes). | `diagramId`: string (required), `presentationId`: string (required), `x`: integer (required), `y`: integer (required), `width`: integer (optional), `height`: integer (optional) |
| `cameo_set_symbol_style` | Modify color, font, fill, line style, or display properties of a diagram symbol (alias: cameo_set_shape_properties). | `diagramId`: string (required), `presentationId`: string (required), `properties`: object (required) |
| `cameo_add_path_to_diagram` | Display a relationship as a line connector (path symbol) between two diagram symbols (alias: cameo_add_diagram_paths). | `diagramId`: string (required), `relationshipId`: string (required), `clientPresentationId`: string (required), `supplierPresentationId`: string (required) |
| `cameo_remove_path_from_diagram` | Remove a path presentation line from a diagram. | `diagramId`: string (required), `pathPresentationId`: string (required) |
| `cameo_reroute_path` | Adjust path connector routing points, endpoints, or path style (alias: cameo_route_paths). | `diagramId`: string (required), `pathPresentationId`: string (required), `breakpoints`: array<{x, y}> (required), `resetLabels`: boolean (optional) |
| `cameo_get_symbol_properties` | Read presentation-level visual properties of a symbol (alias: cameo_get_shape_properties). | `diagramId`: string (required), `presentationId`: string (required) |
| `cameo_set_diagram_grid` | Toggle or set grid spacing and snap-to-grid settings on a diagram. | `diagramId`: string (required), `showGrid`: boolean (optional), `snapToGrid`: boolean (optional), `gridSize`: integer (optional) |
| `cameo_zoom_diagram` | Adjust zoom scale factor or fit-to-window on an active diagram. | `diagramId`: string (required), `zoomFactor`: number (optional), `fitToWindow`: boolean (optional) |
| `cameo_center_symbol` | Scroll and center the diagram view on a specific symbol or element. | `diagramId`: string (required), `presentationId`: string (required) |
| `cameo_select_diagram_symbols` | Highlight and select specific symbols in the open diagram tab. | `diagramId`: string (required), `presentationIds`: array<string> (required) |
| `cameo_clear_diagram_selection` | Deselect all active symbol selections on the open diagram. | `diagramId`: string (required) |
| `cameo_create_shape_on_diagram` | Add non-model shape elements (rectangles, text boxes, notes, separator lines, images) to a diagram. | `diagramId`: string (required), `kind`: string (note|textbox|rectangle|separator|image), `text`: string (optional), `x`: integer (optional), `y`: integer (optional) |
| `cameo_create_note_anchor` | Connect a Comment/Note box symbol to a target symbol with an anchor line via PresentationElementsManager.connectNote. | `diagramId`: string (required), `notePresentationId`: string (required), `targetPresentationId`: string (required) |
| `cameo_get_diagram_legend` | Read or inspect applied legend elements and legend items on a diagram. | `diagramId`: string (required) |
| `cameo_apply_diagram_legend` | Apply a Legend and its dynamic color-coding/adornment rules to a diagram. | `diagramId`: string (required), `legendId`: string (required) |
| `cameo_group_symbols` | Group multiple diagram symbols into a visual group container. | `diagramId`: string (required), `presentationIds`: array<string> (required) |
| `cameo_ungroup_symbols` | Ungroup a container symbol group on a diagram. | `diagramId`: string (required), `groupPresentationId`: string (required) |
| `cameo_order_symbols` | Change z-order depth (bringToFront, sendToBack, bringForward, sendBackward) of symbols on a diagram. | `diagramId`: string (required), `presentationIds`: array<string> (required), `orderAction`: string (bringToFront|sendToBack|bringForward|sendBackward) |
| `cameo_align_symbols` | Align selected diagram symbols horizontally, vertically, or distribute evenly. | `diagramId`: string (required), `presentationIds`: array<string> (required), `alignment`: string (left|center|right|top|middle|bottom|distributeHorizontal|distributeVertical) |
| `cameo_resize_symbols_to_fit` | Auto-fit symbol sizes to fit their internal text or compartment contents. | `diagramId`: string (required), `presentationIds`: array<string> (required) |
| `cameo_toggle_compartment_visibility` | Show or hide compartments (properties, operations, ports, constraints, tagged values, allocations) on a symbol (alias: cameo_set_shape_compartments). | `diagramId`: string (required), `presentationId`: string (required), `compartments`: object (required) |
| `cameo_nest_symbol` | Nest a symbol visually inside a parent container symbol on a diagram (alias: cameo_reparent_shapes). | `diagramId`: string (required), `presentationId`: string (required), `newParentPresentationId`: string (required), `x`: integer (optional), `y`: integer (optional) |
| `cameo_get_diagram_frame` | Read or update the diagram frame boundaries and header properties. | `diagramId`: string (required) |
| `cameo_list_diagram_types` | List validated diagram request tokens, aliases, and native Cameo DiagramTypeConstants. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_add_to_diagram` | Place a model element on a diagram canvas via PresentationElementsManager.createShapeElement and return its presentationId. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `elementId`: string (required), `x`: integer (optional), `y`: integer (optional), `parentPresentationId`: string (optional), `createRelationships`: boolean (optional) |
| `cameo_get_diagram_image` | Export a diagram image as base64 PNG/JPEG with optional metadata-only, resize, and transcode controls. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `format`: string (png|jpeg|svg), `maxWidth`: integer (optional), `maxHeight`: integer (optional), `includeImage`: boolean (optional) |
| `cameo_auto_layout` | Apply Cameo's built-in diagram auto-layout via DiagramPresentationElement.layout. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `layouterType`: string (optional) |
| `cameo_list_diagram_shapes` | List diagram shapes recursively with paging, filtering, bounds, summary counts, and presentation IDs. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `includeNested`: boolean (optional) |
| `cameo_get_shape_properties` | Read the current display PropertyManager properties of one diagram shape. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `presentationId`: string (required) |
| `cameo_move_shapes` | Reposition and resize shapes on a diagram via PresentationElementsManager.reshapeShapeElement with per-item results. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `moves`: array<{presentationId, x, y, width, height}> (required) |
| `cameo_delete_shapes` | Remove shapes from a diagram via PresentationElementsManager.deletePresentationElement (model elements preserved). [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `presentationIds`: array<string> (required) |
| `cameo_add_diagram_paths` | Draw relationship paths between shapes on a diagram via PresentationElementsManager.createPathElement. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `paths`: array<{relationshipId, clientPresentationId, supplierPresentationId}> (required) |
| `cameo_set_shape_properties` | Set display properties (colors, fonts, compartment visibility) via cloned PropertyManager with receipts. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `presentationId`: string (required), `properties`: object (required) |
| `cameo_set_shape_compartments` | Apply normalized compartment visibility controls to one shape via CompartmentManager. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `presentationId`: string (required), `compartments`: object (required) |
| `cameo_set_transition_label_presentation` | Apply a high-level state-transition label preset (show trigger, guard, effect) on a State Machine diagram. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `pathPresentationId`: string (optional), `preset`: string (required) |
| `cameo_set_item_flow_label_presentation` | Apply a high-level item-flow/information-flow label preset for SysML IBD cleanup. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `pathPresentationId`: string (optional), `preset`: string (required) |
| `cameo_set_allocation_compartment_presentation` | Apply a high-level allocation/full-port compartment presentation preset for SysML BDD/IBD cleanup. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `presentationId`: string (optional), `showAllocatedElements`: boolean (required) |
| `cameo_repair_hidden_labels` | Auto-show hidden path and port labels using diagram-type-aware native repair defaults. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `dryRun`: boolean (optional) |
| `cameo_repair_label_positions` | Reset overlapping path labels via PresentationElementsManager.resetLabelPositions with dry-run receipts. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `dryRun`: boolean (optional) |
| `cameo_repair_conveyed_item_labels` | Force conveyed-item/item-flow labels visible on eligible connector/flow paths. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required) |
| `cameo_normalize_compartment_presets` | Normalize compartment and full-port visibility across all shapes on a diagram by diagram type. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `preset`: string (compact|standard|detailed) |
| `cameo_prune_diagram_presentations` | Remove unwanted auto-displayed symbols while preserving caller-requested presentation IDs. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `keepPresentationIds`: array<string> (required) |
| `cameo_prune_path_decorations` | Remove cluttered child path decorations such as association end role/multiplicity labels. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `pathPresentationIds`: array<string> (optional) |
| `cameo_reparent_shapes` | Move existing presentation elements under new container shapes via PresentationElementsManager.movePresentationElement. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `presentationId`: string (required), `newParentPresentationId`: string (required), `x`: integer (optional), `y`: integer (optional) |
| `cameo_route_paths` | Update path breakpoints, client/supplier connection points, and label reset behavior via changePathPoints. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `pathPresentationId`: string (required), `breakpoints`: array<{x, y}> (required), `resetLabels`: boolean (optional) |
| `cameo_rename_diagram` | Rename a Diagram element and update its DiagramPresentationElement header. [Retroactive OpenAPI Discovery] | `diagramId`: string (required), `newName`: string (required) |
| `cameo_open_close_diagram_tab` | Open, focus, or close a diagram tab in Cameo's main GUI via `DiagramPresentationElement.open()` / `ProjectDiagramWindowsManager`. [Retroactive OpenAPI Discovery] | `diagramId`: string (required), `action`: string (open|close|focus) |
| `cameo_export_diagram_to_file` | Export a diagram directly to disk as PNG, SVG, EMF, JPEG, or PDF via `ImageExporter.export(dpe, format, file)` without base64 token overhead. [Retroactive OpenAPI Discovery] | `diagramId`: string (required), `filePath`: string (required), `format`: string (png|svg|jpg|emf) |
| `cameo_create_sequence_message` | Create a positioned Sequence Diagram message (synchCall, asynchCall, asynchSignal, reply, createMessage, deleteMessage, or recursive self-message) with explicit `insertAfter` and `verticalGap` via `PresentationElementsManager.createSequenceMessage`. [Retroactive OpenAPI Discovery] | `diagramId`: string (required), `fromLifelinePresentationId`: string (required), `toLifelinePresentationId`: string (required), `name`: string (required), `sort`: string (synchCall|asynchCall|asynchSignal|reply|createMessage|deleteMessage), `recursive`: boolean (optional), `insertAfterMessageId`: string (optional), `verticalGap`: integer (optional), `signalOrOperationId`: string (optional) |
| `cameo_create_lost_or_found_message` | Create a Lost or Found sequence message symbol on a lifeline via `PresentationElementsManager.createLostMessage` / `createFoundMessage`. [Retroactive OpenAPI Discovery] | `diagramId`: string (required), `lifelinePresentationId`: string (required), `kind`: string (lost|found), `name`: string (required), `verticalGap`: integer (optional) |
| `cameo_create_path_to_self` | Create a self-transition or self-relationship shape element when client == supplier via `PresentationElementsManager.createPathToSelfShapeElement`. [Retroactive OpenAPI Discovery] | `diagramId`: string (required), `relationshipId`: string (required), `clientPresentationId`: string (required) |
| `cameo_create_swimlane` | Create horizontal and vertical ActivityPartition swimlanes on an Activity diagram via `PresentationElementsManager.createSwimlane`. [Retroactive OpenAPI Discovery] | `diagramId`: string (required), `horizontalPartitionIds`: array<string> (optional), `verticalPartitionIds`: array<string> (optional) |
| `cameo_create_deep_part_shape` | Create a nested IBD PartView symbol with explicit `nestedPath` property chain (dot-notation part hierarchy) via `PresentationElementsManager.createPartShape`. [Retroactive OpenAPI Discovery] | `diagramId`: string (required), `partPropertyId`: string (required), `parentPresentationId`: string (required), `nestedPathPropertyIds`: array<string> (optional), `x`: integer (optional), `y`: integer (optional) |
| `cameo_create_note_or_textbox` | Create a Note, TextBox, Separator, or ImageShape on a diagram and optionally anchor it to a target symbol via `createNote` / `connectNote` / `setText`. [Retroactive OpenAPI Discovery] | `diagramId`: string (required), `kind`: string (note|textbox|separator|image), `text`: string (optional), `targetPresentationId`: string (optional), `x`: integer (optional), `y`: integer (optional) |

### 4. ArtifactTools (`artifacts` family — Generic Tables, Dependency Matrices, Relation Maps, Reports, XMI, Attachments & Snapshots) (48 Tools)
- **Catalog Class**: `com.cameomcp.catalog.ArtifactTools`
- **Cameo OpenAPI Packages**: `com.nomagic.generictable.GenericTableManager, com.nomagic.magicdraw.dependencymatrix.MatrixManager, com.nomagic.magicdraw.visualization.relationshipmap, com.nomagic.magicdraw.magicreport.helper.TemplateHelper, com.nomagic.magicdraw.emfuml2xmi, com.nomagic.magicdraw.fileattachments.FileAttachmentsHelper`

| Tool Name | Description | Parameters (`inputSchema`) |
| :--- | :--- | :--- |
| `cameo_list_tables` | List all Generic Tables, Requirements Tables, or Custom Tables in the project (alias: cameo_list_generic_tables). | `packageId`: string (optional) |
| `cameo_get_table_contents` | Retrieve column definitions, row elements, and cell values from a table (alias: cameo_get_generic_table). | `tableId`: string (required) |
| `cameo_create_table` | Create a new Generic Table or Requirements Table artifact under an owner package (alias: cameo_create_generic_table). | `name`: string (required), `parentId`: string (required), `elementTypes`: array<string> (required), `scopeIds`: array<string> (optional), `columnIds`: array<string> (optional) |
| `cameo_add_table_row` | Add a model element as a row in a Generic Table via GenericTableManager. | `tableId`: string (required), `elementId`: string (required) |
| `cameo_remove_table_row` | Remove an element row from a Generic Table. | `tableId`: string (required), `elementId`: string (required) |
| `cameo_set_table_columns` | Configure the displayed columns/properties of a Generic Table. | `tableId`: string (required), `columnIds`: array<string> (required) |
| `cameo_export_table` | Export Generic Table data to CSV, Excel (XLSX), or JSON. | `tableId`: string (required), `filePath`: string (required), `format`: string (csv|xlsx|json) |
| `cameo_list_matrices` | List all Dependency Matrices or SysML Allocation/Satisfy/Derive/Refine Matrices in the project. | `packageId`: string (optional) |
| `cameo_get_matrix_contents` | Retrieve row elements, column elements, and cell relation intersections from a matrix (alias: cameo_get_matrix). | `matrixId`: string (required) |
| `cameo_create_matrix` | Create a new Dependency/SysML Matrix with row and column scopes and types. | `kind`: string (required), `name`: string (required), `parentId`: string (required), `rowScopeIds`: array<string> (optional), `columnScopeIds`: array<string> (optional), `rowTypes`: array<string> (optional), `columnTypes`: array<string> (optional) |
| `cameo_export_matrix` | Export Dependency Matrix data to CSV or Excel. | `matrixId`: string (required), `filePath`: string (required), `format`: string (csv|xlsx) |
| `cameo_list_relation_maps` | List Relation Map diagrams in the project. | `{}` (none) |
| `cameo_get_relation_map_contents` | Read tree/graph expansion nodes and persisted GraphSettings from a Relation Map (alias: cameo_get_relation_map). | `diagramId`: string (required) |
| `cameo_create_relation_map` | Create a new Relation Map with specified context element, depth, and relation criteria. | `name`: string (required), `parentId`: string (required), `contextElementId`: string (optional), `depth`: integer (optional) |
| `cameo_generate_report` | Trigger Cameo Report Wizard to generate documents (Word DOCX, PDF, HTML, PPTX) using installed templates. | `templateName`: string (required), `outputPath`: string (required), `allowWrite`: boolean (optional), `scopeId`: string (optional) |
| `cameo_list_report_templates` | List available Report Wizard templates via TemplateHelper.listTemplates(). | `forceReload`: boolean (optional) |
| `cameo_export_model_xml` | Export package or model to XMI / Eclipse UML2 XML format (`com.nomagic.magicdraw.emfuml2xmi`). | `packageId`: string (optional), `outputPath`: string (required) |
| `cameo_import_model_xml` | Import model structures from external UML/SysML XMI files. | `filePath`: string (required), `targetPackageId`: string (optional) |
| `cameo_list_matrix_kinds` | List validated native matrix kinds (refine, derive, satisfy, allocation, dependency), aliases, and example type domains. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_get_matrix` | Read one supported native matrix with rows, columns, and populated cells. [ajhcs/cameo-mcp-bridge Parity] | `matrixId`: string (required) |
| `cameo_list_generic_tables` | List native Generic Table artifacts in the project. [ajhcs/cameo-mcp-bridge Parity] | `packageId`: string (optional) |
| `cameo_get_generic_table` | Read one Generic Table with row, column, and cell data via GenericTableManager. [ajhcs/cameo-mcp-bridge Parity] | `tableId`: string (required) |
| `cameo_list_generic_table_columns` | Discover possible Generic Table column IDs for an element or metaclass type. [ajhcs/cameo-mcp-bridge Parity] | `targetType`: string (required) |
| `cameo_create_generic_table` | Create and configure a native Generic Table artifact with scope, element types, and visible columns. [ajhcs/cameo-mcp-bridge Parity] | `name`: string (required), `parentId`: string (required), `elementTypes`: array<string> (required), `scopeIds`: array<string> (optional), `columnIds`: array<string> (optional) |
| `cameo_get_relation_map` | Read one Relation Map with persisted graph settings and context element. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required) |
| `cameo_configure_relation_map` | Update native graph settings (context, depth, grouping, layout) for an existing Relation Map. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `settings`: object (required) |
| `cameo_refresh_relation_map` | Explicitly request CATIA native Relation Map refresh on the EDT with timeout evidence. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `timeoutMs`: integer (optional) |
| `cameo_dump_relation_map_raw_settings` | Dump raw GraphSettings getter evidence for a Relation Map. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required) |
| `cameo_list_relation_map_presentations` | Inspect Relation Map presentation elements, node hierarchy, and edge counts. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required) |
| `cameo_list_relation_map_criteria_templates` | List bridge-known Relation Map criteria templates captured from live UI evidence. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_set_relation_map_criteria` | Apply Relation Map dependency criteria with receipts. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `criteriaTemplate`: string (required) |
| `cameo_expand_relation_map` | Request native node expansion on a Relation Map and report reflected support/evidence. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `nodeId`: string (optional) |
| `cameo_collapse_relation_map` | Request native node collapse on a Relation Map and report reflected support/evidence. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `nodeId`: string (optional) |
| `cameo_render_relation_map` | Render/export Relation Map image and node/edge evidence without implicit EDT-blocking refresh by default. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `refreshFirst`: boolean (optional) |
| `cameo_verify_relation_map` | Verify Relation Map graph and presentation evidence against caller-provided node/edge thresholds. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `minNodes`: integer (optional), `minEdges`: integer (optional) |
| `cameo_compare_relation_maps` | Diff two Relation Maps structurally across nodes, edges, and settings. [ajhcs/cameo-mcp-bridge Parity] | `leftDiagramId`: string (required), `rightDiagramId`: string (required) |
| `cameo_get_traceability_graph` | Build a structured traceability graph (nodes + directed edges) from a Relation Map or model scope. [ajhcs/cameo-mcp-bridge Parity] | `rootElementId`: string (required), `maxDepth`: integer (optional) |
| `cameo_create_snapshot` | Capture an in-memory model/diagram evidence snapshot for before/after comparison. [ajhcs/cameo-mcp-bridge Parity] | `label`: string (required), `scopeElementId`: string (optional) |
| `cameo_list_snapshots` | List captured in-memory evidence snapshots. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_get_snapshot` | Read a captured evidence snapshot by ID. [ajhcs/cameo-mcp-bridge Parity] | `snapshotId`: string (required) |
| `cameo_delete_snapshot` | Delete a captured evidence snapshot from memory. [ajhcs/cameo-mcp-bridge Parity] | `snapshotId`: string (required) |
| `cameo_diff_snapshots` | Diff two captured snapshots with bounded structural details. [ajhcs/cameo-mcp-bridge Parity] | `beforeSnapshotId`: string (required), `afterSnapshotId`: string (required) |
| `cameo_list_probe_templates` | List built-in read-only OpenAPI probe templates. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_execute_probe` | Execute a controlled OpenAPI probe or restricted Java-reflection readback. [ajhcs/cameo-mcp-bridge Parity] | `probeName`: string (required), `targetId`: string (optional) |
| `cameo_create_attached_file` | Create an `<<AttachedFile>>` comment element under an owner and embed a local file into the model via `FileAttachmentsHelper.createAttachedFileElement` and `storeFileToAttachedFile`. [Retroactive OpenAPI Discovery] | `ownerId`: string (required), `filePath`: string (required) |
| `cameo_extract_attached_file` | Extract an embedded file from an `<<AttachedFile>>` element to a local directory via `FileAttachmentsHelper.extractToDirectory`. [Retroactive OpenAPI Discovery] | `elementId`: string (required), `targetDirectory`: string (required) |
| `cameo_read_attached_file_metadata` | Inspect embedded file metadata (name, size, dateAdded, dateModified, author) or base64 content via `FileAttachmentsHelper.getStoredFileInputStream`. [Retroactive OpenAPI Discovery] | `elementId`: string (required), `includeBase64`: boolean (optional) |
| `cameo_remove_attached_file` | Remove an embedded file payload from an element via `FileAttachmentsHelper.removeStoredFile`. [Retroactive OpenAPI Discovery] | `elementId`: string (required) |

### 5. AdvancedTools (`advanced` family — Macros, OCL/Expressions, Validation, Profiles, Smart Packages, Hyperlinks, Diff/Merge & Teamwork Cloud) (100 Tools)
- **Catalog Class**: `com.cameomcp.catalog.AdvancedTools`
- **Cameo OpenAPI Packages**: `com.nomagic.magicdraw.automaton.MacroEngine, com.nomagic.magicdraw.expressions.evaluation.ExpressionContext, com.nomagic.magicdraw.validation.ValidationHelper, com.nomagic.magicdraw.hyperlinks.HyperlinkHandler, com.nomagic.magicdraw.merge.MergeUtil, com.nomagic.magicdraw.esi.EsiUtils, com.nomagic.magicdraw.teamwork2.locks, com.nomagic.magicdraw.autoid.NumberingInfoHelper`

| Tool Name | Description | Parameters (`inputSchema`) |
| :--- | :--- | :--- |
| `cameo_execute_macro` | Execute custom Jython, Groovy, or JavaScript scripts inside Cameo's JVM context with project, application, primaryModel, and ef pre-bound. | `script`: string (required), `language`: string (groovy|jython|js), `wrapInSession`: boolean (optional) |
| `cameo_eval_expression` | Evaluate structured OCL or Cameo binary expressions on a model element (alias: cameo_evaluate_expression). | `contextElementId`: string (required), `expression`: string (required), `language`: string (OCL2.0|Groovy|Binary) |
| `cameo_run_validation` | Run Cameo validation suites (e.g. SysML correctness, active validation suites) and return findings. | `suiteName`: string (required), `scopeId`: string (optional) |
| `cameo_get_validation_results` | Retrieve existing validation summary and detailed RuleViolationResult items (alias: cameo_get_validation_result). | `validationRunId`: string (optional) |
| `cameo_fix_validation_violation` | Execute an automated quick-fix NMAction on a validation rule violation. | `violationId`: string (required), `fixIndex`: integer (optional) |
| `cameo_list_profiles` | List all active/loaded UML profiles and stereotype counts in the project (alias: cameo_summarize_applied_profiles). | `{}` (none) |
| `cameo_get_profile_details` | Inspect stereotypes, tag definitions, and extended metaclasses within a profile (alias: cameo_list_profile_stereotypes). | `profileId`: string (required) |
| `cameo_create_custom_stereotype` | Define a new Stereotype element in a custom Profile and bind its base metaclasses. | `profileId`: string (required), `name`: string (required), `metaclasses`: array<string> (optional) |
| `cameo_create_tag_definition` | Add custom tag definitions (TypedElement properties) to a Stereotype. | `stereotypeId`: string (required), `tagName`: string (required), `typeId`: string (optional), `multiplicity`: string (optional) |
| `cameo_get_smart_packages` | List Smart Packages and their dynamic query/expression definitions in the project. | `packageId`: string (optional) |
| `cameo_create_smart_package` | Create a Smart Package with dynamic filtering or OCL/Find criteria logic. | `name`: string (required), `parentId`: string (required), `queryExpression`: string (optional) |
| `cameo_rebuild_smart_package` | Force re-evaluation of a Smart Package's cached dynamic contents. | `smartPackageId`: string (required) |
| `cameo_get_hyperlinks` | Extract outward web URLs, file links, or model element hyperlinks attached to an element (alias: cameo_list_hyperlinks). | `elementId`: string (required) |
| `cameo_add_hyperlink` | Attach a web URL, file link, or element/diagram reference hyperlink to a model element. | `elementId`: string (required), `targetElementOrUrl`: string (required), `text`: string (optional), `setActive`: boolean (optional) |
| `cameo_remove_hyperlink` | Delete a hyperlink from an element via HyperlinkHandler. | `elementId`: string (required), `targetElementOrUrl`: string (required) |
| `cameo_get_element_history` | Query change history and revision tracking records for an element. | `elementId`: string (required) |
| `cameo_compare_projects` | Compare two project revisions, local files, or branch versions for differences via MergeUtil. | `targetProjectPath`: string (required), `ancestorProjectPath`: string (optional) |
| `cameo_merge_projects` | Perform 2-way or 3-way model merge resolution between projects or branches via MergeUtil (alias: cameo_apply_project_merge). | `diffId`: string (required), `conflictResolution`: string (TARGET_PREFERRED|SOURCE_PREFERRED), `allowWrite`: boolean (optional) |
| `cameo_lock_element` | Lock an element (and optionally its subtree) on Teamwork Cloud for editing. | `elementId`: string (required), `recursive`: boolean (optional) |
| `cameo_unlock_element` | Release a Teamwork Cloud element lock. | `elementId`: string (required), `recursive`: boolean (optional) |
| `cameo_get_locks` | List active user locks across the Teamwork Cloud project (alias: cameo_get_teamwork_locks). | `{}` (none) |
| `cameo_commit_project` | Commit local changes to Teamwork Cloud server with a commit message. | `message`: string (required), `keepLocks`: boolean (optional) |
| `cameo_update_project` | Pull latest updates from Teamwork Cloud server into the active session. | `{}` (none) |
| `cameo_list_project_versions` | Query version history log on Teamwork Cloud (alias: cameo_get_teamwork_history). | `limit`: integer (optional) |
| `cameo_manage_used_projects` | Mount, unmount, or list referenced used project modules (.mdzip). | `action`: string (list|mount|unmount), `modulePath`: string (optional) |
| `cameo_create_derived_property` | Define custom derived properties on a customization or stereotype using expressions. | `customizationId`: string (required), `propertyName`: string (required), `expression`: string (required) |
| `cameo_get_derived_property_value` | Calculate and return the current derived property value for an element. | `elementId`: string (required), `propertyName`: string (required) |
| `cameo_create_metamodel_extension` | Add custom meta-extensions and customization rules (`<<Customization>>`). | `packageId`: string (required), `name`: string (required), `customizationTargetId`: string (required) |
| `cameo_get_dsl_customizations` | Inspect active DSL customization rules and Customization targets in Cameo. | `packageId`: string (optional) |
| `cameo_search_by_ocl` | Perform low-level OCL 2.0 queries across a model scope and return matching elements. | `oclQuery`: string (required), `scopeId`: string (optional) |
| `cameo_export_json_schema` | Export model package or block hierarchy metadata as JSON Schema specifications. | `scopeId`: string (required) |
| `cameo_import_json_data` | Populate model element structures or InstanceSpecifications from structured JSON payloads. | `parentId`: string (required), `jsonData`: string (required) |
| `cameo_profile_performance` | Benchmark execution speed and memory footprint of Cameo OpenAPI operations. | `operation`: string (optional) |
| `cameo_get_system_logs` | Read recent stack traces or log entries from Cameo's md.log and audit ring buffer. | `lines`: integer (optional) |
| `cameo_get_validation_capabilities` | Inspect native model validation capabilities and loaded validation suites. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_list_validation_suites` | List all ValidationSuite packages and rules available in the project and loaded profiles. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_run_native_validation` | Run a native Cameo validation suite against a scope and cache RuleViolationResult records. [ajhcs/cameo-mcp-bridge Parity] | `suiteId`: string (required), `scopeId`: string (optional), `severityThreshold`: string (optional) |
| `cameo_get_validation_result` | Read cached results from a previous native validation execution. [ajhcs/cameo-mcp-bridge Parity] | `validationRunId`: string (required) |
| `cameo_get_report_capabilities` | Inspect Report Wizard capabilities, engines, and output format support. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_generate_report_preview` | Validate Report Wizard template parameters and output path in dry-run preview mode. [ajhcs/cameo-mcp-bridge Parity] | `templateName`: string (required), `outputPath`: string (required), `scopeId`: string (optional) |
| `cameo_get_report_job` | Check status and output file receipt of a Report Wizard generation job. [ajhcs/cameo-mcp-bridge Parity] | `jobId`: string (required) |
| `cameo_get_import_export_capabilities` | Inspect JSON/CSV/ReqIF requirements import and export capabilities. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_export_requirements` | Export requirements hierarchy and traceability to structured JSON or CSV. [ajhcs/cameo-mcp-bridge Parity] | `scopeId`: string (required), `format`: string (json|csv), `outputPath`: string (optional) |
| `cameo_preview_requirements_import` | Dry-run preview of JSON/CSV requirements import showing adds, updates, and conflicts. [ajhcs/cameo-mcp-bridge Parity] | `inputData`: string (required), `format`: string (json|csv), `targetPackageId`: string (required) |
| `cameo_apply_requirements_import` | Apply validated JSON/CSV requirements import inside a SessionManager transaction. [ajhcs/cameo-mcp-bridge Parity] | `inputData`: string (required), `format`: string (json|csv), `targetPackageId`: string (required), `allowWrite`: boolean (required) |
| `cameo_get_requirements_capabilities` | Get SysML requirements profile and ReqIF plugin capability summary. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_export_requirements_preview` | Preview requirements export scope and row counts before writing to disk. [ajhcs/cameo-mcp-bridge Parity] | `scopeId`: string (required), `format`: string (json|csv|reqif) |
| `cameo_import_requirements_preview` | Preview ReqIF or tabular requirements import mapping before execution. [ajhcs/cameo-mcp-bridge Parity] | `filePath`: string (required), `targetPackageId`: string (required) |
| `cameo_get_simulation_capabilities` | Check Cameo Simulation Toolkit (CST) availability, engine types, and active sessions. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_list_simulation_configurations` | List SimulationConfiguration elements and execution targets in the project. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_run_simulation_preview` | Preview simulation execution settings, target classifier, and initial values. [ajhcs/cameo-mcp-bridge Parity] | `configId`: string (required) |
| `cameo_get_simulation_result` | Read runtime values, state history, and console output from a SimulationExecution. [ajhcs/cameo-mcp-bridge Parity] | `executionId`: string (required) |
| `cameo_terminate_simulation` | Terminate an active Cameo Simulation Toolkit session cleanly. [ajhcs/cameo-mcp-bridge Parity] | `executionId`: string (required) |
| `cameo_get_teamwork_capabilities` | Check Teamwork Cloud (TWC / ESI) connection status, user session, and server reachability. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_get_teamwork_project` | Get Teamwork Cloud repository ID, branch, revision, and remote descriptor metadata. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_preview_teamwork_commit` | Preview modified local elements and lock checks prior to Teamwork Cloud commit. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_preview_teamwork_update` | Preview incoming remote changes from Teamwork Cloud prior to update. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_list_teamwork_descriptors` | List remote projects available on the connected Teamwork Cloud server. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_list_teamwork_branches` | List branches for the active Teamwork Cloud project. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_get_teamwork_history` | Get commit revision history for the active Teamwork Cloud project. [ajhcs/cameo-mcp-bridge Parity] | `limit`: integer (optional) |
| `cameo_get_teamwork_locks` | List currently locked elements and lock owners in the active Teamwork Cloud project. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_get_datahub_capabilities` | Probe Cameo DataHub plugin presence and loaded driver types without emitting credentials. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_list_datahub_sources` | List configured DataHub data sources and synchronization statuses (read-only). [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_preview_datahub_sync` | Preview pending DataHub sync items in read-only mode. [ajhcs/cameo-mcp-bridge Parity] | `sourceId`: string (required) |
| `cameo_get_criteria_capabilities` | Inspect structured criteria expression builder capabilities and metaclass navigation rules. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_list_criteria_operators` | List supported criteria operators, filter predicates, and stereotype matchers. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_build_criteria_expression` | Build a structured Finder/RelationMap criteria expression from element types and stereotypes. [ajhcs/cameo-mcp-bridge Parity] | `elementTypes`: array<string>, `stereotypes`: array<string>, `navigation`: string (optional) |
| `cameo_validate_criteria_expression` | Validate a criteria expression syntax and metaclass resolution against the active project. [ajhcs/cameo-mcp-bridge Parity] | `expression`: string (required) |
| `cameo_preview_criteria_matches` | Preview matching elements for a criteria expression with bounded count. [ajhcs/cameo-mcp-bridge Parity] | `expression`: string (required), `scopeId`: string (optional) |
| `cameo_get_profile_capabilities` | Inspect profile management capabilities and standard/custom profile repositories. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_summarize_applied_profiles` | Summarize all applied profiles, versions, and stereotype counts in the project. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_list_profile_stereotypes` | List all stereotypes, tagged value definitions, and metaclasses inside a specific profile. [ajhcs/cameo-mcp-bridge Parity] | `profileId`: string (required) |
| `cameo_preview_profile_application` | Dry-run preview of applying or updating a profile on a target package. [ajhcs/cameo-mcp-bridge Parity] | `profileId`: string (required), `targetPackageId`: string (required) |
| `cameo_validate_profile_consistency` | Check stereotyped elements against profile tag multiplicity and type constraints. [ajhcs/cameo-mcp-bridge Parity] | `profileId`: string (required) |
| `cameo_get_variants_capabilities` | Check Model-Based Product Line Engineering (MBPLE) plugin and profile availability. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_list_variation_points` | List Product Line Engineering (MBPLE) variation points and feature trees. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_list_variant_configurations` | List VariantConfiguration elements and active feature selections. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_evaluate_variant_expression` | Evaluate an MBPLE variation point boolean/feature expression against a VariantConfiguration. [ajhcs/cameo-mcp-bridge Parity] | `variantConfigId`: string (required), `expression`: string (required) |
| `cameo_preview_variant_realization` | Preview variant realization transformation for a VariantConfiguration. [ajhcs/cameo-mcp-bridge Parity] | `variantConfigId`: string (required) |
| `cameo_get_extension_capabilities` | Probe installed domain extension plugins (UAF, DoDAF, SysML v2, Safety, Cyber, Spice). [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_scan_safety_cyber_extensions` | Scan installed safety/reliability (ISO 26262, ARP4761, RAAML, FMEA) and cyber plugins/profiles. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_list_extension_stereotypes` | List domain-specific stereotypes exposed by optional safety, cyber, or UAF extensions. [ajhcs/cameo-mcp-bridge Parity] | `extensionFamily`: string (required) |
| `cameo_preview_extension_artifact` | Preview creation of a domain-extension artifact (FMEA table, Fault Tree, Cyber Risk matrix) with refusal/capability receipts. [ajhcs/cameo-mcp-bridge Parity] | `artifactKind`: string (required), `parentId`: string (required) |
| `cameo_get_typed_diagram_capabilities` | Inspect custom and profile-extended DiagramDescriptor capabilities. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_inspect_typed_diagram_descriptor` | Inspect DiagramDescriptor metadata and supported symbols/actions for a custom diagram type. [ajhcs/cameo-mcp-bridge Parity] | `diagramType`: string (required) |
| `cameo_list_typed_diagram_palettes` | List toolbar action categories and creatable element types for a specialized diagram. [ajhcs/cameo-mcp-bridge Parity] | `diagramType`: string (required) |
| `cameo_preview_typed_diagram_creation` | Validate parent owner rules and stereotype prerequisites before creating a specialized diagram. [ajhcs/cameo-mcp-bridge Parity] | `diagramType`: string (required), `parentId`: string (required) |
| `cameo_verify_typed_diagram_conformance` | Verify that symbols on a specialized diagram conform to its DiagramDescriptor metamodel rules. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required) |
| `cameo_list_merge_changes` | List individual `Change` records, conflicts, equivalent changes, and required dependencies from a computed `ProjectDifference`. [Retroactive OpenAPI Discovery] | `diffId`: string (required), `conflictsOnly`: boolean (optional) |
| `cameo_preview_project_merge` | Preview 2-way or 3-way merge conflict resolution using `MergeUtil.ConflictResolution` (TARGET_PREFERRED / SOURCE_PREFERRED). [Retroactive OpenAPI Discovery] | `diffId`: string (required), `conflictResolution`: string (TARGET_PREFERRED|SOURCE_PREFERRED) |
| `cameo_apply_project_merge` | Execute project merge changes via `MergeUtil` with explicit `allowWrite=true` gate. [Retroactive OpenAPI Discovery] | `diffId`: string (required), `conflictResolution`: string (TARGET_PREFERRED|SOURCE_PREFERRED), `allowWrite`: boolean (required) |
| `cameo_list_hyperlinks` | List all active and normal hyperlinks (model element links, diagram links, web URLs, file links) attached to an element via `HyperlinkHandler`. [Retroactive OpenAPI Discovery] | `elementId`: string (required) |
| `cameo_find_elements_by_hyperlink` | Search the model for all elements referencing a specific hyperlink target via `Finder.byHyperlink()`. [Retroactive OpenAPI Discovery] | `hyperlinkTarget`: string (required), `scopeId`: string (optional) |
| `cameo_get_numbering_schemes` | Inspect active AutoNumbering schemes, prefixes, and separators configured for Requirements, Activities, or Use Cases via `NumberingInfoHelper`. [Retroactive OpenAPI Discovery] | `scopeId`: string (optional) |
| `cameo_configure_autonumbering` | Configure prefix, separator, and hierarchical numbering scheme on a package or element type. [Retroactive OpenAPI Discovery] | `packageId`: string (required), `targetType`: string (required), `prefix`: string (required), `separator`: string (optional) |
| `cameo_renumber_elements_recursively` | Trigger automatic hierarchical renumbering of Requirements or Actions in a package scope. [Retroactive OpenAPI Discovery] | `packageId`: string (required), `recursive`: boolean (optional) |
| `cameo_evaluate_expression` | Evaluate an OCL 2.0, Binary, or Structured Expression against a context element using `ExpressionContext`. [Retroactive OpenAPI Discovery] | `contextElementId`: string (required), `expression`: string (required), `language`: string (OCL2.0|Groovy|Structured) |
| `cameo_calculate_package_metrics` | Calculate recursive package element/relationship/stereotype coverage metrics via `com.nomagic.magicdraw.modelmetrics.ScriptHelper`. [Retroactive OpenAPI Discovery] | `packageId`: string (required), `includeSubpackages`: boolean (optional) |
| `cameo_list_model_transformations` | List registered Model Transformation types (e.g., UML-to-XML, SysML-to-Model, Any-to-Any) in `com.nomagic.magicdraw.modeltransformations`. [Retroactive OpenAPI Discovery] | `{}` (none) |
| `cameo_execute_model_transformation` | Execute a registered Model Transformation with source/target package mapping inside a SessionManager transaction. [Retroactive OpenAPI Discovery] | `transformationType`: string (required), `sourcePackageIds`: array<string> (required), `targetPackageId`: string (required), `allowWrite`: boolean (required) |

### 6. AnalysisTools (`analysis` family — Simulation Toolkit, Parametrics, Traceability, Impact/Coverage, Metrics, Trade Studies, Roll-Ups, OOSEM & Semantic Verification) (52 Tools)
- **Catalog Class**: `com.cameomcp.catalog.AnalysisTools`
- **Cameo OpenAPI Packages**: `com.nomagic.magicdraw.simulation.SimulationHelper, com.nomagic.magicdraw.simulation.execution.SimulationExecution, com.nomagic.magicdraw.modelmetrics.ScriptHelper`

| Tool Name | Description | Parameters (`inputSchema`) |
| :--- | :--- | :--- |
| `cameo_run_simulation` | Trigger Cameo Simulation Toolkit execution on a Block, Activity, StateMachine, Parametric diagram, or SimulationConfiguration. | `configOrTargetId`: string (required), `headless`: boolean (optional), `allowExecution`: boolean (optional) |
| `cameo_stop_simulation` | Stop or abort a running simulation session (alias: cameo_terminate_simulation). | `executionId`: string (optional) |
| `cameo_pause_simulation` | Pause an active simulation run. | `executionId`: string (optional) |
| `cameo_resume_simulation` | Resume a paused simulation run. | `executionId`: string (optional) |
| `cameo_step_simulation` | Execute a single step in a simulation run. | `executionId`: string (optional) |
| `cameo_get_simulation_status` | Check status, state vector, and active executions of the simulation engine. | `executionId`: string (optional) |
| `cameo_get_simulation_variables` | Inspect runtime values of variables, signals, and ports in an active simulation. | `executionId`: string (optional) |
| `cameo_set_simulation_variable` | Inject or override a runtime variable or signal value during simulation execution. | `executionId`: string (optional), `variablePath`: string (required), `value`: string (required) |
| `cameo_export_simulation_log` | Save time-series simulation trace log to CSV or JSON file. | `executionId`: string (optional), `outputPath`: string (required) |
| `cameo_run_parametric_analysis` | Execute mathematical constraint solver on SysML Parametric models. | `contextBlockId`: string (required) |
| `cameo_get_parametric_results` | Retrieve evaluated ConstraintParameter values from a Parametric evaluation. | `contextBlockId`: string (required) |
| `cameo_trace_dependency_tree` | Build full dependency tree upward or downward from a target element. | `elementId`: string (required), `direction`: string (upward|downward|both), `maxDepth`: integer (optional) |
| `cameo_trace_impact_analysis` | Calculate downstream/upstream impact analysis graph when an element changes. | `elementId`: string (required), `maxDepth`: integer (optional) |
| `cameo_trace_coverage_analysis` | Analyze requirement satisfaction, verification, and allocation coverage percentages across a package. | `packageId`: string (required) |
| `cameo_get_unlinked_requirements` | List requirements lacking satisfy, verify, or derive links. | `packageId`: string (required) |
| `cameo_get_orphan_elements` | List model elements lacking diagram appearances or structural relationships. | `packageId`: string (required) |
| `cameo_calculate_metrics` | Calculate structural metrics (depth, coupling, count, stereotype coverage) across model packages via ScriptHelper (alias: cameo_calculate_package_metrics). | `packageId`: string (required), `includeSubpackages`: boolean (optional) |
| `cameo_list_metric_suites` | List defined MetricSuites in the project. | `{}` (none) |
| `cameo_run_metric_suite` | Execute a specific MetricSuite calculation run. | `metricSuiteId`: string (required), `scopeId`: string (optional) |
| `cameo_export_metrics` | Export calculated metric results to CSV or JSON. | `outputPath`: string (required) |
| `cameo_find_circular_dependencies` | Identify cyclic dependency loops among packages, blocks, or activities. | `scopeId`: string (optional) |
| `cameo_find_dead_end_flows` | Detect activity control/object flows or IBD item flows that end without targets. | `scopeId`: string (required) |
| `cameo_verify_interface_compatibility` | Compare ports and InterfaceBlocks for flow property direction, type, or signal mismatch (alias: cameo_verify_port_boundary_consistency). | `scopeId`: string (required) |
| `cameo_run_trade_study` | Evaluate weighted criteria, MoEs, and design alternatives in a SysML Trade Study model. | `tradeStudyContextId`: string (required) |
| `cameo_get_trade_study_results` | Retrieve trade study objective scores and alternative ranking tables. | `tradeStudyContextId`: string (required) |
| `cameo_create_analysis_context` | Set up an analysis context container block for trade studies or simulation runs. | `name`: string (required), `parentId`: string (required) |
| `cameo_run_roll_up_analysis` | Compute mass, power, cost, or reliability roll-ups along block composition hierarchies. | `rootBlockId`: string (required), `valuePropertyName`: string (required) |
| `cameo_get_roll_up_results` | Retrieve itemized subsystem and total roll-up calculation results. | `rootBlockId`: string (required), `valuePropertyName`: string (required) |
| `cameo_check_state_reachability` | Run state reachability and deadlock analysis on StateMachine models. | `stateMachineId`: string (required) |
| `cameo_find_unreachable_states` | List dead or unreachable states in StateMachine diagrams. | `stateMachineId`: string (required) |
| `cameo_verify_allocation` | Analyze allocation coverage between logical/functional behaviors and physical architecture blocks. | `scopeId`: string (required) |
| `cameo_get_allocation_matrix` | Generate inline summary table of behavioral-to-structural allocation links. | `scopeId`: string (required) |
| `cameo_list_methodology_packs` | List built-in methodology packs such as oosem, sysmod, and uaf. [ajhcs/cameo-mcp-bridge Parity] | `{}` (none) |
| `cameo_get_methodology_pack` | Get one pack's phases, bounded artifact recipes, naming rules, and evidence structure. [ajhcs/cameo-mcp-bridge Parity] | `packId`: string (required) |
| `cameo_get_methodology_guidance` | Inspect current model packages and explain which methodology artifact is missing next and why. [ajhcs/cameo-mcp-bridge Parity] | `packId`: string (required), `rootPackageId`: string (required) |
| `cameo_execute_methodology_recipe` | Execute a bounded methodology recipe inside a SessionManager transaction and return receipts, conformance, and review packet output. [ajhcs/cameo-mcp-bridge Parity] | `packId`: string (required), `recipeId`: string (required), `targetPackageId`: string (required), `parameters`: object (optional) |
| `cameo_validate_methodology_recipe` | Validate normalized artifact snapshots against recipe expectations without mutating the model. [ajhcs/cameo-mcp-bridge Parity] | `packId`: string (required), `recipeId`: string (required), `targetPackageId`: string (required) |
| `cameo_generate_review_packet` | Generate a compact evidence bundle and Markdown review packet without mutating the model. [ajhcs/cameo-mcp-bridge Parity] | `packId`: string (required), `scopePackageId`: string (required) |
| `cameo_verify_matrix_consistency` | Check matrix row/column membership, populated-cell count, dependency names, and density. [ajhcs/cameo-mcp-bridge Parity] | `matrixId`: string (required), `minPopulatedCells`: integer (optional) |
| `cameo_verify_diagram_visual` | Check rendered diagram bounds, shape/path presence, canvas coverage, and symbol bounding-box overlap risk. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required), `maxOverlapCount`: integer (optional) |
| `cameo_verify_activity_flow_semantics` | Check connected behavior flow, initial/final node reachability, and swimlane partition sanity on an Activity diagram. [ajhcs/cameo-mcp-bridge Parity] | `diagramId`: string (required) |
| `cameo_verify_port_boundary_consistency` | Check InterfaceBlock flow-property ownership, conjugate port compatibility, duplicate vocabulary, and direction conflicts. [ajhcs/cameo-mcp-bridge Parity] | `packageId`: string (required) |
| `cameo_verify_requirement_quality` | Check SysML Requirement IDs, non-blank text, atomic shall statements, and verification traceability readiness. [ajhcs/cameo-mcp-bridge Parity] | `packageId`: string (required) |
| `cameo_verify_cross_diagram_traceability` | Compare Activity, Interface, IBD, and Requirements-to-Architecture vocabulary and satisfy/allocate trace coverage. [ajhcs/cameo-mcp-bridge Parity] | `rootPackageId`: string (required) |
| `cameo_detect_cross_diagram_inconsistencies` | Run semantic cross-diagram checks and return previewable remediation receipts plus a structured patchPlan. [ajhcs/cameo-mcp-bridge Parity] | `rootPackageId`: string (required) |
| `cameo_build_cross_diagram_remediation_plan` | Turn existing validation payloads into a non-mutating, step-by-step remediation patch plan. [ajhcs/cameo-mcp-bridge Parity] | `validationPayload`: object (required) |
| `cameo_proof_model_text` | Collect and proof requirements text, comments, state/transition names, and diagram labels with optional safe auto-apply. [ajhcs/cameo-mcp-bridge Parity] | `scopeId`: string (required), `autoApply`: boolean (optional) |
| `cameo_apply_proofing_patch_plan` | Apply a previously generated proofing patch plan to the live model inside a single undoable session. [ajhcs/cameo-mcp-bridge Parity] | `patchPlan`: object (required) |
| `cameo_compare_expected_artifact_list` | Diff current model artifacts against an expected methodology artifact list and return preview actions. [ajhcs/cameo-mcp-bridge Parity] | `packId`: string (required), `scopePackageId`: string (required) |
| `cameo_validate_methodology_package` | Validate a pack/recipe/package scope against methodology structural and naming definitions. [ajhcs/cameo-mcp-bridge Parity] | `packId`: string (required), `scopePackageId`: string (required) |
| `cameo_export_required_diagrams` | Plan or execute export of all methodology-required diagrams to a target directory. [ajhcs/cameo-mcp-bridge Parity] | `packId`: string (required), `scopePackageId`: string (required), `outputDir`: string (required) |
| `cameo_assemble_ppt_pdf` | Plan or assemble review packages and HTML/DocBook/PPTX report bundles from the exported diagram set. [ajhcs/cameo-mcp-bridge Parity] | `packId`: string (required), `scopePackageId`: string (required), `outputPath`: string (required) |

### 7. Sysml2Tools (`sysml2` family — Cameo 2026x SysML v2 / KerML Textual Notation, Usages, Definitions, Data Markings & v1↔v2 Conversion) (28 Tools)
- **Catalog Class**: `com.cameomcp.catalog.Sysml2Tools`
- **Cameo OpenAPI Packages**: `com.nomagic.magicdraw.uml.symbols.layout.sysml2, com.nomagic.magicdraw.datamarkingsandclassification.banner, com.dassault_systemes.modeler.foundation.model`

| Tool Name | Description | Parameters (`inputSchema`) |
| :--- | :--- | :--- |
| `cameo_sysml2_import` | Import KerML / SysML v2 textual notation code into the active Cameo 2026x model or convert to SysML v1.x elements. | `textualCode`: string (required), `targetNamespaceId`: string (optional) |
| `cameo_sysml2_export` | Export Cameo SysML model elements out to standardized SysML v2 textual notation. | `elementId`: string (required), `recursive`: boolean (optional) |
| `cameo_sysml2_parse_text` | Validate and parse SysML v2 textual notation snippets and return AST/diagnostics. | `textualCode`: string (required) |
| `cameo_sysml2_get_element` | Read a model element in SysML v2 metamodel representation (Definition / Usage / Features). | `elementId`: string (required) |
| `cameo_sysml2_create_part` | Create a SysML v2 PartUsage or PartDefinition under a namespace. | `name`: string (required), `parentId`: string (required), `isDefinition`: boolean (optional), `typeId`: string (optional) |
| `cameo_sysml2_create_port` | Create a SysML v2 PortUsage or PortDefinition. | `name`: string (required), `parentId`: string (required), `isDefinition`: boolean (optional), `conjugated`: boolean (optional) |
| `cameo_sysml2_create_item` | Create a SysML v2 ItemUsage or ItemDefinition. | `name`: string (required), `parentId`: string (required), `isDefinition`: boolean (optional) |
| `cameo_sysml2_create_action` | Create a SysML v2 ActionUsage or ActionDefinition. | `name`: string (required), `parentId`: string (required), `isDefinition`: boolean (optional) |
| `cameo_sysml2_create_state` | Create a SysML v2 StateUsage or StateDefinition. | `name`: string (required), `parentId`: string (required), `isDefinition`: boolean (optional) |
| `cameo_sysml2_create_requirement` | Create a SysML v2 RequirementUsage or RequirementDefinition with reqId and text. | `name`: string (required), `parentId`: string (required), `reqId`: string (optional), `text`: string (optional), `isDefinition`: boolean (optional) |
| `cameo_sysml2_create_constraint` | Create a SysML v2 ConstraintUsage or ConstraintDefinition. | `name`: string (required), `parentId`: string (required), `expression`: string (optional), `isDefinition`: boolean (optional) |
| `cameo_sysml2_create_calculation` | Create a SysML v2 CalculationUsage or CalculationDefinition. | `name`: string (required), `parentId`: string (required), `expression`: string (optional), `isDefinition`: boolean (optional) |
| `cameo_sysml2_create_case` | Create a SysML v2 AnalysisCaseUsage or VerificationCaseUsage. | `name`: string (required), `parentId`: string (required), `caseKind`: string (analysis|verification|useCase) |
| `cameo_sysml2_create_connection` | Connect two SysML v2 usages via a ConnectionUsage / ConnectionDefinition. | `name`: string (optional), `sourceUsageId`: string (required), `targetUsageId`: string (required), `parentId`: string (required) |
| `cameo_sysml2_create_interface` | Define a SysML v2 InterfaceUsage or InterfaceDefinition connecting two ports. | `name`: string (required), `parentId`: string (required), `sourcePortId`: string (optional), `targetPortId`: string (optional) |
| `cameo_sysml2_create_allocation` | Define a SysML v2 AllocationUsage between logical and physical usages. | `sourceUsageId`: string (required), `targetUsageId`: string (required), `parentId`: string (required) |
| `cameo_sysml2_bind_ports` | Create a SysML v2 BindingConnectorAsUsage between two ports or features. | `sourceFeatureId`: string (required), `targetFeatureId`: string (required), `parentId`: string (required) |
| `cameo_sysml2_list_members` | List owned members, usages, and definitions inside a SysML v2 package or namespace. | `namespaceId`: string (required) |
| `cameo_sysml2_get_relationships` | Read SysML v2 semantic relationships (specialization, subsetting, redefinition, succession, flow) for a usage. | `elementId`: string (required) |
| `cameo_sysml2_validate` | Run SysML v2 semantic and KerML conformance rule checkers. | `scopeId`: string (optional) |
| `cameo_sysml2_convert_v1_to_v2` | Convert a SysML v1.x model branch to SysML v2 definitions and usages. | `sourcePackageId`: string (required), `targetNamespaceId`: string (optional) |
| `cameo_sysml2_convert_v2_to_v1` | Convert SysML v2 structures back to Cameo SysML v1.x blocks, ports, and requirements. | `sourceNamespaceId`: string (required), `targetPackageId`: string (optional) |
| `cameo_get_data_markings` | Read security classification and data markings (CUI, Classification Banner, Portion Markings) on elements or diagrams (supports 2024x & 2026x `datamarkingsandclassification`). [Retroactive OpenAPI Discovery] | `elementId`: string (required) |
| `cameo_set_data_markings` | Apply or update security classification / data marking stereotypes and banner properties on an element or diagram. [Retroactive OpenAPI Discovery] | `elementId`: string (required), `marking`: string (required), `bannerText`: string (optional) |
| `cameo_poll_model_change_events` | Poll the plugin's non-blocking SmartListener ring-buffer for recent model element creations, modifications, and deletions (resolves the lack of change notifications in request/response MCP). [Retroactive OpenAPI Discovery] | `sinceSequenceId`: integer (optional), `limit`: integer (optional) |
| `cameo_sysmlv2_bridge_probe` | Detect 2026x SysML v2 plugin/metamodel availability, SysML v2 layout engine support (`com.nomagic.magicdraw.uml.symbols.layout.sysml2`), and v1-to-v2 conversion readiness. [Retroactive OpenAPI Discovery] | `{}` (none) |
| `cameo_get_environment_options` | Read Cameo global EnvironmentOptions (`Application.getInstance().getEnvironmentOptions()`) and PathVariables (`com.nomagic.magicdraw.pathvariables`). [Retroactive OpenAPI Discovery] | `groupId`: string (optional) |
| `cameo_resolve_path_variable` | Resolve or inspect a Cameo path variable (e.g., `<install.root>`, `<profiles.dir>`) via `PathVariablesResolver`. [Retroactive OpenAPI Discovery] | `variableName`: string (required) |

