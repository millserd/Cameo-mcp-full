package com.cameomcp.plugin;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import static org.junit.Assert.*;

/** The real HTTP server, without Cameo: transport rules, JSON-RPC handling, restarts. */
public class McpRuntimeTest {

    static McpRuntime rt;
    static int port;

    @BeforeClass
    public static void start() throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("mcp-home").toString());
        Settings s = Settings.load();
        try (var sock = new java.net.ServerSocket(0)) { port = sock.getLocalPort(); }
        s.setPort(port);
        rt = new McpRuntime(s);
        assertTrue(rt.start());
    }

    @AfterClass
    public static void stop() { rt.stop(); }

    static HttpResponse<String> post(String body, String contentType, String origin) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) b.header("Origin", origin);
        // fresh client: a pooled connection would point at a server that a restart test closed
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode rpc(String method, String params) throws Exception {
        HttpResponse<String> r = post("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"" + method + "\",\"params\":" + params + "}", "application/json", null);
        assertEquals(r.body(), 200, r.statusCode());
        JsonNode j = Catalog.JSON.readTree(r.body());
        assertEquals(7, j.path("id").asInt());
        return j;
    }

    @Test
    public void macrosAreOffByDefault() {
        assertFalse(rt.getCallGuard().isMacrosAllowed());
    }

    @Test
    public void rejectsForeignOriginsAndNonJson() throws Exception {
        assertEquals(403, post("{}", "application/json", "http://localhost.attacker.com").statusCode());
        assertEquals(415, post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}", "text/plain", null).statusCode());
        assertEquals(200, post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}", "application/json", "http://127.0.0.1:" + port).statusCode());
    }

    @Test
    public void protocolEdgeCases() throws Exception {
        assertEquals(202, post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/whatever\"}", "application/json", null).statusCode());
        HttpResponse<String> batch = post("[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]", "application/json", null);
        assertEquals(400, batch.statusCode());
        HttpResponse<String> bad = post("not json", "application/json", null);
        assertEquals(-32700, Catalog.JSON.readTree(bad.body()).path("error").path("code").asInt());
        HttpResponse<String> big = post("[" + "1,".repeat(6_000_000) + "1]", "application/json", null);
        assertEquals(413, big.statusCode());
    }

    @Test
    public void argumentsBeforeNameAndNestedIdsAreReadCorrectly() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"arguments\":{\"name\":\"Pump\",\"x\":{\"id\":\"inner\"}},\"name\":\"no_such_tool\"},\"id\":42}";
        JsonNode j = Catalog.JSON.readTree(post(body, "application/json", null).body());
        assertEquals(42, j.path("id").asInt());
        assertTrue(j.path("error").path("message").asText().contains("no_such_tool"));
    }

    @Test
    public void handlerErrorsStillGetAReply() throws Exception {
        // /status calls into Cameo, which is absent here: the client must get a 500, not a dropped connection
        HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/status")).build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(r.statusCode() == 200 || r.statusCode() == 500);
        assertTrue(r.body().startsWith("{"));
    }

    @Test
    public void methods() throws Exception {
        JsonNode init = rpc("initialize", "{\"protocolVersion\":\"2025-03-26\"}");
        assertEquals("2025-03-26", init.path("result").path("protocolVersion").asText());
        assertEquals(McpRuntime.PROTOCOL_VERSIONS.get(0), rpc("initialize", "{\"protocolVersion\":\"1999\"}").path("result").path("protocolVersion").asText());
        JsonNode tools = rpc("tools/list", "{}").path("result").path("tools");
        assertEquals(Catalog.tools().size(), tools.size());
        assertEquals(rt.getTools().size(), tools.size());
        assertEquals(-32601, rpc("nope", "{}").path("error").path("code").asInt());
        assertEquals(-32602, rpc("tools/call", "{\"name\":\"cameo_nope\"}").path("error").path("code").asInt());
        assertTrue(rpc("prompts/get", "{\"name\":\"trace_requirements\"}").path("result").path("messages").size() > 0);
        assertEquals(-32002, rpc("resources/read", "{\"uri\":\"cameo://nope\"}").path("error").path("code").asInt());
        assertEquals("application/json", rpc("resources/read", "{\"uri\":\"cameo://tools\"}").path("result").path("contents").get(0).path("mimeType").asText());
    }

    @Test
    public void restartsDoNotLeakThreads() throws Exception {
        for (int i = 0; i < 5; i++) assertTrue(rt.start());
        rpc("ping", "{}");
        long threads = Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("Cameo-MCP-HTTP")).count();
        assertTrue("threads=" + threads, threads <= 8);
    }
}
