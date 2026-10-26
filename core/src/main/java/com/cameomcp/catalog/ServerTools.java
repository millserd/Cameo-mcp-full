package com.cameomcp.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 1. ServerTools (`server` family — Connection, Discovery, Undo/Redo, UI Actions & Logging)
 * OpenAPI Packages: com.nomagic.magicdraw.core.Application, com.nomagic.magicdraw.core.GUILog, com.nomagic.magicdraw.commands.CommandHistory, com.nomagic.magicdraw.openapi.uml.SessionManager, com.nomagic.magicdraw.actions.ActionsManager
 */
public final class ServerTools {

    public static final String FAMILY = "server";
    public static final int TOOL_COUNT = 19;

    public static final List<String> TOOL_NAMES = Collections.unmodifiableList(Arrays.asList(
        "cameo_status",
        "cameo_get_capabilities",
        "cameo_probe_bridge",
        "cameo_list_tools",
        "cameo_list_families",
        "cameo_list_prompts",
        "cameo_get_last_error",
        "cameo_reset_session",
        "cameo_undo",
        "cameo_redo",
        "cameo_log",
        "cameo_get_command_history",
        "cameo_clear_command_history",
        "cameo_batch_create_elements",
        "cameo_batch_create_relationships",
        "cameo_list_ui_actions",
        "cameo_invoke_ui_action",
        "cameo_select_in_containment_tree",
        "cameo_write_gui_log"
    ));

    private ServerTools() {}
}
