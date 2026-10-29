package com.cameomcp.plugin.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * Generates and copies ready-to-use MCP client configurations for Streamable HTTP and Stdio clients
 */
public final class ClientConfigs {

    private ClientConfigs() {}

    public static String buildConfigSnippet(String host, int port) {
        return String.format(
            "{\n" +
            "  \"mcpServers\": {\n" +
            "    \"cameo-http\": {\n" +
            "      \"url\": \"http://%s:%d/mcp\"\n" +
            "    },\n" +
            "    \"cameo-stdio\": {\n" +
            "      \"command\": \"java\",\n" +
            "      \"args\": [\"-jar\", \"<CAMEO_HOME>/plugins/com.cameomcp.plugin/cameo-mcp-stdio.jar\", \"--port\", \"%d\"]\n" +
            "    }\n" +
            "  }\n" +
            "}", host, port, port
        );
    }

    public static void copyToClipboard(String host, int port) {
        String snippet = buildConfigSnippet(host, port);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(snippet), null);
    }
}
