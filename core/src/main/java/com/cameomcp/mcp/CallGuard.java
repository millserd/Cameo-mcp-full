package com.cameomcp.mcp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/** Request gate: browser Origin check, optional Bearer token, macro switch. */
public final class CallGuard {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private volatile String bearerToken;
    private volatile boolean allowMacros;

    public CallGuard(String bearerToken, boolean allowMacros) {
        this.bearerToken = bearerToken;
        this.allowMacros = allowMacros;
    }

    /** No Origin = non-browser client. Otherwise the origin host must be exactly a loopback name. */
    public boolean verifyOrigin(String origin) {
        if (origin == null || origin.isBlank()) return true;
        try {
            URI u = URI.create(origin.trim());
            String scheme = u.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme)) && u.getHost() != null
                    && LOCAL_HOSTS.contains(u.getHost().toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean verifyBearerToken(String authHeader) {
        String token = bearerToken;
        if (token == null || token.isBlank()) return true;
        if (authHeader == null || authHeader.length() < 7 || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                authHeader.substring(7).trim().getBytes(StandardCharsets.UTF_8));
    }

    public boolean isMacrosAllowed() { return allowMacros; }
    public void setAllowMacros(boolean allowMacros) { this.allowMacros = allowMacros; }
    public void setBearerToken(String token) { this.bearerToken = token; }
}
