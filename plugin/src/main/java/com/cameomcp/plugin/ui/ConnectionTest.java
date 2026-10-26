package com.cameomcp.plugin.ui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Diagnostic utility verifying local HTTP connectivity to /status and /mcp
 * (aligned with millserd/Cameo-mcp ConnectionTest.java).
 */
public final class ConnectionTest {

    private ConnectionTest() {}

    public static String runSelfTest(String host, int port) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            URI uri = URI.create("http://" + host + ":" + port + "/status");
            HttpRequest req = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return String.format("Connection Self-Test PASSED (HTTP %d on %s:%d): %s",
                    resp.statusCode(), host, port, resp.body());
        } catch (Exception e) {
            return String.format("Connection Self-Test FAILED on %s:%d — %s", host, port, e.getMessage());
        }
    }
}
