package com.cameomcp.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 2. ModelTools (`model` family — Projects, Elements, Stereotypes, Properties, Relationships & State Semantics)
 * OpenAPI Packages: com.nomagic.magicdraw.core.Project, com.nomagic.magicdraw.core.project.ProjectsManager, com.nomagic.uml2.impl.ElementsFactory, com.nomagic.magicdraw.openapi.uml.ModelElementsManager, com.nomagic.magicdraw.uml.Finder, com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper, com.nomagic.uml2.ext.jmi.helpers.TagsHelper, com.nomagic.magicdraw.copypaste.CopyPasting, com.nomagic.magicdraw.uml.Refactoring
 */
public final class ModelTools {

    public static final String FAMILY = "model";
    public static final int TOOL_COUNT = 52;

    public static final List<String> TOOL_NAMES = Collections.unmodifiableList(Arrays.asList(
        "cameo_get_project",
        "cameo_save_project",
        "cameo_get_ui_state",
        "cameo_get_active_diagram",
        "cameo_get_ui_selection",
        "cameo_query_elements",
        "cameo_get_element",
        "cameo_get_containment_tree",
        "cameo_list_containment_children",
        "cameo_create_element",
        "cameo_create_elements",
        "cameo_modify_element",
        "cameo_move_element",
        "cameo_delete_element",
        "cameo_apply_stereotype",
        "cameo_remove_stereotype",
        "cameo_set_tagged_values",
        "cameo_set_stereotype_metaclasses",
        "cameo_apply_profile",
        "cameo_create_relationship",
        "cameo_get_element_relationships",
        "cameo_reconnect_relationship",
        "cameo_delete_relationship",
        "cameo_set_property_type",
        "cameo_set_property_multiplicity",
        "cameo_set_property_default_value",
        "cameo_set_property_aggregation",
        "cameo_set_specification",
        "cameo_get_specification",
        "cameo_find_usages",
        "cameo_find_by_name",
        "cameo_clone_element",
        "cameo_convert_element_type",
        "cameo_batch_modify_elements",
        "cameo_get_relationships",
        "cameo_get_transition_triggers",
        "cameo_set_transition_trigger",
        "cameo_get_state_behaviors",
        "cameo_set_state_behaviors",
        "cameo_set_usecase_subject",
        "cameo_clone_elements_with_views",
        "cameo_copy_paste_symbol_style",
        "cameo_refactor_convert_element",
        "cameo_refactor_replace_element",
        "cameo_refactor_reverse_relationship",
        "cameo_refactor_extract_model_fragment",
        "cameo_create_new_project",
        "cameo_open_project_file",
        "cameo_close_project",
        "cameo_save_project_as",
        "cameo_list_attached_modules",
        "cameo_find_shared_packages"
    ));

    private ModelTools() {}
}
