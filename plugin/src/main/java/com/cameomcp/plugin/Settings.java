package com.cameomcp.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Configuration loader reading ~/.cameo-mcp/settings.properties.
 * Port priority:
 *   1. JVM system property (-Dcameo.mcp.port=18740)
 *   2. Environment variable (CAMEO_MCP_PORT or CAMEO_BRIDGE_PORT)
 *   3. ~/.cameo-mcp/settings.properties (port=18740)
 *   4. Default 18740
 */
public final class Settings {

    public static final int DEFAULT_PORT = 18740;
    private int port = DEFAULT_PORT;
    private String host = "127.0.0.1";
    private String bearerToken = "";
    private boolean allowMacros = false; // scripts run arbitrary code inside Cameo: opt-in only

    public static Settings load() {
        Settings s = new Settings();
        File propFile = getSettingsFile();
        if (propFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propFile)) {
                Properties p = new Properties();
                p.load(fis);
                s.port = parsePort(p.getProperty("port"), DEFAULT_PORT);
                s.host = p.getProperty("host", "127.0.0.1");
                s.bearerToken = p.getProperty("bearerToken", "");
                s.allowMacros = Boolean.parseBoolean(p.getProperty("allowMacros", "false"));
            } catch (Exception ignored) {}
        }
        String envPort = System.getenv("CAMEO_MCP_PORT");
        if (envPort == null || envPort.isBlank()) {
            envPort = System.getenv("CAMEO_BRIDGE_PORT");
        }
        if (envPort != null && !envPort.isBlank()) {
            s.port = parsePort(envPort, s.port);
        }
        String sysPort = System.getProperty("cameo.mcp.port");
        if (sysPort != null && !sysPort.isBlank()) {
            s.port = parsePort(sysPort, s.port);
        }
        return s;
    }

    /** Returns false (and logs why) if the settings file could not be written. */
    public boolean save() {
        try {
            File propFile = getSettingsFile();
            propFile.getParentFile().mkdirs();
            Properties p = new Properties();
            p.setProperty("port", String.valueOf(port));
            p.setProperty("host", host);
            p.setProperty("bearerToken", bearerToken != null ? bearerToken : "");
            p.setProperty("allowMacros", String.valueOf(allowMacros));
            try (FileOutputStream fos = new FileOutputStream(propFile)) {
                p.store(fos, "Cameo MCP Plugin Settings");
            }
            return true;
        } catch (Exception e) {
            CameoLog.error("Could not save " + getSettingsFile(), e);
            return false;
        }
    }

    public static File getSettingsFile() {
        return new File(System.getProperty("user.home"), ".cameo-mcp/settings.properties");
    }

    private static int parsePort(String val, int fallback) {
        if (val == null || val.isBlank()) return fallback;
        try {
            int port = Integer.parseInt(val.trim());
            return isValidPort(port) ? port : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean isValidPort(int port) {
        return port >= 1024 && port <= 65535;
    }

    public int getPort() { return port; }
    public void setPort(int port) {
        if (!isValidPort(port)) throw new IllegalArgumentException("Port must be 1024-65535");
        this.port = port;
    }
    public String getHost() { return host; }
    public String getBearerToken() { return bearerToken; }
    public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }
    public boolean isAllowMacros() { return allowMacros; }
    public void setAllowMacros(boolean allowMacros) { this.allowMacros = allowMacros; }
}
