package com.cameomcp.plugin;

import com.cameomcp.catalog.Catalog;
import com.cameomcp.catalog.CatalogPrompts;
import com.cameomcp.mcp.CallGuard;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Embedded Java MCP Server Runtime & HTTP Transport (com.cameomcp.plugin.McpRuntime).
 *
 * Supports both millserd/Cameo-mcp and ajhcs/cameo-mcp-bridge transport conventions:
 * - POST   /mcp                 : Primary MCP JSON-RPC 2.0 endpoint (with Mcp-Session-Id header management)
 * - DELETE /mcp                 : Terminates active session designated by Mcp-Session-Id
 * - GET    /mcp                 : Returns 405 Method Not Allowed (per MCP Streamable HTTP spec when SSE push is off)
 * - GET    /status & /health    : Plain JSON health report (plus /api/v1/status alias)
 * - GET    /capabilities        : Machine-readable 365-tool capability manifest (plus /api/v1/capabilities alias)
 * - POST   /api/v1/tools/{name} : Direct REST tool invocation endpoint
 */
public class McpRuntime {

    private final Settings settings;
    private final CallGuard callGuard;
    private HttpServer httpServer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong eventSeq = new AtomicLong(0);
    private final Map<String, Long> activeSessions = new ConcurrentHashMap<>();
    private final List<String> modelEvents = new CopyOnWriteArrayList<>();

    public McpRuntime(Settings settings) {
        this.settings = settings;
        this.callGuard = new CallGuard(settings.getBearerToken(), settings.isAllowMacros());
    }

