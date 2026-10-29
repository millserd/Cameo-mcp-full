package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.cameomcp.plugin.CameoLog;
import com.cameomcp.plugin.McpRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.actions.ActionsManager;
import com.nomagic.actions.NMAction;
import com.nomagic.magicdraw.actions.ActionsProvider;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.plugins.Plugin;
import com.nomagic.magicdraw.plugins.PluginUtils;
import com.nomagic.magicdraw.ui.browser.Node;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;

import javax.jmi.reflect.RefObject;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.cameomcp.plugin.tools.Tool.Mode.*;

final class ServerTools {

    private ServerTools() {}

    static void register(Map<String, Tool> m) {
        Tools.add(m, "cameo_status", READ, c -> {
            ObjectNode o = Catalog.JSON.createObjectNode()
                    .put("plugin", "cameo-mcp " + Catalog.version()).put("cameo", McpRuntime.cameoRelease())
                    .put("macrosAllowed", Tools.guard != null && Tools.guard.isMacrosAllowed());
            Project p = c.projectOrNull();
            ObjectNode proj = o.putObject("project").put("open", p != null);
            if (p != null) {
                proj.put("name", p.getName()).put("dirty", p.isDirty()).put("remote", p.isRemote())
                        .put("kind", p.getPrimaryModel() == null ? "sysml2" : "uml")
                        .put("editingSessionOpen", SessionManager.getInstance().isSessionCreated(p));
            }
            o.put("openProjects", Application.getInstance().getProjectsManager().getProjects().size());
            o.put("sysml2Support", Tools.v2Available).put("communityMode", ModelTools.communityMode());
            return o;
        });

        Tools.add(m, "cameo_get_capabilities", READ, c -> {
            ObjectNode o = Catalog.JSON.createObjectNode().put("cameo", McpRuntime.cameoRelease());
            ObjectNode fam = o.putObject("families");
            for (JsonNode t : Catalog.tools()) {
                String f = t.path("family").asText();
                fam.put(f, fam.path(f).asInt() + 1);
            }
            ArrayNode plugins = o.putArray("cameoPlugins");
            for (Plugin pl : PluginUtils.getPlugins()) {
                plugins.addObject().put("id", pl.getDescriptor().getID()).put("name", pl.getDescriptor().getName())
                        .put("version", pl.getDescriptor().getVersion());
            }
            return o;
        });

        Tools.add(m, "cameo_list_tools", READ, c -> {
            String family = c.opt("family");
            String q = c.str("query", "").toLowerCase(Locale.ROOT);
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (JsonNode t : Catalog.tools()) {
                if (family != null && !family.equals(t.path("family").asText())) continue;
                String text = (t.path("name").asText() + " " + t.path("description").asText()).toLowerCase(Locale.ROOT);
                if (!q.isEmpty() && !text.contains(q)) continue;
                a.addObject().put("name", t.path("name").asText()).put("family", t.path("family").asText())
                        .put("description", t.path("description").asText());
            }
            return Catalog.JSON.createObjectNode().put("count", a.size()).set("tools", a);
        });

        Tools.add(m, "cameo_get_last_error", READ, c ->
                Catalog.JSON.createObjectNode().put("lastError", CameoLog.getLastErrorTrace()));

        Tools.add(m, "cameo_get_system_logs", READ, c -> {
            int n = Math.max(1, c.i("lines", 100));
            ArrayNode a = Catalog.JSON.createArrayNode();
            var logs = CameoLog.getRecentLogs().toArray(new String[0]);
            for (int i = Math.max(0, logs.length - n); i < logs.length; i++) a.add(logs[i]);
            return Catalog.JSON.createObjectNode().set("lines", a);
        });

        Tools.add(m, "cameo_log", READ, c -> {
            String level = c.str("level", "info").toLowerCase(Locale.ROOT);
            String msg = c.str("message");
            if (level.startsWith("warn")) CameoLog.warn(msg);
            else if (level.startsWith("err")) CameoLog.error(msg, null);
            else CameoLog.info(msg);
            return Catalog.JSON.createObjectNode().put("logged", true);
        });

        Tools.add(m, "cameo_reset_session", APP, c -> {
            Project p = c.project();
            SessionManager sm = SessionManager.getInstance();
            boolean open = sm.isSessionCreated(p);
            if (open) sm.cancelSession(p);
            return Catalog.JSON.createObjectNode().put("cancelledSession", open);
        });

        Tools.add(m, "cameo_undo", APP, c -> history(c, true));
        Tools.add(m, "cameo_redo", APP, c -> history(c, false));

        Tools.add(m, "cameo_get_command_history", READ, c -> {
            var h = c.project().getCommandHistory();
            ArrayNode a = Catalog.JSON.createArrayNode();
            var cmds = h.getCommands();
            int n = c.i("limit", 50);
            for (int i = Math.max(0, cmds.size() - n); i < cmds.size(); i++) a.add(String.valueOf(cmds.get(i).getName()));
            ObjectNode o = Catalog.JSON.createObjectNode();
            o.set("commands", a);
            o.put("nextUndo", h.getCommandForUndo() == null ? null : h.getCommandForUndo().getName());
            o.put("nextRedo", h.getCommandForRedo() == null ? null : h.getCommandForRedo().getName());
            return o;
        });

        Tools.add(m, "cameo_clear_command_history", APP, c -> {
            c.project().getCommandHistory().clearHistory();
            return Catalog.JSON.createObjectNode().put("cleared", true);
        });

        Tools.add(m, "cameo_get_ui_state", READ, c -> {
            Project p = c.project();
            ObjectNode o = Catalog.JSON.createObjectNode();
            DiagramPresentationElement d = p.getActiveDiagram();
            if (d != null) {
                o.set("activeDiagram", Model.ref(d.getDiagram()));
                ArrayNode sel = o.putArray("selectedSymbols");
                DiagramTools.forEachPe(d, pe -> {
                    if (pe.isSelected()) {
                        ObjectNode s = sel.addObject().put("presentationId", pe.getID()).put("type", pe.getHumanType());
                        if (pe.getElement() != null) s.set("element", Model.ref(pe.getElement()));
                    }
                });
            }
            ArrayNode tree = o.putArray("containmentTreeSelection");
            Node[] nodes = p.getBrowser().getContainmentTree().getSelectedNodes();
            if (nodes != null) for (Node n : nodes) if (n.getUserObject() instanceof Element e) tree.add(Model.ref(e));
            return o;
        });

        Tools.add(m, "cameo_select_in_containment_tree", APP, c -> {
            Element e = c.el("elementId");
            c.project().getBrowser().getContainmentTree().openNode(e, true);
            return Catalog.JSON.createObjectNode().put("selected", e.getID());
        });

        Tools.add(m, "cameo_list_ui_actions", READ, c -> {
            String q = c.str("query", "").toLowerCase(Locale.ROOT);
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (NMAction act : mainMenu().getAllActions()) {
                String id = act.getID();
                String name = act.getName();
                if (id == null) continue;
                if (!q.isEmpty() && !(id + " " + name).toLowerCase(Locale.ROOT).contains(q)) continue;
                a.addObject().put("id", id).put("name", name);
                if (a.size() >= c.i("limit", 200)) break;
            }
            return Catalog.JSON.createObjectNode().set("actions", a);
        });

        Tools.add(m, "cameo_invoke_ui_action", APP, c -> {
            String id = c.str("actionId");
            NMAction act = mainMenu().getActionFor(id);
            if (act == null) throw new ToolError("No main-menu action with id '" + id + "' (see cameo_list_ui_actions)");
            act.updateState();
            if (!act.isEnabled()) throw new ToolError("Action '" + id + "' is disabled right now");
            act.actionPerformed(new ActionEvent(Application.getInstance().getMainFrame(), ActionEvent.ACTION_PERFORMED, id));
            return Catalog.JSON.createObjectNode().put("invoked", id);
        });

        Tools.add(m, "cameo_poll_model_change_events", READ, c -> {
            Project p = c.project();
            ChangeLog log = CHANGES.computeIfAbsent(p, ChangeLog::new);
            long since = c.node("sinceSeq").asLong(0);
            ArrayNode a = Catalog.JSON.createArrayNode();
            synchronized (log) {
                for (ObjectNode e : log.events) if (e.path("seq").asLong() > since) a.add(e);
            }
            ObjectNode o = Catalog.JSON.createObjectNode().put("lastSeq", log.seq.get()).put("listeningSince", log.started);
            o.set("events", a);
            return o;
        });
    }

