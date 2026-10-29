package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.dassault_systemes.modeler.kerml.model.kerml.*;
import com.dassault_systemes.modeler.sysml.model.sysml.ObjectiveMembership;
import com.dassault_systemes.modeler.sysml.model.sysml.RequirementConstraintMembership;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.core.Project;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

import static com.cameomcp.plugin.tools.Tool.Mode.*;

/** SysML v2 tools. Registered only when Cameo's SysML v2 plugin classes are present. */
final class Sysml2Tools {

    private Sysml2Tools() {}

    static Project v2(Ctx c) {
        Project p = c.project();
        if (p.getPrimaryModel() != null && V2.roots(p).isEmpty()) {
            throw new ToolError("The active project is a UML/SysML v1 project; use the non-sysml2 tools for it.");
        }
        return p;
    }

    static void register(Map<String, Tool> m) {
        Tools.add(m, "cameo_sysml2_list_roots", READ, c -> {
            Project p = v2(c);
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (Namespace r : c.b("includeLibraries", false) ? V2.allRoots(p) : V2.roots(p)) {
                ObjectNode o = a.addObject().put("id", V2.id(r));
                ArrayNode mem = o.putArray("members");
                for (Element e : r.getOwnedMember()) mem.add(V2.ref(e));
            }
            return Catalog.JSON.createObjectNode().set("roots", a);
        });

        Tools.add(m, "cameo_sysml2_get_element", READ, c -> {
            Project p = v2(c);
            return V2.details(V2.find(p, c.str("elementId")), c.b("evaluate", true));
        });

        Tools.add(m, "cameo_sysml2_get_tree", READ, c -> {
            Project p = v2(c);
            int depth = c.i("depth", 3);
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (Element e : V2.scope(p, c.opt("rootId"))) a.add(tree(e, depth));
            return Catalog.JSON.createObjectNode().set("tree", a);
        });

        Tools.add(m, "cameo_sysml2_find_elements", READ, c -> {
            Project p = v2(c);
            String kind = c.opt("kind");
            String kw = kind == null ? null : V2.normalizeKind(kind);
            Pattern name = c.has("name") ? Pattern.compile(c.str("name"), Pattern.CASE_INSENSITIVE) : null;
            int limit = c.i("limit", 200);
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (Element root : V2.scope(p, c.opt("scopeId"))) {
                V2.walkMembers(root, e -> {
                    if (a.size() >= limit) return;
                    if (kw != null && !V2.keyword(e).equals(kw) && !V2.kind(e).equalsIgnoreCase(kind)) return;
                    if (name != null && (e.getDeclaredName() == null || !name.matcher(e.getDeclaredName()).find())) return;
                    if (kw == null && name == null && e.getDeclaredName() == null) return;
                    a.add(V2.ref(e));
                });
            }
            return Catalog.JSON.createObjectNode().put("count", a.size()).put("truncated", a.size() >= limit).set("elements", a);
        });

        Tools.add(m, "cameo_sysml2_create_element", APP, c -> {
            Project p = v2(c);
            Element owner = owner(c, p);
            String text = declaration(c, p);
            V2.Added added = V2.addText(p, owner, text, false);
            return result(added, text);
        });

        Tools.add(m, "cameo_sysml2_add_text", APP, c -> {
            Project p = v2(c);
            V2.Added added = V2.addText(p, owner(c, p), c.str("text"), c.b("allowErrors", false));
            return result(added, null);
        });

        Tools.add(m, "cameo_sysml2_check_text", APP, c -> {
            Project p = v2(c);
            String text = c.str("text");
            if (c.has("ownerId")) text = V2.wrap(V2.find(p, c.str("ownerId")), text, "__mcp_check");
            V2.Parsed parsed = V2.parse(p, text);
            return Catalog.JSON.createObjectNode().put("valid", !parsed.hasErrors()).set("diagnostics", parsed.diagnostics());
        });

        Tools.add(m, "cameo_sysml2_import_file", APP, c -> {
            Project p = v2(c);
            File f = new File(c.str("filePath"));
            if (!f.isFile()) throw new ToolError("No such file: " + f);
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (Element e : V2.importFile(p, f, c.b("allowErrors", false))) a.add(V2.ref(e));
            return Catalog.JSON.createObjectNode().set("imported", a);
        });

        Tools.add(m, "cameo_sysml2_export_text", READ, c -> {
            Project p = v2(c);
            List<Element> els = new ArrayList<>();
            for (String ref : c.strs("elementIds")) els.add(V2.find(p, ref));
            if (els.isEmpty()) els.addAll(V2.roots(p));
            V2.Export ex = V2.export(els);
            ObjectNode o = exportJson(ex);
            if (c.has("filePath")) {
                File f = new File(c.str("filePath"));
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                Files.writeString(f.toPath(), ex.text(), StandardCharsets.UTF_8);
                o.put("file", f.getAbsolutePath());
            }
            return o.put("text", ex.text());
        });

        Tools.add(m, "cameo_sysml2_set_property", APP, c -> {
            Project p = v2(c);
            Element e = V2.find(p, c.str("elementId"));
            String mult = c.opt("multiplicity"), vex = c.opt("valueExpression");
            // Check every argument before anything is changed, so a bad argument never leaves partial edits behind.
            boolean needsFeature = mult != null || vex != null || c.has("type") || c.has("value") || c.has("direction");
            if (needsFeature && !(e instanceof Feature)) throw new ToolError("type, value, valueExpression, multiplicity and direction need a usage (feature), not " + V2.kind(e));
            if ((c.has("isAbstract") || c.has("specializes")) && !(e instanceof Type)) throw new ToolError("isAbstract and specializes need a definition or usage, not " + V2.kind(e));
            FeatureDirectionKind dir = null;
            if (c.has("direction")) {
                dir = FeatureDirectionKind.get(c.str("direction").toLowerCase(Locale.ROOT));
                if (dir == null) throw new ToolError("direction must be in, out or inout");
            }
            List<Type> types = new ArrayList<>(), supers = new ArrayList<>();
            for (String t : c.strs("type")) types.add(asType(p, t));
            for (String s : c.strs("specializes")) supers.add(asType(p, s));
            if (c.has("value") && !c.node("value").isValueNode()) throw new ToolError("value must be a number, boolean or string; use valueExpression for expressions");
            boolean any = needsFeature || c.has("name") || c.has("shortName") || c.has("documentation") || c.has("isAbstract") || !supers.isEmpty();
            if (!any) throw new ToolError("Nothing to set: give name, shortName, documentation, isAbstract, direction, type, specializes, multiplicity, value or valueExpression");
            if (mult != null || vex != null) {
                String probe = "attribute __mcp_check" + (mult != null ? " [" + mult + "]" : "") + (vex != null ? " = " + vex : "") + ";";
                Feature f = (Feature) e;
                V2.Parsed parsed = V2.parse(p, V2.wrap(f.getOwningType() != null ? f.getOwningType() : f.getOwner(), probe, "__mcp_check"));
                if (parsed.hasErrors()) throw new ToolError("multiplicity/valueExpression has errors; nothing was changed: " + parsed.diagnostics());
            }
            // Multiplicity and expressions are built by Cameo's text importer as a helper feature, then moved over.
            Feature donor = null;
            if (mult != null || vex != null) {
                Feature f = (Feature) e;
                Element ctxOwner = f.getOwningType() != null ? f.getOwningType() : f.getOwner();
                String snippet = "attribute __mcp_donor" + (mult != null ? " [" + mult + "]" : "") + (vex != null ? " = " + vex : "") + ";";
                List<Element> d = V2.addText(p, ctxOwner, snippet, false).elements();
                donor = d.isEmpty() ? null : (Feature) d.get(0);
            }
            final Feature fDonor = donor;
            final FeatureDirectionKind fDir = dir;
            List<String> changed = new ArrayList<>();
            try {
                Tools.inSession(p, "MCP: cameo_sysml2_set_property", () -> {
                    if (c.has("name")) { e.setDeclaredName(c.str("name")); changed.add("name"); }
                    if (c.has("shortName")) { e.setDeclaredShortName(c.str("shortName")); changed.add("shortName"); }
                    if (c.has("documentation")) { V2.setDocumentation(e, c.str("documentation")); changed.add("documentation"); }
                    if (c.has("isAbstract")) { ((Type) e).setIsAbstract(c.b("isAbstract", false)); changed.add("isAbstract"); }
                    if (fDir != null) { ((Feature) e).setDirection(fDir); changed.add("direction"); }
                    if (!types.isEmpty()) { V2.setTypes((Feature) e, types); changed.add("type"); }
                    for (Type s : supers) { V2.addSpecialization((Type) e, s); changed.add("specializes " + V2.qn(s)); }
                    if (c.has("value")) { V2.setLiteral((Feature) e, c.node("value")); changed.add("value"); }
                    if (fDonor != null) {
                        V2.transplant(fDonor, (Feature) e, vex != null, mult != null);
                        if (mult != null) changed.add("multiplicity");
                        if (vex != null) changed.add("valueExpression");
                    }
                    return null;
                });
            } catch (Exception | Error ex) {
                if (fDonor != null) {
                    try { Tools.inSession(p, "MCP: remove helper", () -> { V2.delete(fDonor); return null; }); } catch (Exception ignored) { /* original error is reported */ }
                }
                throw ex;
            }
            ObjectNode o = V2.details(e, true);
            ArrayNode ch = o.putArray("changed");
            changed.forEach(ch::add);
            if (fDonor != null) o.put("undoSteps", 3);
            return o;
        });

        Tools.add(m, "cameo_sysml2_delete_element", WRITE, c -> {
            Project p = v2(c);
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (String ref : c.strs("elementIds")) {
                Element e = V2.find(p, ref);
                a.add(V2.ref(e));
                V2.delete(e);
            }
            return Catalog.JSON.createObjectNode().set("deleted", a);
        });

        Tools.add(m, "cameo_sysml2_move_element", WRITE, c -> {
            Project p = v2(c);
            Element e = V2.find(p, c.str("elementId"));
            Element to = V2.find(p, c.str("newOwnerId"));
            Relationship owning = e.getOwningRelationship();
            if (owning == null) throw new ToolError("Root namespaces cannot be moved");
            to.getOwnedRelationship().add(owning);
            return V2.ref(e);
        });

        Tools.add(m, "cameo_sysml2_get_relationships", READ, c -> {
            Project p = v2(c);
            Element e = V2.find(p, c.str("elementId"));
            return V2.relationships(p, e).set("element", V2.ref(e));
        });

        Tools.add(m, "cameo_sysml2_connect", APP, c -> {
            Project p = v2(c);
            Element owner = owner(c, p);
            String kind = c.str("kind").toLowerCase(Locale.ROOT);
            String a = c.str("source"), b = c.str("target");
            String n = V2.name(c.opt("name"));
            String type = c.has("type") ? " : " + typeText(p, c.str("type")) : "";
            String decl = n.isEmpty() && type.isEmpty() ? "" : " " + (n.isEmpty() ? "" : n) + type;
            String text = switch (kind) {
                case "connection", "connect" -> (decl.isEmpty() ? "" : "connection" + decl + " ") + "connect " + a + " to " + b + ";";
                case "interface" -> "interface" + decl + " connect " + a + " to " + b + ";";
                case "binding", "bind" -> "bind " + a + " = " + b + ";";
                case "flow" -> "flow" + decl + (c.has("itemType") ? " of " + typeText(p, c.str("itemType")) : "") + " from " + a + " to " + b + ";";
                case "succession" -> "first " + a + " then " + b + ";";
                case "allocation", "allocate" -> (decl.isEmpty() ? "" : "allocation" + decl + " ") + "allocate " + a + " to " + b + ";";
                case "satisfy" -> "satisfy " + a + " by " + b + ";";
                case "dependency" -> "dependency" + (n.isEmpty() ? "" : " " + n) + " from " + a + " to " + b + ";";
                default -> throw new ToolError("kind must be connection, interface, binding, flow, succession, allocation, satisfy or dependency");
            };
            return result(V2.addText(p, owner, text, false), text);
        });

        Tools.add(m, "cameo_sysml2_validate", READ, c -> {
            Project p = v2(c);
            return validate(p, V2.scope(p, c.opt("scopeId")), c.i("limit", 300));
        });
    }

