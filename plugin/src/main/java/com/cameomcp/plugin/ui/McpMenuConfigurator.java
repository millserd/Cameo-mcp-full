package com.cameomcp.plugin.ui;

import com.cameomcp.plugin.CameoLog;
import com.cameomcp.plugin.CameoMcpPlugin;
import com.nomagic.actions.AMConfigurator;
import com.nomagic.actions.ActionsCategory;
import com.nomagic.actions.ActionsManager;
import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.actions.MDActionsCategory;

import java.awt.event.ActionEvent;

/**
 * Registers the top-level "MCP" menu in Cameo Systems Modeler / CATIA Magic (2024x & 2026x)
 * (aligned with millserd/Cameo-mcp McpMenuConfigurator.java).
 *
 * Menu Items:
 * 1. MCP Server Status... (opens McpStatusWindow)
 * 2. Run Self-Test & Log to Cameo (executes ConnectionTest and logs to GUILog + md.log)
 * 3. Restart MCP Server
 * 4. Copy Client Config JSON (copies HTTP + Stdio MCP configuration via ClientConfigs)
 */
public class McpMenuConfigurator implements AMConfigurator {

    private final CameoMcpPlugin plugin;

    public McpMenuConfigurator(CameoMcpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ActionsManager manager) {
        CameoLog.info("Registering MCP main menu");
        // Top-level main-menu categories must be MDActionsCategory (see Cameo openapi/examples MainMenuConfigurator);
        // a plain ActionsCategory showed the menu but its items never reached actionPerformed on 2026x.
        ActionsCategory mcpMenu = (ActionsCategory) manager.getActionFor("CAMEO_MCP_TOP_MENU");
        if (mcpMenu == null) {
            mcpMenu = new MDActionsCategory("CAMEO_MCP_TOP_MENU", "MCP");
            mcpMenu.setNested(true);
            manager.addCategory(mcpMenu);
        }

        MDAction statusWindowAction = new MDAction(
                "CAMEO_MCP_STATUS_WINDOW",
                "MCP Server Status...",
                null,
                null) {
            { setDescription("Open the Cameo MCP Server Status, Port, Bearer Token, and Live Audit Window"); }
            @Override
            public void actionPerformed(ActionEvent e) {
                CameoLog.info("Menu action: " + getName());
                McpStatusWindow.showWindow(plugin);
            }
        };

        MDAction selfTestAction = new MDAction(
                "CAMEO_MCP_RUN_SELF_TEST",
                "Run Connection Self-Test (Log to Cameo)",
                null,
                null) {
            { setDescription("Verify local HTTP /status and /mcp endpoints and publish results to Cameo Notification Log"); }
            @Override
            public void actionPerformed(ActionEvent e) {
                CameoLog.info("Menu action: " + getName());
                String report = ConnectionTest.runSelfTest(plugin.getSettings().getHost(), plugin.getSettings().getPort());
                CameoLog.info(report);
            }
        };

        MDAction restartAction = new MDAction(
                "CAMEO_MCP_RESTART_RUNTIME",
                "Restart MCP Server",
                null,
                null) {
            { setDescription("Restart the embedded MCP HTTP & JSON-RPC server"); }
            @Override
            public void actionPerformed(ActionEvent e) {
                CameoLog.info("Menu action: " + getName());
                plugin.getRuntime().start();
            }
        };

        MDAction copyConfigAction = new MDAction(
                "CAMEO_MCP_COPY_CLIENT_CONFIG",
                "Copy MCP Client Config JSON",
                null,
                null) {
            { setDescription("Copy MCP client configuration snippet to system clipboard"); }
            @Override
            public void actionPerformed(ActionEvent e) {
                CameoLog.info("Menu action: " + getName());
                ClientConfigs.copyToClipboard(plugin.getSettings().getHost(), plugin.getSettings().getPort());
                CameoLog.info("Copied MCP client configuration JSON to clipboard.");
            }
        };

        mcpMenu.addAction(statusWindowAction);
        mcpMenu.addAction(selfTestAction);
        mcpMenu.addAction(restartAction);
        mcpMenu.addAction(copyConfigAction);

    }

    @Override
    public int getPriority() {
        return AMConfigurator.MEDIUM_PRIORITY;
    }
}
