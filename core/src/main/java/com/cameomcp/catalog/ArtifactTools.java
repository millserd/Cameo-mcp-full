package com.cameomcp.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 4. ArtifactTools (`artifacts` family — Generic Tables, Dependency Matrices, Relation Maps, Reports, XMI, Attachments & Snapshots)
 * OpenAPI Packages: com.nomagic.generictable.GenericTableManager, com.nomagic.magicdraw.dependencymatrix.MatrixManager, com.nomagic.magicdraw.visualization.relationshipmap, com.nomagic.magicdraw.magicreport.helper.TemplateHelper, com.nomagic.magicdraw.emfuml2xmi, com.nomagic.magicdraw.fileattachments.FileAttachmentsHelper
 */
public final class ArtifactTools {

    public static final String FAMILY = "artifacts";
    public static final int TOOL_COUNT = 48;

    public static final List<String> TOOL_NAMES = Collections.unmodifiableList(Arrays.asList(
        "cameo_list_tables",
        "cameo_get_table_contents",
        "cameo_create_table",
        "cameo_add_table_row",
        "cameo_remove_table_row",
        "cameo_set_table_columns",
        "cameo_export_table",
        "cameo_list_matrices",
        "cameo_get_matrix_contents",
        "cameo_create_matrix",
        "cameo_export_matrix",
        "cameo_list_relation_maps",
        "cameo_get_relation_map_contents",
        "cameo_create_relation_map",
        "cameo_generate_report",
        "cameo_list_report_templates",
        "cameo_export_model_xml",
        "cameo_import_model_xml",
        "cameo_list_matrix_kinds",
        "cameo_get_matrix",
        "cameo_list_generic_tables",
        "cameo_get_generic_table",
        "cameo_list_generic_table_columns",
        "cameo_create_generic_table",
        "cameo_get_relation_map",
        "cameo_configure_relation_map",
        "cameo_refresh_relation_map",
        "cameo_dump_relation_map_raw_settings",
        "cameo_list_relation_map_presentations",
        "cameo_list_relation_map_criteria_templates",
        "cameo_set_relation_map_criteria",
        "cameo_expand_relation_map",
        "cameo_collapse_relation_map",
        "cameo_render_relation_map",
        "cameo_verify_relation_map",
        "cameo_compare_relation_maps",
        "cameo_get_traceability_graph",
        "cameo_create_snapshot",
        "cameo_list_snapshots",
        "cameo_get_snapshot",
        "cameo_delete_snapshot",
        "cameo_diff_snapshots",
        "cameo_list_probe_templates",
        "cameo_execute_probe",
        "cameo_create_attached_file",
        "cameo_extract_attached_file",
        "cameo_read_attached_file_metadata",
        "cameo_remove_attached_file"
    ));

    private ArtifactTools() {}
}
