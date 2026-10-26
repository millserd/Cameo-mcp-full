package com.cameomcp.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 7. Sysml2Tools (`sysml2` family — Cameo 2026x SysML v2 / KerML Textual Notation, Usages, Definitions, Data Markings & v1↔v2 Conversion)
 * OpenAPI Packages: com.nomagic.magicdraw.uml.symbols.layout.sysml2, com.nomagic.magicdraw.datamarkingsandclassification.banner, com.dassault_systemes.modeler.foundation.model
 */
public final class Sysml2Tools {

    public static final String FAMILY = "sysml2";
    public static final int TOOL_COUNT = 28;

    public static final List<String> TOOL_NAMES = Collections.unmodifiableList(Arrays.asList(
        "cameo_sysml2_import",
        "cameo_sysml2_export",
        "cameo_sysml2_parse_text",
        "cameo_sysml2_get_element",
        "cameo_sysml2_create_part",
        "cameo_sysml2_create_port",
        "cameo_sysml2_create_item",
        "cameo_sysml2_create_action",
        "cameo_sysml2_create_state",
        "cameo_sysml2_create_requirement",
        "cameo_sysml2_create_constraint",
        "cameo_sysml2_create_calculation",
        "cameo_sysml2_create_case",
        "cameo_sysml2_create_connection",
        "cameo_sysml2_create_interface",
        "cameo_sysml2_create_allocation",
        "cameo_sysml2_bind_ports",
        "cameo_sysml2_list_members",
        "cameo_sysml2_get_relationships",
        "cameo_sysml2_validate",
        "cameo_sysml2_convert_v1_to_v2",
        "cameo_sysml2_convert_v2_to_v1",
        "cameo_get_data_markings",
        "cameo_set_data_markings",
        "cameo_poll_model_change_events",
        "cameo_sysmlv2_bridge_probe",
        "cameo_get_environment_options",
        "cameo_resolve_path_variable"
    ));

    private Sysml2Tools() {}
}
