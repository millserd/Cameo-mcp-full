package com.cameomcp.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 3. DiagramTools (`diagrams` family — Diagram Lifecycle, Symbols, Paths, Layout, Compartments & Export)
 * OpenAPI Packages: com.nomagic.magicdraw.openapi.uml.PresentationElementsManager, com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement, com.nomagic.magicdraw.uml.symbols.CompartmentManager, com.nomagic.magicdraw.export.image.ImageExporter
 */
public final class DiagramTools {

    public static final String FAMILY = "diagrams";
    public static final int TOOL_COUNT = 66;

    public static final List<String> TOOL_NAMES = Collections.unmodifiableList(Arrays.asList(
        "cameo_list_diagrams",
        "cameo_open_diagram",
        "cameo_close_diagram",
        "cameo_get_diagram_contents",
        "cameo_create_diagram",
        "cameo_delete_diagram",
        "cameo_export_diagram",
        "cameo_get_diagram_image_base64",
        "cameo_add_symbol_to_diagram",
        "cameo_add_symbols_to_diagram",
        "cameo_remove_symbol_from_diagram",
        "cameo_layout_diagram",
        "cameo_set_symbol_bounds",
        "cameo_set_symbol_style",
        "cameo_add_path_to_diagram",
        "cameo_remove_path_from_diagram",
        "cameo_reroute_path",
        "cameo_get_symbol_properties",
        "cameo_set_diagram_grid",
        "cameo_zoom_diagram",
        "cameo_center_symbol",
        "cameo_select_diagram_symbols",
        "cameo_clear_diagram_selection",
        "cameo_create_shape_on_diagram",
        "cameo_create_note_anchor",
        "cameo_get_diagram_legend",
        "cameo_apply_diagram_legend",
        "cameo_group_symbols",
        "cameo_ungroup_symbols",
        "cameo_order_symbols",
        "cameo_align_symbols",
        "cameo_resize_symbols_to_fit",
        "cameo_toggle_compartment_visibility",
        "cameo_nest_symbol",
        "cameo_get_diagram_frame",
        "cameo_list_diagram_types",
        "cameo_add_to_diagram",
        "cameo_get_diagram_image",
        "cameo_auto_layout",
        "cameo_list_diagram_shapes",
        "cameo_get_shape_properties",
        "cameo_move_shapes",
        "cameo_delete_shapes",
        "cameo_add_diagram_paths",
        "cameo_set_shape_properties",
        "cameo_set_shape_compartments",
        "cameo_set_transition_label_presentation",
        "cameo_set_item_flow_label_presentation",
        "cameo_set_allocation_compartment_presentation",
        "cameo_repair_hidden_labels",
        "cameo_repair_label_positions",
        "cameo_repair_conveyed_item_labels",
        "cameo_normalize_compartment_presets",
        "cameo_prune_diagram_presentations",
        "cameo_prune_path_decorations",
        "cameo_reparent_shapes",
        "cameo_route_paths",
        "cameo_rename_diagram",
        "cameo_open_close_diagram_tab",
        "cameo_export_diagram_to_file",
        "cameo_create_sequence_message",
        "cameo_create_lost_or_found_message",
        "cameo_create_path_to_self",
        "cameo_create_swimlane",
        "cameo_create_deep_part_shape",
        "cameo_create_note_or_textbox"
    ));

    private DiagramTools() {}
}
