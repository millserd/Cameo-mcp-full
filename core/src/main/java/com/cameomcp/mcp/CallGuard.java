package com.cameomcp.mcp;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Serializes mutating writes, checks browser Origin headers to block cross-site local attacks,
 * and validates optional Bearer tokens (aligned with millserd/Cameo-mcp CallGuard).
 */
public final class CallGuard {

    private final Semaphore writeLock = new Semaphore(1, true);
    private volatile String bearerToken;
    private volatile boolean allowMacros = true;

    public CallGuard(String bearerToken, boolean allowMacros) {
        this.bearerToken = bearerToken;
        this.allowMacros = allowMacros;
    }

    public boolean verifyOrigin(String originHeader) {
        if (originHeader == null || originHeader.isBlank()) {
            return true; // Non-browser MCP stdio/HTTP client
        }
        return originHeader.startsWith("http://127.0.0.1") || originHeader.startsWith("http://localhost");
    }

    public boolean verifyBearerToken(String authHeader) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return true; // Token auth disabled
        }
        return ("Bearer " + bearerToken).equals(authHeader);
    }

    public boolean isMacrosAllowed() {
        return allowMacros;
    }

    public void setAllowMacros(boolean allowMacros) {
        this.allowMacros = allowMacros;
    }

    public void setBearerToken(String token) {
        this.bearerToken = token;
    }

    public boolean acquireWriteLock(long timeoutMs) throws InterruptedException {
        return writeLock.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void releaseWriteLock() {
        writeLock.release();
    }
}
