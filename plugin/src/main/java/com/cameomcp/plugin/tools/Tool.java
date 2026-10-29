package com.cameomcp.plugin.tools;

/**
 * One MCP tool. READ runs on the Swing thread with no editing session; WRITE runs inside one
 * SessionManager session (one undoable step); APP runs on the Swing thread outside any session
 * (project open/save/close, undo/redo, UI actions — Cameo refuses these inside a session).
 */
public record Tool(String name, Mode mode, Handler handler) {

    public enum Mode { READ, WRITE, APP }

    @FunctionalInterface
    public interface Handler {
        Object run(Ctx c) throws Exception;
    }
}
