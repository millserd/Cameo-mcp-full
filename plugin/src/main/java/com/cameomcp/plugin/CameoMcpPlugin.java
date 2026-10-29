package com.cameomcp.plugin;

import com.cameomcp.plugin.ui.McpMenuConfigurator;
import com.nomagic.magicdraw.actions.ActionsConfiguratorsManager;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.project.ProjectEventListenerAdapter;
import com.nomagic.magicdraw.plugins.Plugin;

/**
 * Main Cameo Plugin Entry Point (com.cameomcp.plugin.CameoMcpPlugin).
 *
 * Startup sequence (Cameo 2024x and 2026x):
 * 1. Loads ~/.cameo-mcp/settings.properties, env vars (CAMEO_MCP_PORT / CAMEO_BRIDGE_PORT), or -Dcameo.mcp.port.
 * 2. Registers top-level "MCP" menu in Cameo's main window via McpMenuConfigurator.
 * 3. Starts McpRuntime (embedded HTTP + JSON-RPC 2.0 server on 127.0.0.1:18740).
 * 4. Publishes live lifecycle telemetry to Cameo's Notification Window (GUILog) and md.log via CameoLog.
 */
public class CameoMcpPlugin extends Plugin {

    private static CameoMcpPlugin instance;
    private Settings settings;
    private McpRuntime runtime;

    @Override
    public void init() {
        instance = this;
        this.settings = Settings.load();
        Resources.syncToUserHome();
        this.runtime = new McpRuntime(this.settings);

        // Register top-level "MCP" menu in Cameo main window
        ActionsConfiguratorsManager.getInstance()
                .addMainMenuConfigurator(new McpMenuConfigurator(this));

        Application.getInstance().addActivityAfterStartup(() -> {
            this.runtime.start();
            registerProjectListeners();
        });
    }

    private void registerProjectListeners() {
        Application.getInstance().addProjectEventListener(new ProjectEventListenerAdapter() {
            @Override
            public void projectOpened(Project project) {
                CameoLog.info("Project opened: " + project.getName());
                runtime.recordModelEvent("PROJECT_OPENED", project.getName(),
                        project.getPrimaryModel() != null ? project.getPrimaryModel().getID() : "");
            }

            @Override
            public void projectClosed(Project project) {
                CameoLog.info("Project closed: " + (project != null ? project.getName() : "unknown"));
            }

            @Override
            public void projectSaved(Project project, boolean savedInServer) {
                CameoLog.info("Project saved: " + project.getName() + (savedInServer ? " (Teamwork Cloud)" : " (Local)"));
            }
        });
    }

    @Override
    public boolean close() {
        if (this.runtime != null) {
            this.runtime.stop();
        }
        return true;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    public static CameoMcpPlugin getInstance() {
        return instance;
    }

    public Settings getSettings() {
        return settings;
    }

    public McpRuntime getRuntime() {
        return runtime;
    }
}