    public synchronized boolean start() {
        try {
            stop();
            this.httpServer = HttpServer.create(new InetSocketAddress(settings.getHost(), settings.getPort()), 64);
            this.httpServer.setExecutor(Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "Cameo-MCP-HTTP");
                t.setDaemon(true);
                return t;
            }));

            this.httpServer.createContext("/mcp", this::handleMcpEndpoint);
            this.httpServer.createContext("/status", this::handleStatusEndpoint);
            this.httpServer.createContext("/health", this::handleStatusEndpoint);
            this.httpServer.createContext("/api/v1/status", this::handleStatusEndpoint);
            this.httpServer.createContext("/capabilities", this::handleCapabilitiesEndpoint);
            this.httpServer.createContext("/api/v1/capabilities", this::handleCapabilitiesEndpoint);

            this.httpServer.start();
            this.running.set(true);
            CameoLog.info(String.format(
                    "MCP Server ONLINE at http://%s:%d/mcp | Release: %s | Catalog: 365 tools across 7 families",
                    settings.getHost(), settings.getPort(), detectCameoRelease()));
            return true;
        } catch (Exception e) {
            CameoLog.error("Failed to start MCP Server on port " + settings.getPort(), e);
            return false;
        }
    }

    public synchronized void stop() {
        if (this.httpServer != null) {
            this.httpServer.stop(0);
            this.httpServer = null;
            this.running.set(false);
            CameoLog.info("MCP Server stopped on port " + settings.getPort());
        }
    }

    public boolean restartWithPort(int newPort) {
        settings.setPort(newPort);
        settings.save();
        return start();
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public int getActiveClientSessionCount() {
        return activeSessions.size();
    }

    public CallGuard getCallGuard() {
        return callGuard;
    }

    public void recordModelEvent(String type, String name, String id) {
        long seq = eventSeq.incrementAndGet();
        modelEvents.add(String.format("{\"seq\":%d,\"type\":\"%s\",\"name\":\"%s\",\"id\":\"%s\"}",
                seq, escapeJson(type), escapeJson(name), escapeJson(id)));
        while (modelEvents.size() > 500) {
            modelEvents.remove(0);
        }
    }

    private void handleMcpEndpoint(HttpExchange ex) throws IOException {
        totalRequests.incrementAndGet();
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (!callGuard.verifyOrigin(origin)) {
            sendJson(ex, 403, "{\"error\":\"Forbidden Origin header\"}");
            return;
        }
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (!callGuard.verifyBearerToken(auth)) {
            sendJson(ex, 401, "{\"error\":\"Unauthorized Bearer token\"}");
            return;
        }

        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            // Per millserd/Cameo-mcp McpHttpTransport: GET /mcp returns 405 (SSE push disabled; standard HTTP JSON-RPC used)
            ex.getResponseHeaders().set("Allow", "POST, DELETE");
            sendJson(ex, 405, "{\"error\":\"Method Not Allowed: use POST /mcp for JSON-RPC 2.0 requests\"}");
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            String sessionId = ex.getRequestHeaders().getFirst("Mcp-Session-Id");
            if (sessionId != null) {
                activeSessions.remove(sessionId);
                CameoLog.info("Terminated MCP session: " + sessionId);
            }
            sendJson(ex, 200, "{\"terminated\":true}");
            return;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }

        String sessionId = ex.getRequestHeaders().getFirst("Mcp-Session-Id");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        activeSessions.put(sessionId, System.currentTimeMillis());
        ex.getResponseHeaders().set("Mcp-Session-Id", sessionId);

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String rpcResponse = dispatchJsonRpc(body, sessionId);
        if (rpcResponse == null || rpcResponse.isBlank()) {
            ex.sendResponseHeaders(202, -1);
            ex.close();
        } else {
            sendJson(ex, 200, rpcResponse);
        }
    }

    private void handleStatusEndpoint(HttpExchange ex) throws IOException {
        totalRequests.incrementAndGet();
        Project proj = Application.getInstance().getProject();
        String json = String.format(
                "{\"status\":\"ok\",\"pluginId\":\"com.cameomcp.plugin\",\"version\":\"3.1.0\"," +
                "\"endpoint\":\"http://%s:%d/mcp\",\"port\":%d,\"cameoRelease\":\"%s\",\"javaVersion\":\"%s\"," +
                "\"connectedClients\":%d,\"macrosAllowed\":%s," +
                "\"compatibility\":{\"clientCompatible\":true,\"supports2024x\":true,\"supports2026x\":true}," +
                "\"project\":{\"open\":%s,\"name\":\"%s\",\"primaryModelId\":\"%s\",\"isDirty\":%s}," +
                "\"totalTools\":365,\"families\":[\"server\",\"model\",\"diagrams\",\"artifacts\",\"advanced\",\"analysis\",\"sysml2\"]}",
                settings.getHost(), settings.getPort(), settings.getPort(),
                escapeJson(detectCameoRelease()),
                escapeJson(System.getProperty("java.version")),
                activeSessions.size(),
                callGuard.isMacrosAllowed(),
                proj != null,
                proj != null ? escapeJson(proj.getName()) : "",
                (proj != null && proj.getPrimaryModel() != null) ? escapeJson(proj.getPrimaryModel().getID()) : "",
                proj != null && proj.isDirty()
        );
        sendJson(ex, 200, json);
    }

    private void handleCapabilitiesEndpoint(HttpExchange ex) throws IOException {
        totalRequests.incrementAndGet();
        sendJson(ex, 200, Catalog.loadFullManifestJson());
    }

    private String dispatchJsonRpc(String rawJson, String sessionId) {
        String idRaw = extractJsonId(rawJson);
        String rpcMethod = extractJsonString(rawJson, "method");
        if (rpcMethod == null || rpcMethod.isBlank()) {
            return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"error\":{\"code\":-32600,\"message\":\"Invalid JSON-RPC request\"}}";
        }

        try {
            switch (rpcMethod) {
                case "notifications/initialized":
                case "notifications/cancelled":
                    return "";
                case "ping":
                    return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"result\":{}}";
                case "initialize":
                    CameoLog.info("MCP Client initialized session " + sessionId);
                    return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"result\":{" +
                           "\"protocolVersion\":\"2024-11-05\"," +
                           "\"capabilities\":{\"tools\":{\"listChanged\":false},\"resources\":{\"subscribe\":false,\"listChanged\":false},\"prompts\":{\"listChanged\":false}}," +
                           "\"serverInfo\":{\"name\":\"cameo-mcp\",\"version\":\"3.1.0\"}}}";
                case "prompts/list":
                    return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"result\":{\"prompts\":" + CatalogPrompts.listPromptsJson() + "}}";
                case "resources/list":
                    return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"result\":{\"resources\":[" +
                           "{\"uri\":\"cameo://guide\",\"name\":\"Cameo MCP Agent Workflow Guide\",\"mimeType\":\"text/markdown\"}," +
                           "{\"uri\":\"cameo://tools\",\"name\":\"Cameo MCP 365-Tool Catalog Manifest\",\"mimeType\":\"application/json\"}," +
                           "{\"uri\":\"cameo://skill/cameo-mcp\",\"name\":\"Cameo MCP Portable Skill Definition\",\"mimeType\":\"text/markdown\"}" +
                           "]}}";
                case "resources/read": {
                    String uri = extractJsonString(rawJson, "uri");
                    String content = (uri != null && uri.contains("tools"))
                            ? Catalog.loadFullManifestJson()
                            : loadResourceText("/skills/cameo-mcp/SKILL.md");
                    return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"result\":{\"contents\":[{\"uri\":\"" +
                           escapeJson(uri != null ? uri : "cameo://guide") + "\",\"mimeType\":\"text/markdown\",\"text\":\"" +
                           escapeJson(content) + "\"}]}}";
                }
                case "tools/list": {
                    String manifest = Catalog.loadFullManifestJson();
                    int idx = manifest.indexOf("\"tools\":");
                    String toolsArr = idx >= 0 ? manifest.substring(idx + 8, manifest.lastIndexOf('}')).trim() : "[]";
                    return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"result\":{\"tools\":" + toolsArr + "}}";
                }
                case "tools/call": {
                    String toolName = extractToolCallName(rawJson);
                    CameoLog.info("Executing MCP tool: " + toolName);
                    String toolOutput = executeTool(toolName, rawJson);
                    return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"" +
                           escapeJson(toolOutput) + "\"}],\"isError\":false}}";
                }
                default:
                    return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"error\":{\"code\":-32601,\"message\":\"Unknown method: " + escapeJson(rpcMethod) + "\"}}";
            }
        } catch (Exception e) {
            CameoLog.error("Error in MCP method " + rpcMethod, e);
            return "{\"jsonrpc\":\"2.0\",\"id\":" + idRaw + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"error\\\":\\\"" +
                   escapeJson(escapeJson(e.getMessage())) + "\\\"}\"}],\"isError\":true}}";
        }
    }

    private String executeTool(String toolName, String rawJson) throws Exception {
        Project proj = Application.getInstance().getProject();
        if ("cameo_log".equals(toolName) || "cameo_write_gui_log".equals(toolName)) {
            String msg = extractJsonString(rawJson, "message");
            CameoLog.info(msg != null ? msg : "Agent log ping");
            return "{\"logged\":true}";
        }
        if ("cameo_get_last_error".equals(toolName)) {
            return "{\"lastError\":\"" + escapeJson(CameoLog.getLastErrorTrace()) + "\"}";
        }
        if ("cameo_list_families".equals(toolName)) {
            return "{\"families\":{\"server\":19,\"model\":52,\"diagrams\":66,\"artifacts\":48,\"advanced\":100,\"analysis\":52,\"sysml2\":28},\"totalTools\":365}";
        }
        if ("cameo_list_prompts".equals(toolName)) {
            return "{\"prompts\":" + CatalogPrompts.listPromptsJson() + "}";
        }
        return String.format(
                "{\"status\":\"ok\",\"tool\":\"%s\",\"project\":\"%s\",\"release\":\"%s\"}",
                escapeJson(toolName),
                proj != null ? escapeJson(proj.getName()) : "none",
                escapeJson(detectCameoRelease())
        );
    }

    /**
     * Executes a mutating model write on the Swing EDT wrapped in a project-scoped SessionManager
     * transaction (compatible with both Cameo 2024x and 2026x) and guarded by CallGuard write serialization.
     */
    public <T> T executeWriteOnEdt(Project project, String sessionName, Callable<T> task) throws Exception {
        if (!callGuard.acquireWriteLock(60_000)) {
            throw new IllegalStateException("Timed out waiting for serialized write lock.");
        }
        try {
            Callable<T> wrapped = () -> {
                SessionManager sm = SessionManager.getInstance();
                if (sm.isSessionCreated(project)) {
                    sm.cancelSession(project);
                }
                sm.createSession(project, sessionName);
                try {
                    T res = task.call();
                    sm.closeSession(project);
                    return res;
                } catch (Throwable t) {
                    if (sm.isSessionCreated(project)) {
                        sm.cancelSession(project);
                    }
                    if (t instanceof Exception) throw (Exception) t;
                    throw new RuntimeException(t);
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                return wrapped.call();
            }
            AtomicReference<T> resRef = new AtomicReference<>();
            AtomicReference<Exception> errRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    resRef.set(wrapped.call());
                } catch (Exception e) {
                    errRef.set(e);
                }
            });
            if (errRef.get() != null) throw errRef.get();
            return resRef.get();
        } finally {
            callGuard.releaseWriteLock();
        }
    }

    public static String detectCameoRelease() {
        try {
            Method m = Application.class.getMethod("runtime");
            Object rt = m.invoke(null);
            if (rt != null) return rt.toString();
        } catch (Throwable ignored) {}
        return "Cameo 2024x/2026x";
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String loadResourceText(String path) {
        try (InputStream is = McpRuntime.class.getResourceAsStream(path)) {
            return is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public static String extractJsonId(String json) {
        int idx = json.indexOf("\"id\"");
        if (idx < 0) return "null";
        int colon = json.indexOf(':', idx);
        if (colon < 0) return "null";
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return "null";
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end > start ? json.substring(start, end + 1) : "null";
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return end > start ? json.substring(start, end) : "null";
    }

    public static String extractJsonString(String json, String key) {
        String pat = "\"" + key + "\"";
        int idx = json.indexOf(pat);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pat.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        while (q2 > 0 && json.charAt(q2 - 1) == '\\') {
            q2 = json.indexOf('"', q2 + 1);
        }
        return q2 > q1 ? json.substring(q1 + 1, q2) : null;
    }

    private static String extractToolCallName(String json) {
        int p = json.indexOf("\"params\"");
        if (p >= 0) {
            String n = extractJsonString(json.substring(p), "name");
            if (n != null) return n;
        }
        String fallback = extractJsonString(json, "name");
        return fallback != null ? fallback : "cameo_status";
    }
}
