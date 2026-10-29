package com.cameomcp.plugin.tools;

import com.cameomcp.mcp.CallGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;

import javax.swing.SwingUtilities;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Tool table and the single place where tools touch Cameo: the Swing thread, inside a session for writes. */
public final class Tools {

    /** A tool result that is an image (sent as MCP image content). */
    public record Image(String base64, String mimeType, JsonNode meta) {}

    static final int DEFAULT_TIMEOUT_S = 120;
    /** Upper bound so a few slow calls cannot hold all of the server's threads for long (the stdio bridge waits a bit longer). */
    static final int MAX_TIMEOUT_S = 600;

    private Tools() {}

    public static Map<String, Tool> all() {
        Map<String, Tool> m = new LinkedHashMap<>();
        ServerTools.register(m);
        ModelTools.register(m);
        DiagramTools.register(m);
        ArtifactTools.register(m);
        AdvancedTools.register(m);
        AnalysisTools.register(m);
        StudyTools.register(m);
        MethodTools.register(m);
        ExtraTools.register(m);
        try {
            Class.forName("com.dassault_systemes.modeler.kerml.model.kerml.Namespace", false, Tools.class.getClassLoader());
            Sysml2Tools.register(m);
            v2Available = true;
        } catch (ClassNotFoundException | LinkageError e) {
            v2Available = false; // this Cameo has no SysML v2 support; the sysml2 tools are not offered
        }
        return Collections.unmodifiableMap(m);
    }

    static void add(Map<String, Tool> m, String name, Tool.Mode mode, Tool.Handler h) {
        if (m.put(name, new Tool(name, mode, h)) != null) throw new IllegalStateException("Duplicate tool " + name);
    }

    /** True when Cameo's SysML v2 classes are loadable (set by all()). */
    static volatile boolean v2Available;

    static CallGuard guard; // set per call; read by execute_macro

    public static Object execute(Tool tool, JsonNode args, CallGuard callGuard) throws Exception {
        guard = callGuard;
        int timeout = args.path("timeoutSeconds").asInt(DEFAULT_TIMEOUT_S);
        if (timeout < 1 || timeout > MAX_TIMEOUT_S) {
            throw new ToolError("timeoutSeconds must be 1-" + MAX_TIMEOUT_S + " (default " + DEFAULT_TIMEOUT_S + ")");
        }
        FutureTask<Object> task = new FutureTask<>(() -> run(tool, args));
        if (tool.mode() == Tool.Mode.BACKGROUND) {
            // Blocking calls (simulation) run on their own thread so Cameo's UI thread stays free.
            Thread th = new Thread(task, "cameo-mcp " + tool.name());
            th.setDaemon(true);
            th.start();
        } else if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
        try {
            return task.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new ToolError(tool.name() + " did not finish within " + timeout + " s. Cameo may be showing a dialog "
                    + "or running a long operation; check the Cameo window. The call keeps running in Cameo.");
        }
    }

    private static Object run(Tool tool, JsonNode args) throws Exception {
        Project p = Application.getInstance().getProject();
        Ctx c = new Ctx(args, p);
        if (tool.mode() != Tool.Mode.WRITE) return tool.handler().run(c);
        return inSession(c.project(), "MCP: " + tool.name(), () -> tool.handler().run(c));
    }

    interface Body<T> { T run() throws Exception; }

    /** Run body in one undoable editing session; refuses if a session is already open. */
    static <T> T inSession(Project project, String name, Body<T> body) throws Exception {
        SessionManager sm = SessionManager.getInstance();
        if (sm.isSessionCreated(project)) {
            // Never cancel it: it is usually the user's own unfinished edit.
            throw new ToolError("Cameo already has an open editing session (the user is editing, or an earlier "
                    + "operation got stuck). Finish the edit in Cameo, or call cameo_reset_session if nothing is in progress.");
        }
        sm.createSession(project, name);
        try {
            T r = body.run();
            sm.closeSession(project);
            return r;
        } catch (Throwable t) {
            if (sm.isSessionCreated(project)) sm.cancelSession(project);
            throw t;
        }
    }

    /** Run body in a session and always roll it back (for what-if evaluation). */
    static <T> T inRolledBackSession(Project project, String name, Body<T> body) throws Exception {
        SessionManager sm = SessionManager.getInstance();
        if (sm.isSessionCreated(project)) throw new ToolError("Cameo already has an open editing session; finish it first.");
        sm.createSession(project, name);
        try {
            return body.run();
        } finally {
            if (sm.isSessionCreated(project)) sm.cancelSession(project);
        }
    }
}
