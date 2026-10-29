package com.cameomcp.stdio;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
 * Stdio MCP bridge and small CLI for the Cameo MCP plugin.
 * Without options it relays newline-delimited JSON-RPC between stdin/stdout and http://127.0.0.1:18740/mcp.
 *   --host h --port n | --url u     where the plugin listens
 *   --token t                       Bearer token (or env CAMEO_MCP_TOKEN)
 *   --diagnose                      print the plugin's /status
 *   --list                          list tool names and descriptions
 *   --describe tool                 print one tool's description and input schema
 *   --call tool [json-args]         call one tool and print the result
 */
public final class StdioLauncher {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 18740;
        String url = null;
        String token = System.getenv("CAMEO_MCP_TOKEN");
        String mode = "relay";
        String target = null;
        String callArgs = "{}";
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            String next = i + 1 < args.length ? args[i + 1] : null;
            switch (a) {
                case "--host" -> { host = need(a, next); i++; }
                case "--port" -> { port = Integer.parseInt(need(a, next)); i++; }
                case "--url" -> { url = need(a, next); i++; }
                case "--token" -> { token = need(a, next); i++; }
                case "--diagnose" -> mode = "diagnose";
                case "--list" -> mode = "list";
                case "--describe" -> { mode = "describe"; target = need(a, next); i++; }
                case "--call" -> {
                    mode = "call";
                    target = need(a, next);
                    i++;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) callArgs = args[++i];
                }
                default -> {
                    System.err.println("Unknown option " + a);
                    System.exit(2);
                }
            }
        }
        URI mcp = URI.create(url != null ? url : "http://" + host + ":" + port + "/mcp");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        switch (mode) {
            case "list" -> {
                for (JsonNode t : Catalog.tools())
                    System.out.println(t.path("name").asText() + "  [" + t.path("family").asText() + "]  " + t.path("description").asText());
                return;
            }
            case "describe" -> {
                JsonNode t = Catalog.tool(target);
                if (t == null) {
                    System.err.println("No tool named " + target + " (see --list)");
                    System.exit(1);
                }
                System.out.println(Catalog.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(t));
                return;
            }
            case "diagnose" -> {
                HttpRequest.Builder rb = HttpRequest.newBuilder(mcp.resolve("/status")).GET().timeout(Duration.ofSeconds(10));
                if (token != null && !token.isBlank()) rb.header("Authorization", "Bearer " + token);
                try {
                    HttpResponse<String> r = client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
                    System.out.println("HTTP " + r.statusCode() + " " + r.body());
                } catch (Exception e) {
                    System.out.println("Cannot reach Cameo MCP at " + mcp + ": " + e);
                    System.exit(1);
                }
                return;
            }
            case "call" -> {
                ObjectNode req = Catalog.JSON.createObjectNode().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call");
                req.putObject("params").put("name", target).set("arguments", Catalog.JSON.readTree(callArgs));
                System.out.println(post(client, mcp, token, null, req.toString()).body());
                return;
            }
            default -> relay(client, mcp, token);
        }
    }

    /** Newline-delimited JSON-RPC relay. Every request with an id gets exactly one reply, errors included. */
    private static void relay(HttpClient client, URI mcp, String token) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String session = null;
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) continue;
            JsonNode id = null;
            try {
                id = Catalog.JSON.readTree(line).get("id");
            } catch (Exception ignored) {
                // forward as-is; the server reports the parse error
            }
            try {
                HttpResponse<String> r = post(client, mcp, token, session, line);
                session = r.headers().firstValue("Mcp-Session-Id").orElse(session);
                if (r.body() != null && !r.body().isBlank()) out.println(r.body().replace("\n", " ").replace("\r", ""));
            } catch (Exception e) {
                if (id == null) continue; // a notification gets no reply
                ObjectNode err = Catalog.JSON.createObjectNode().put("jsonrpc", "2.0");
                err.set("id", id);
                err.putObject("error").put("code", -32000).put("message", "Cannot reach Cameo MCP at " + mcp
                        + ". Is Cameo running with the plugin? Check MCP > MCP Server Status. (" + e + ")");
                out.println(err);
            }
        }
    }

    private static HttpResponse<String> post(HttpClient client, URI mcp, String token, String session, String body) throws Exception {
        HttpRequest.Builder rb = HttpRequest.newBuilder(mcp)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (token != null && !token.isBlank()) rb.header("Authorization", "Bearer " + token);
        if (session != null) rb.header("Mcp-Session-Id", session);
        return client.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String need(String opt, String value) {
        if (value == null) {
            System.err.println(opt + " needs a value");
            System.exit(2);
        }
        return value;
    }
}
