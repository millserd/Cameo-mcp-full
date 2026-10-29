package com.cameomcp.stdio;

import com.cameomcp.catalog.Catalog;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StdioLauncherTest {

    @Test
    public void errorRepliesGetTheRequestId() throws Exception {
        String reply = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32000,\"message\":\"Missing or wrong Bearer token\"}}";
        String fixed = StdioLauncher.withId(reply, Catalog.JSON.readTree("7"));
        assertEquals(7, Catalog.JSON.readTree(fixed).path("id").asInt());
    }

    @Test
    public void normalRepliesAreUntouched() throws Exception {
        String reply = "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{}}";
        assertEquals(reply, StdioLauncher.withId(reply, Catalog.JSON.readTree("3")));
    }
}
