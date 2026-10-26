package com.cameomcp.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 6. AnalysisTools (`analysis` family — Simulation Toolkit, Parametrics, Traceability, Impact/Coverage, Metrics, Trade Studies, Roll-Ups, OOSEM & Semantic Verification)
 * OpenAPI Packages: com.nomagic.magicdraw.simulation.SimulationHelper, com.nomagic.magicdraw.simulation.execution.SimulationExecution, com.nomagic.magicdraw.modelmetrics.ScriptHelper
 */
public final class AnalysisTools {

    public static final String FAMILY = "analysis";
    public static final int TOOL_COUNT = 52;

    public static final List<String> TOOL_NAMES = Collections.unmodifiableList(Arrays.asList(
        "cameo_run_simulation",
        "cameo_stop_simulation",
        "cameo_pause_simulation",
        "cameo_resume_simulation",
        "cameo_step_simulation",
        "cameo_get_simulation_status",
        "cameo_get_simulation_variables",
        "cameo_set_simulation_variable",
        "cameo_export_simulation_log",
        "cameo_run_parametric_analysis",
        "cameo_get_parametric_results",
        "cameo_trace_dependency_tree",
        "cameo_trace_impact_analysis",
        "cameo_trace_coverage_analysis",
        "cameo_get_unlinked_requirements",
        "cameo_get_orphan_elements",
        "cameo_calculate_metrics",
        "cameo_list_metric_suites",
        "cameo_run_metric_suite",
        "cameo_export_metrics",
        "cameo_find_circular_dependencies",
        "cameo_find_dead_end_flows",
        "cameo_verify_interface_compatibility",
        "cameo_run_trade_study",
        "cameo_get_trade_study_results",
        "cameo_create_analysis_context",
        "cameo_run_roll_up_analysis",
        "cameo_get_roll_up_results",
        "cameo_check_state_reachability",
        "cameo_find_unreachable_states",
        "cameo_verify_allocation",
        "cameo_get_allocation_matrix",
        "cameo_list_methodology_packs",
        "cameo_get_methodology_pack",
        "cameo_get_methodology_guidance",
        "cameo_execute_methodology_recipe",
        "cameo_validate_methodology_recipe",
        "cameo_generate_review_packet",
        "cameo_verify_matrix_consistency",
        "cameo_verify_diagram_visual",
        "cameo_verify_activity_flow_semantics",
        "cameo_verify_port_boundary_consistency",
        "cameo_verify_requirement_quality",
        "cameo_verify_cross_diagram_traceability",
        "cameo_detect_cross_diagram_inconsistencies",
        "cameo_build_cross_diagram_remediation_plan",
        "cameo_proof_model_text",
        "cameo_apply_proofing_patch_plan",
        "cameo_compare_expected_artifact_list",
        "cameo_validate_methodology_package",
        "cameo_export_required_diagrams",
        "cameo_assemble_ppt_pdf"
    ));

    private AnalysisTools() {}
}