    // ------------------------------------------------------------------ helpers

    private static Element owner(Ctx c, Project p) {
        if (c.has("ownerId")) return V2.find(p, c.str("ownerId"));
        List<Namespace> roots = V2.roots(p);
        for (Namespace r : roots) for (Element e : r.getOwnedMember()) if ("Model".equals(e.getDeclaredName())) return e;
        if (roots.isEmpty()) throw new ToolError("No SysML v2 root namespace in the project; give ownerId");
        return roots.get(0);
    }

    private static Type asType(Project p, String ref) {
        Element t = V2.find(p, ref);
        if (!(t instanceof Type type)) throw new ToolError(ref + " is a " + V2.kind(t) + ", not a type");
        return type;
    }

    /** Element id or qualified name to text usable in SysML (qualified name). */
    private static String typeText(Project p, String ref) {
        Element t = V2.findOpt(p, ref);
        return t == null ? ref : V2.qnText(t);
    }

    private static String declaration(Ctx c, Project p) {
        String kw = V2.normalizeKind(c.str("kind"));
        StringBuilder s = new StringBuilder();
        if (c.has("direction")) s.append(c.str("direction").toLowerCase(Locale.ROOT)).append(' ');
        if (c.b("isAbstract", false)) s.append("abstract ");
        s.append(kw);
        if (c.has("shortName")) s.append(" <").append(V2.name(c.str("shortName"))).append('>');
        if (c.has("name")) s.append(' ').append(V2.name(c.str("name")));
        List<String> types = new ArrayList<>();
        for (String t : c.strs("type")) types.add(typeText(p, t));
        if (!types.isEmpty()) s.append(" : ").append(String.join(", ", types));
        if (c.has("multiplicity")) s.append(" [").append(c.str("multiplicity")).append(']');
        List<String> sp = new ArrayList<>();
        for (String t : c.strs("specializes")) sp.add(typeText(p, t));
        if (!sp.isEmpty()) s.append(" :> ").append(String.join(", ", sp));
        List<String> rd = new ArrayList<>();
        for (String t : c.strs("redefines")) rd.add(typeText(p, t));
        if (!rd.isEmpty()) s.append(" :>> ").append(String.join(", ", rd));
        if (c.has("value")) {
            JsonNode v = c.node("value");
            s.append(" = ").append(v.isTextual() ? v.asText() : v.toString());
        }
        String doc = c.opt("documentation"), body = c.opt("body");
        if (doc == null && body == null) return s.append(';').toString();
        s.append(" {\n");
        if (doc != null) s.append("    doc /* ").append(doc.replace("*/", "* /")).append(" */\n");
        if (body != null) s.append(body).append('\n');
        return s.append('}').toString();
    }

