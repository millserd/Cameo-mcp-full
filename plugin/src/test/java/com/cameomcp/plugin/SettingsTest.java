package com.cameomcp.plugin;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/** Settings must fail closed: never listen beyond loopback, never start without a token the user configured. */
public class SettingsTest {

    private String home;

    @Before
    public void tempHome() throws Exception {
        home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("mcp-settings").toString());
    }

    @After
    public void restoreHome() {
        System.setProperty("user.home", home);
    }

    private static void write(String text) throws Exception {
        File f = Settings.getSettingsFile();
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), text);
    }

    @Test
    public void nonLoopbackHostIsIgnored() throws Exception {
        write("host=0.0.0.0\nbearerToken=secret\n");
        Settings s = Settings.load();
        assertEquals("127.0.0.1", s.getHost());
        assertEquals("secret", s.getBearerToken());
        assertNull(s.getLoadError());
    }

    @Test
    public void unreadableFileKeepsTheServerOff() throws Exception {
        write("bearerToken=abc\\u12\n"); // malformed \\u escape: Properties.load throws
        Settings s = Settings.load();
        assertNotNull(s.getLoadError());
        assertFalse(s.isAllowMacros());
        assertFalse("must not start without the token the file may hold", new McpRuntime(s).start());
        assertTrue(s.save());
        assertNull("saving a fresh file clears the error", s.getLoadError());
    }
}
