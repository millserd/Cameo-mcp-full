package com.cameomcp.plugin;

import com.cameomcp.catalog.Catalog;
import com.cameomcp.catalog.CatalogPrompts;
import com.cameomcp.mcp.CallGuard;
import com.cameomcp.plugin.tools.Tool;
import com.cameomcp.plugin.tools.ToolError;
import com.cameomcp.plugin.tools.Tools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Embedded MCP server (Streamable HTTP transport, JSON responses only).
 *   POST /mcp      JSON-RPC 2.0 request or notification
 *   DELETE /mcp    end the session named by Mcp-Session-Id
 *   GET /status, GET /health   plain JSON health report
 */
public class McpRuntime {

    static final List<String> PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");
    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    private static final long SESSION_IDLE_MS = 30 * 60 * 1000L;

    private final Settings settings;
    private final CallGuard callGuard;
    private final Map<String, Tool> tools = Tools.all();
    private HttpServer httpServer;
    private ExecutorService executor;
    private final AtomicLong totalRequests = new AtomicLong();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private volatile ArrayNode toolList;

    public McpRuntime(Settings settings) {
        this.settings = settings;
        this.callGuard = new CallGuard(settings.getBearerToken(), settings.isAllowMacros());
    }

    public synchronized boolean start() {
        stop();
        if (settings.getLoadError() != null) {
            CameoLog.error(settings.getLoadError(), null);
            return false;
        }
        try {
            httpServer = HttpServer.create(new InetSocketAddress(settings.getHost(), settings.getPort()), 64);
            executor = Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "Cameo-MCP-HTTP");
                t.setDaemon(true);
                return t;
            });
            httpServer.setExecutor(executor);
            httpServer.createContext("/mcp", ex -> guarded(ex, this::handleMcp));
            httpServer.createContext("/status", ex -> guarded(ex, this::handleStatus));
            httpServer.createContext("/health", ex -> guarded(ex, this::handleStatus));
            httpServer.start();
            CameoLog.info(String.format("MCP Server ONLINE at http://%s:%d/mcp | %s | %d tools",
                    settings.getHost(), settings.getPort(), cameoRelease(), tools.size()));
            return true;
        } catch (Exception e) {
            CameoLog.error("Failed to start MCP Server on port " + settings.getPort(), e);
            stop();
            return false;
        }
    }

    public synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            CameoLog.info("MCP Server stopped on port " + settings.getPort());
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean restartWithPort(int newPort) {
        settings.setPort(newPort);
        settings.save();
        return start();
    }

    public boolean isRunning() { return httpServer != null; }
    public long getTotalRequests() { return totalRequests.get(); }
    public CallGuard getCallGuard() { return callGuard; }
    public Map<String, Tool> getTools() { return tools; }

    public int getActiveClientSessionCount() {
        long cutoff = System.currentTimeMillis() - SESSION_IDLE_MS;
        sessions.values().removeIf(last -> last < cutoff);
        return sessions.size();
    }

    // ---------------------------------------------------------------- HTTP

    private interface Handler { void handle(HttpExchange ex) throws Exception; }

    /** Origin + token checks, and a guaranteed reply: an uncaught error must never leave the client waiting. */
    private void guarded(HttpExchange ex, Handler h) {
        // Not try-with-resources: that would close the exchange before the catch block could send the 500.
        try {
            totalRequests.incrementAndGet();
            if (!callGuard.verifyOrigin(ex.getRequestHeaders().getFirst("Origin"))) {
                sendJson(ex, 403, error(null, -32000, "Forbidden Origin header"));
                return;
            }
            if (!callGuard.verifyBearerToken(ex.getRequestHeaders().getFirst("Authorization"))) {
                sendJson(ex, 401, error(null, -32000, "Missing or wrong Bearer token"));
                return;
            }
            h.handle(ex);
        } catch (Throwable t) {
            CameoLog.error("HTTP handler failed for " + ex.getRequestURI(), t);
            try {
                sendJson(ex, 500, error(null, -32603, "Internal error: " + t));
            } catch (Throwable ignored) {
                // response already started; closing the exchange ends it
            }
        } finally {
            ex.close();
        }
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        ObjectNode s = Catalog.JSON.createObjectNode()
                .put("status", "ok").put("pluginId", "com.cameomcp.plugin").put("version", Catalog.version())
                .put("endpoint", "http://" + settings.getHost() + ":" + settings.getPort() + "/mcp")
                .put("cameo", cameoRelease()).put("javaVersion", System.getProperty("java.version"))
                .put("activeSessions", getActiveClientSessionCount())
                .put("macrosAllowed", callGuard.isMacrosAllowed()).put("tools", tools.size());
        Project p = Application.getInstance().getProject();
        ObjectNode proj = s.putObject("project").put("open", p != null);
        if (p != null) proj.put("name", p.getName()).put("dirty", p.isDirty());
        sendJson(ex, 200, s);
    }

    private void handleMcp(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("DELETE".equalsIgnoreCase(method)) {
            String sid = ex.getRequestHeaders().getFirst("Mcp-Session-Id");
            boolean known = sid != null && sessions.remove(sid) != null;
            sendJson(ex, known ? 200 : 404, Catalog.JSON.createObjectNode().put("terminated", known));
            return;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            ex.getResponseHeaders().set("Allow", "POST, DELETE");
            sendJson(ex, 405, error(null, -32000, "Use POST /mcp for JSON-RPC requests"));
            return;
        }
        // Browsers send text/plain without a CORS preflight; requiring JSON blocks blind cross-site posts.
        String ctype = ex.getRequestHeaders().getFirst("Content-Type");
        if (ctype == null || !ctype.toLowerCase().startsWith("application/json")) {
            sendJson(ex, 415, error(null, -32000, "Content-Type must be application/json"));
            return;
        }
        byte[] body;
        try (InputStream in = ex.getRequestBody()) {
            body = in.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (body.length > MAX_BODY_BYTES) {
            sendJson(ex, 413, error(null, -32600, "Request larger than " + MAX_BODY_BYTES + " bytes"));
            return;
        }
        JsonNode req;
        try {
            req = Catalog.JSON.readTree(body);
        } catch (IOException e) {
            sendJson(ex, 400, error(null, -32700, "Parse error: " + e.getMessage()));
            return;
        }
        if (req == null || req.isArray()) {
            sendJson(ex, 400, error(null, -32600, "Batched JSON-RPC requests are not supported; send one request per POST"));
            return;
        }
        if (!req.isObject() || !req.path("method").isTextual()) {
            sendJson(ex, 400, error(req.get("id"), -32600, "Invalid JSON-RPC request"));
            return;
        }
        String sid = ex.getRequestHeaders().getFirst("Mcp-Session-Id");
        String rpc = req.get("method").asText();
        if ("initialize".equals(rpc)) {
            sid = UUID.randomUUID().toString();
            sessions.put(sid, System.currentTimeMillis());
        } else if (sid != null) {
            sessions.computeIfPresent(sid, (k, v) -> System.currentTimeMillis());
        }
        if (sid != null) ex.getResponseHeaders().set("Mcp-Session-Id", sid);

        JsonNode id = req.get("id");
        if (id == null) { // notification: never answered
            ex.sendResponseHeaders(202, -1);
            return;
        }
        sendJson(ex, 200, dispatch(rpc, id, req.path("params")));
    }

    // ---------------------------------------------------------------- JSON-RPC

    ObjectNode dispatch(String rpc, JsonNode id, JsonNode params) {
        switch (rpc) {
            case "ping":
                return result(id, Catalog.JSON.createObjectNode());
            case "initialize": {
                String asked = params.path("protocolVersion").asText("");
                ObjectNode r = Catalog.JSON.createObjectNode()
                        .put("protocolVersion", PROTOCOL_VERSIONS.contains(asked) ? asked : PROTOCOL_VERSIONS.get(0));
                ObjectNode caps = r.putObject("capabilities");
                caps.putObject("tools").put("listChanged", false);
                caps.putObject("prompts").put("listChanged", false);
                caps.putObject("resources").put("listChanged", false).put("subscribe", false);
                r.putObject("serverInfo").put("name", "cameo-mcp").put("version", Catalog.version());
                r.put("instructions", "Call cameo_status first. Element ids come from cameo_query_elements / cameo_get_containment_tree. "
                        + "Every write tool is one undoable step (cameo_undo).");
                CameoLog.info("MCP client initialized: " + params.path("clientInfo").path("name").asText("unknown"));
                return result(id, r);
            }
            case "tools/list": {
                ObjectNode r = Catalog.JSON.createObjectNode();
                r.set("tools", toolList());
                return result(id, r);
            }
            case "tools/call":
                return callTool(id, params);
            case "prompts/list": {
                ObjectNode r = Catalog.JSON.createObjectNode();
                r.set("prompts", CatalogPrompts.list());
                return result(id, r);
            }
            case "prompts/get": {
                ObjectNode p = CatalogPrompts.get(params.path("name").asText(), params.path("arguments").path("input").asText(null));
                return p == null ? error(id, -32602, "Unknown prompt: " + params.path("name").asText()) : result(id, p);
            }
            case "resources/list": {
                ObjectNode r = Catalog.JSON.createObjectNode();
                ArrayNode res = r.putArray("resources");
                res.addObject().put("uri", "cameo://guide").put("name", "Cameo MCP agent guide").put("mimeType", "text/markdown");
                res.addObject().put("uri", "cameo://tools").put("name", "Cameo MCP tool manifest").put("mimeType", "application/json");
                return result(id, r);
            }
            case "resources/read": {
                String uri = params.path("uri").asText();
                String text;
                String mime;
                if ("cameo://guide".equals(uri)) {
                    text = Resources.text("/skills/cameo-mcp/SKILL.md");
                    mime = "text/markdown";
                } else if ("cameo://tools".equals(uri)) {
                    text = toolList().toString();
                    mime = "application/json";
                } else {
                    return error(id, -32002, "Resource not found: " + uri);
                }
                ObjectNode r = Catalog.JSON.createObjectNode();
                r.putArray("contents").addObject().put("uri", uri).put("mimeType", mime).put("text", text);
                return result(id, r);
            }
            default:
                return error(id, -32601, "Method not found: " + rpc);
        }
    }

    /** Manifest entries for the tools that are actually registered. */
    private ArrayNode toolList() {
        ArrayNode l = toolList;
        if (l == null) {
            l = Catalog.JSON.createArrayNode();
            for (JsonNode t : Catalog.tools()) {
                Tool tool = tools.get(t.path("name").asText());
                if (tool == null) continue;
                ObjectNode o = l.addObject();
                o.put("name", tool.name());
                o.set("description", t.get("description"));
                o.set("inputSchema", t.get("inputSchema"));
                ObjectNode ann = o.putObject("annotations");
                ann.put("readOnlyHint", tool.mode() == Tool.Mode.READ);
                if (t.has("destructive")) ann.put("destructiveHint", t.get("destructive").asBoolean());
            }
            toolList = l;
        }
        return l;
    }

    private ObjectNode callTool(JsonNode id, JsonNode params) {
        String name = params.path("name").asText("");
        Tool tool = tools.get(name);
        if (tool == null) return error(id, -32602, "Unknown tool: " + name);
        JsonNode args = params.path("arguments");
        if (args.isMissingNode() || args.isNull()) args = Catalog.JSON.createObjectNode();
        if (!args.isObject()) return error(id, -32602, "Tool arguments must be a JSON object");
        long t0 = System.currentTimeMillis();
        ObjectNode r = Catalog.JSON.createObjectNode();
        ArrayNode content = r.putArray("content");
        try {
            Object out = Tools.execute(tool, args, callGuard);
            if (out instanceof Tools.Image img) {
                content.addObject().put("type", "image").put("data", img.base64()).put("mimeType", img.mimeType());
                if (img.meta() != null) content.addObject().put("type", "text").put("text", img.meta().toString());
            } else {
                JsonNode json = Catalog.JSON.valueToTree(out);
                content.addObject().put("type", "text").put("text", json.toString());
                if (json.isObject()) r.set("structuredContent", json);
            }
            r.put("isError", false);
            CameoLog.info("Tool " + name + " ok in " + (System.currentTimeMillis() - t0) + " ms");
        } catch (Throwable e) {
            Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
            String msg = cause instanceof ToolError ? cause.getMessage() : cause.toString();
            if (!(cause instanceof ToolError)) CameoLog.error("Tool " + name + " failed", cause);
            else CameoLog.warn("Tool " + name + ": " + msg);
            content.addObject().put("type", "text").put("text", msg);
            r.put("isError", true);
        }
        return result(id, r);
    }

    // ---------------------------------------------------------------- helpers

    private static ObjectNode result(JsonNode id, JsonNode result) {
        ObjectNode o = Catalog.JSON.createObjectNode().put("jsonrpc", "2.0");
        o.set("id", id);
        o.set("result", result);
        return o;
    }

    static ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode o = Catalog.JSON.createObjectNode().put("jsonrpc", "2.0");
        o.set("id", id);
        o.putObject("error").put("code", code).put("message", message);
        return o;
    }

    private static void sendJson(HttpExchange ex, int status, JsonNode json) throws IOException {
        byte[] bytes = Catalog.JSON.writeValueAsBytes(json);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static String cameoRelease() {
        try {
            Application.Runtime rt = Application.runtime();
            return rt.getApplicationName() + " " + rt.getFullVersion();
        } catch (Throwable t) {
            return "Cameo (version unknown)";
        }
    }
}