    /** complete=false (with the list of gaps) whenever some construct could not be written exactly. */
    static ObjectNode exportJson(V2.Export ex) {
        ObjectNode o = Catalog.JSON.createObjectNode().put("complete", ex.gaps().isEmpty());
        if (!ex.gaps().isEmpty()) {
            ArrayNode g = o.putArray("gaps");
            ex.gaps().forEach(g::add);
        }
        if (ex.syntheticNames() > 0) {
            o.put("syntheticNames", ex.syntheticNames()).put("note", "Unnamed decision/merge/fork/join nodes were given names "
                    + "(_decide1, ...) so successions can refer to them.");
        }
        return o;
    }

    private static ObjectNode result(V2.Added added, String text) {
        ObjectNode o = Catalog.JSON.createObjectNode();
        ArrayNode a = o.putArray("created");
        for (Element e : added.elements()) a.add(V2.ref(e));
        if (text != null) o.put("text", text);
        if (!added.diagnostics().isEmpty()) o.set("diagnostics", added.diagnostics());
        o.put("undoSteps", 2).put("note", "Cameo records this as two undo steps (import, then placement): call cameo_undo with steps=2 to remove it.");
        return o;
    }

    private static ObjectNode tree(Element e, int depth) {
        ObjectNode o = V2.ref(e);
        if (depth > 0 && e instanceof Namespace ns && !ns.getOwnedMember().isEmpty()) {
            ArrayNode a = o.putArray("members");
            for (Element m : ns.getOwnedMember()) {
                if (m instanceof MultiplicityRange || m instanceof Expression && !(m instanceof Feature f && f.getDeclaredName() != null)) continue;
                a.add(tree(m, depth - 1));
            }
        }
        return o;
    }