    private static ActionsManager mainMenu() {
        return ActionsProvider.getInstance().getMainMenuActions();
    }

    private static Object history(Ctx c, boolean undo) {
        var h = c.project().getCommandHistory();
        var cmd = undo ? h.getCommandForUndo() : h.getCommandForRedo();
        if (cmd == null) throw new ToolError("Nothing to " + (undo ? "undo" : "redo"));
        String name = cmd.getName();
        if (undo) h.undo(); else h.redo();
        return Catalog.JSON.createObjectNode().put(undo ? "undone" : "redone", name);
    }

    // Model change events: one listener per project, started on the first poll.
    private static final Map<Project, ChangeLog> CHANGES = new WeakHashMap<>();

    private static final class ChangeLog {
        final Deque<ObjectNode> events = new ArrayDeque<>();
        final AtomicLong seq = new AtomicLong();
        final String started = java.time.Instant.now().toString();

        ChangeLog(Project p) {
            PropertyChangeListener l = ev -> {
                if (!(ev.getSource() instanceof Element e)) return;
                ObjectNode o = Catalog.JSON.createObjectNode().put("seq", seq.incrementAndGet())
                        .put("property", ev.getPropertyName()).put("elementId", e.getID()).put("type", e.getHumanType());
                String n = Model.name(e);
                if (n != null) o.put("name", n);
                synchronized (this) {
                    events.addLast(o);
                    if (events.size() > 1000) events.removeFirst();
                }
            };
            p.getRepositoryListenerRegistry().addPropertyChangeListener(l, (RefObject) null);
        }
    }
}
