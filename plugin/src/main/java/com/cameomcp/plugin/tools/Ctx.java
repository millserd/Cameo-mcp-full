package com.cameomcp.plugin.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.uml.BaseElement;
import com.nomagic.magicdraw.uml.Finder;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;

import java.util.ArrayList;
import java.util.List;

/** Arguments of one tool call plus the active project, with typed accessors that fail with clear messages. */
public final class Ctx {

    public final JsonNode args;
    private final Project project;

    Ctx(JsonNode args, Project project) {
        this.args = args;
        this.project = project;
    }

    public Project project() {
        if (project == null) throw new ToolError("No project is open in Cameo. Open one, or call cameo_create_new_project / cameo_open_project_file.");
        return project;
    }

    public Project projectOrNull() { return project; }

    public boolean has(String key) {
        JsonNode n = args.get(key);
        return n != null && !n.isNull();
    }

    public JsonNode node(String key) { return args.path(key); }

    public String str(String key) {
        String s = opt(key);
        if (s == null || s.isEmpty()) throw new ToolError("Missing required argument '" + key + "'");
        return s;
    }

    public String opt(String key) {
        JsonNode n = args.get(key);
        return n == null || n.isNull() ? null : n.asText();
    }

    public String str(String key, String def) {
        String s = opt(key);
        return s == null ? def : s;
    }

    public int i(String key, int def) { return has(key) ? args.get(key).asInt(def) : def; }

    public boolean b(String key, boolean def) { return has(key) ? args.get(key).asBoolean(def) : def; }

    public List<String> strs(String key) {
        List<String> out = new ArrayList<>();
        JsonNode n = args.get(key);
        if (n == null || n.isNull()) return out;
        if (n.isArray()) n.forEach(x -> out.add(x.asText()));
        else out.add(n.asText());
        return out;
    }

    /** Required element argument (id or qualified name). */
    public Element el(String key) { return find(str(key)); }

    public Element elOpt(String key) {
        String ref = opt(key);
        return ref == null || ref.isEmpty() ? null : find(ref);
    }

    public List<Element> els(String key) {
        List<Element> out = new ArrayList<>();
        for (String ref : strs(key)) out.add(find(ref));
        return out;
    }

    /** Element by id, or by qualified name ("Model::Pkg::Block"). */
    public Element find(String ref) {
        Project p = project();
        BaseElement b = p.getElementByID(ref);
        if (b instanceof Element e) return e;
        if (ref.contains("::")) {
            Element e = Finder.byQualifiedName().find(p, ref);
            if (e != null) return e;
        }
        throw new ToolError("No element with id or qualified name '" + ref + "'");
    }
}
