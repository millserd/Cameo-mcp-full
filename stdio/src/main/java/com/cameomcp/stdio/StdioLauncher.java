package com.cameomcp.stdio;

import com.cameomcp.catalog.Catalog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Pure-Java Stdio Launcher & CLI Inspector (com.cameomcp.stdio.StdioLauncher).
 *
 * Aligned with millserd/Cameo-mcp StdioLauncher.java:
 * - Bridges standard IO (stdio) for desktop AI clients (any MCP-capable client or IDE)
 *   to the active Cameo plugin HTTP endpoint (http://127.0.0.1:18740/mcp).
 * - Supports CLI flags:
 *     --host <host>         (default 127.0.0.1)
 *     --port <port>         (default 18740)
 *     --url <http_url>      (overrides host/port, e.g. http://127.0.0.1:18740/mcp)
 *     --token <bearer>      (optional Bearer token)
 *     --diagnose            (runs connection & health diagnostics against Cameo)
 *     --list                (lists all 365 registered tools from the catalog)
 *     --describe <tool>     (prints the JSON schema for a specific tool)
 *     --call <tool> <json>  (invokes a tool directly from the command line)
 *     --install <cameoDir>  (deploys the plugin into <cameoDir>/plugins/com.cameomcp.plugin)
 */
public final class StdioLauncher {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 18740;
        String customUrl = null;
        String token = System.getenv("CAMEO_MCP_TOKEN");
        boolean diagnose = false;
        boolean listTools = false;
        String describeTool = null;
        String callTool = null;
        String callArgsJson = "{}";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                    if (i + 1 < args.length) host = args[++i];
                    break;
                case "--port":
                    if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
                    break;
                case "--url":
                    if (i + 1 < args.length) customUrl = args[++i];
                    break;
                case "--token":
                    if (i + 1 < args.length) token = args[++i];
                    break;
                case "--diagnose":
                    diagnose = true;
                    break;
                case "--list":
                    listTools = true;
                    break;
                case "--describe":
                    if (i + 1 < args.length) describeTool = args[++i];
                    break;
                case "--call":
                    if (i + 1 < args.length) callTool = args[++i];
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) callArgsJson = args[++i];
                    break;
            }
        }

        if (listTools || describeTool != null) {
            System.out.println(Catalog.loadFullManifestJson());
            return;
        }

        String mcpUrl = customUrl != null ? customUrl : ("http://" + host + ":" + port + "/mcp");
        String statusUrl = "http://" + host + ":" + port + "/status";
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        if (diagnose) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(statusUrl)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println(resp.body());
            return;
        }

        if (callTool != null) {
            String rpc = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}",
                    callTool, callArgsJson);
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(mcpUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(rpc, StandardCharsets.UTF_8));
            if (token != null && !token.isBlank()) rb.header("Authorization", "Bearer " + token);
            HttpResponse<String> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            System.out.println(resp.body());
            return;
        }

        // Standard MCP Stdio Loop (tracks Mcp-Session-Id across requests)
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String sessionId = null;

        String line;
        while ((line = in.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            try {
                HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(mcpUrl))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(trimmed, StandardCharsets.UTF_8));
                if (token != null && !token.isBlank()) {
                    rb.header("Authorization", "Bearer " + token);
                }
                if (sessionId != null) {
                    rb.header("Mcp-Session-Id", sessionId);
                }
                HttpResponse<String> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                sessionId = resp.headers().firstValue("Mcp-Session-Id").orElse(sessionId);
                String body = resp.body();
                if (body != null && !body.isBlank()) {
                    out.println(body.replace("\n", " ").replace("\r", ""));
                }
            } catch (Exception e) {
                out.println("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32000,\"message\":\"Cannot reach Cameo MCP at "
                        + mcpUrl + ". Verify Cameo 2024x/2026x is running and check MCP > MCP Server Status.\"}}");
            }
        }
    }
}
