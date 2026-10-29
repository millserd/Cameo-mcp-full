package com.cameomcp.mcp;

import org.junit.Test;

import static org.junit.Assert.*;

public class CallGuardTest {

    @Test
    public void allowsOnlyExactLoopbackOrigins() {
        CallGuard g = new CallGuard("", false);
        assertTrue(g.verifyOrigin(null));
        assertTrue(g.verifyOrigin(""));
        assertTrue(g.verifyOrigin("http://localhost:3000"));
        assertTrue(g.verifyOrigin("http://127.0.0.1:18740"));
        assertTrue(g.verifyOrigin("http://[::1]:8080"));
        assertFalse(g.verifyOrigin("http://localhost.attacker.com"));
        assertFalse(g.verifyOrigin("http://127.0.0.1.evil.net"));
        assertFalse(g.verifyOrigin("http://localhostevil.com"));
        assertFalse(g.verifyOrigin("https://evil.com"));
        assertFalse(g.verifyOrigin("null"));
        assertFalse(g.verifyOrigin("file://localhost/x"));
    }

    @Test
    public void bearerToken() {
        assertTrue(new CallGuard("", false).verifyBearerToken(null));
        CallGuard g = new CallGuard("s3cret", false);
        assertTrue(g.verifyBearerToken("Bearer s3cret"));
        assertTrue(g.verifyBearerToken("bearer s3cret"));
        assertFalse(g.verifyBearerToken("Bearer s3cre"));
        assertFalse(g.verifyBearerToken("s3cret"));
        assertFalse(g.verifyBearerToken(null));
    }
}