    private static final Set<String> SHOULD_BE_TYPED = Set.of("PartUsage", "PortUsage", "ItemUsage", "AttributeUsage", "InterfaceUsage");

    /** Model checks Cameo does not report in the API: missing names and types, duplicates, unsatisfied requirements, failing constraints. */
    static ObjectNode validate(Project p, List<Element> scope, int limit) {
        ArrayNode issues = Catalog.JSON.createArrayNode();
        Map<String, Integer> counts = new TreeMap<>();
        Set<Element> satisfied = new HashSet<>();
        List<Element> all = new ArrayList<>();
        for (Namespace r : V2.roots(p)) V2.walkMembers(r, x -> {
            if (x instanceof com.dassault_systemes.modeler.sysml.model.sysml.SatisfyRequirementUsage s && s.getSatisfiedRequirement() != null) {
                satisfied.add(s.getSatisfiedRequirement());
                satisfied.addAll(s.getSatisfiedRequirement().getType());
            }
        });
        for (Element root : scope) V2.walkMembers(root, all::add);
        for (Element e : all) {
            String k = V2.kind(e);
            if (k.endsWith("Definition") && !k.equals("ConjugatedPortDefinition") && e.getDeclaredName() == null) add(issues, counts, limit, e, "error", "unnamed-definition", "Definition has no name");
            if (SHOULD_BE_TYPED.contains(k) && e instanceof Feature f && f.getOwnedTyping().isEmpty()
                    && f.getOwnedSpecialization().stream().noneMatch(s -> !s.isImplied())) {
                add(issues, counts, limit, e, "warning", "untyped-usage", V2.keyword(e) + " '" + e.getDeclaredName() + "' has no type");
            }
            if (k.equals("RequirementUsage") || k.equals("RequirementDefinition")) {
                // Objectives of cases and requirements nested in other requirements are not satisfied on their own.
                boolean nested = e.getOwner() instanceof Feature || e.getOwningRelationship() instanceof ObjectiveMembership
                        || e.getOwningRelationship() instanceof RequirementConstraintMembership;
                if (!satisfied.contains(e) && !nested) add(issues, counts, limit, e, "info", "unsatisfied-requirement", "No satisfy relationship points to this requirement");
                if (V2.documentation(e) == null) add(issues, counts, limit, e, "info", "requirement-without-text", "Requirement has no doc text");
            }
            if (k.equals("ConstraintUsage") || k.equals("AssertConstraintUsage")) {
                Boolean b = V2.constraint(e);
                if (Boolean.FALSE.equals(b)) add(issues, counts, limit, e, "error", "constraint-violated", "Constraint evaluates to false");
            }
            if (e instanceof Namespace ns) {
                Map<String, Integer> names = new HashMap<>();
                for (Element mem : ns.getOwnedMember()) if (mem.getDeclaredName() != null) names.merge(mem.getDeclaredName(), 1, Integer::sum);
                names.forEach((n, cnt) -> { if (cnt > 1) add(issues, counts, limit, e, "error", "duplicate-name", cnt + " members are named '" + n + "'"); });
            }
        }
        ObjectNode o = Catalog.JSON.createObjectNode().put("elementsChecked", all.size()).put("issueCount", counts.values().stream().mapToInt(i -> i).sum());
        ObjectNode byRule = o.putObject("byRule");
        counts.forEach(byRule::put);
        o.set("issues", issues);
        return o;
    }

    private static void add(ArrayNode issues, Map<String, Integer> counts, int limit, Element e, String sev, String rule, String msg) {
        counts.merge(rule, 1, Integer::sum);
        if (issues.size() < limit) issues.add(V2.ref(e).put("severity", sev).put("rule", rule).put("message", msg));
    }
}
