package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.dassault_systemes.modeler.kerml.model.kerml.Element;
import com.dassault_systemes.modeler.kerml.model.kerml.Namespace;
import com.dassault_systemes.modeler.kerml.model.kerml.Relationship;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.core.Project;

import java.util.ArrayList;
import java.util.List;

/** SysML v2 side of MethodTools; kept separate so MethodTools loads on Cameo editions without SysML v2. */
final class V2Items {

    private V2Items() {}

    static List<MethodTools.Item> items(Project p, String scopeId) {
        List<MethodTools.Item> out = new ArrayList<>();
        for (Element root : V2.scope(p, scopeId)) {
            V2.walkMembers(root, e -> {
                if (e.getDeclaredName() == null || e.getOwner() == null) return;
                String k = V2.kind(e);
                if (k.startsWith("Literal") || k.endsWith("Expression") || k.equals("MultiplicityRange") || k.equals("Documentation")) return;
                out.add(new MethodTools.Item(V2.id(e), e.getDeclaredName(), k, V2.keyword(e), V2.documentation(e),
                        e.getQualifiedName(), k.endsWith("Definition")));
            });
        }
        return out;
    }

    static ObjectNode applyPack(Project p, String ownerId, String rootName, MethodTools.Pack pack) throws Exception {
        Element owner = ownerId != null ? V2.find(p, ownerId) : V2.roots(p).get(0);
        StringBuilder sb = new StringBuilder("package ").append(V2.name(rootName)).append(" {\n");
        sb.append("    doc /* ").append(pack.description()).append(" */\n");
        List<String> open = new ArrayList<>();
        for (MethodTools.Section s : pack.sections()) {
            String[] path = s.name().split("::");
            while (!open.isEmpty() && (open.size() >= path.length || !open.get(open.size() - 1).equals(path[open.size() - 1]))) {
                sb.append("    ".repeat(open.size())).append("}\n");
                open.remove(open.size() - 1);
            }
            String ind = "    ".repeat(open.size() + 1);
            sb.append(ind).append("package ").append(V2.name(path[path.length - 1])).append(" {\n");
            sb.append(ind).append("    doc /* ").append(s.purpose()).append(" */\n");
            open.add(path[path.length - 1]);
        }
        while (!open.isEmpty()) { sb.append("    ".repeat(open.size())).append("}\n"); open.remove(open.size() - 1); }
        sb.append("}");
        V2.Added a = V2.addText(p, owner, sb.toString(), false);
        ObjectNode o = Catalog.JSON.createObjectNode();
        if (!a.elements().isEmpty()) o.set("root", V2.ref(a.elements().get(0)));
        ArrayNode pk = o.putArray("packages");
        if (!a.elements().isEmpty()) V2.walkMembers(a.elements().get(0), e -> { if (V2.kind(e).equals("Package")) pk.add(V2.ref(e)); });
        return o.put("text", sb.toString());
    }

    static List<MethodTools.Item> sectionItems(Project p, String rootId, String packTitle, String path) {
        Element root = rootId != null ? V2.find(p, rootId) : null;
        if (root == null) {
            for (Namespace r : V2.roots(p)) {
                Element f = findNamed(r, packTitle, 3);
                if (f != null) { root = f; break; }
            }
        }
        if (root == null) return null;
        Element cur = root;
        for (String seg : path.split("::")) {
            Element next = null;
            if (cur instanceof Namespace ns) for (Element e : ns.getOwnedMember()) if (seg.equals(e.getDeclaredName())) next = e;
            if (next == null) return null;
            cur = next;
        }
        String id = V2.id(cur);
        List<MethodTools.Item> out = items(p, id);
        out.removeIf(i -> i.id().equals(id));
        return out;
    }

    private static Element findNamed(Element e, String name, int depth) {
        if (name.equals(e.getDeclaredName())) return e;
        if (depth == 0 || !(e instanceof Namespace ns)) return null;
        for (Element m : ns.getOwnedMember()) {
            if (!V2.isStructural(m)) continue;
            Element f = findNamed(m, name, depth - 1);
            if (f != null) return f;
        }
        return null;
    }

    static String set(Project p, String id, String field, String value, boolean dry) {
        Element e = V2.find(p, id);
        String before = field.equals("name") ? e.getDeclaredName() : V2.documentation(e);
        if (!dry) {
            if (field.equals("name")) e.setDeclaredName(value);
            else V2.setDocumentation(e, value);
        }
        return before;
    }

    static ObjectNode validate(Project p, String scopeId) { return Sysml2Tools.validate(p, V2.scope(p, scopeId), 300); }

    static ObjectNode evaluate(Project p, String scopeId) {
        ObjectNode all = Catalog.JSON.createObjectNode();
        ArrayNode attrs = all.putArray("attributes"), cons = all.putArray("constraints");
        int pass = 0, fail = 0, unk = 0;
        for (Element root : V2.scope(p, scopeId)) {
            ObjectNode r = V2.evaluateAll(root);
            attrs.addAll((ArrayNode) r.get("attributes"));
            cons.addAll((ArrayNode) r.get("constraints"));
            pass += r.path("summary").path("constraintsPassed").asInt();
            fail += r.path("summary").path("constraintsFailed").asInt();
            unk += r.path("summary").path("constraintsNotEvaluable").asInt();
        }
        all.putObject("summary").put("constraintsPassed", pass).put("constraintsFailed", fail).put("constraintsNotEvaluable", unk);
        return all;
    }

    /** SysML text of the scope plus complete/gaps, as JSON ("text" holds the text). */
    static ObjectNode export(Project p, String scopeId) {
        V2.Export ex = V2.export(V2.scope(p, scopeId));
        return Sysml2Tools.exportJson(ex).put("text", ex.text());
    }
}
