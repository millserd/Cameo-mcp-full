package com.cameomcp.plugin;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.GUILog;

import javax.swing.SwingUtilities;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Native Cameo Logging Utility (com.cameomcp.plugin.CameoLog).
 *
 * Publishes every status, connection diagnostic, and tool audit event with the "[Cameo MCP]"
 * prefix directly to:
 *   1. Cameo's Notification / Messages window via Application.getInstance().getGUILog()
 *   2. Cameo's md.log file via java.util.logging.Logger
 *   3. The in-memory ring buffer displayed in McpStatusWindow
 */
public final class CameoLog {

    private static final Logger MD_LOGGER = Logger.getLogger("com.cameomcp.plugin");
    private static final ConcurrentLinkedDeque<String> LOG_RING = new ConcurrentLinkedDeque<>();
    private static final int MAX_LINES = 300;
    private static volatile String lastErrorTrace = "";

    private CameoLog() {}

    public static void info(String message) {
        publish("INFO", message, false);
    }

    public static void warn(String message) {
        publish("WARN", message, true);
    }

    public static void error(String message, Throwable t) {
        if (t != null) {
            StringBuilder sb = new StringBuilder(message).append(" — ").append(t.toString());
            for (StackTraceElement ste : t.getStackTrace()) {
                sb.append("\n  at ").append(ste.toString());
            }
            lastErrorTrace = sb.toString();
        } else {
            lastErrorTrace = message;
        }
        publish("ERROR", message + (t != null ? " (" + t.getMessage() + ")" : ""), true);
    }

    private static void publish(String level, String message, boolean isWarnOrError) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        String formatted = String.format("[%s] [Cameo MCP] [%s] %s", ts, level, message);
        LOG_RING.addLast(formatted);
        while (LOG_RING.size() > MAX_LINES) {
            LOG_RING.pollFirst();
        }

        if ("ERROR".equals(level)) {
            MD_LOGGER.log(Level.SEVERE, "[Cameo MCP] " + message);
        } else if ("WARN".equals(level)) {
            MD_LOGGER.log(Level.WARNING, "[Cameo MCP] " + message);
        } else {
            MD_LOGGER.log(Level.INFO, "[Cameo MCP] " + message);
        }

        Runnable guiTask = () -> {
            try {
                Application app = Application.getInstance();
                if (app != null) {
                    GUILog guiLog = app.getGUILog();
                    if (guiLog != null) {
                        guiLog.log("[Cameo MCP] " + (isWarnOrError ? "[" + level + "] " : "") + message);
                    }
                }
            } catch (Throwable ignored) {}
        };
        if (SwingUtilities.isEventDispatchThread()) {
            guiTask.run();
        } else {
            SwingUtilities.invokeLater(guiTask);
        }
    }

    public static ConcurrentLinkedDeque<String> getRecentLogs() {
        return LOG_RING;
    }

    public static String getLastErrorTrace() {
        return lastErrorTrace;
    }
}
