package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.dassault_systemes.modeler.kerml.evaluation.ModelLevelExpressionEvaluatorEngine;
import com.dassault_systemes.modeler.kerml.model.kerml.*;
import com.dassault_systemes.modeler.magic.sysml.core.TextualModelHelper;
import com.dassault_systemes.modeler.sysml.model.RichElementsFactory;
import com.dassault_systemes.modeler.sysml.model.sysml.AcceptActionUsage;
import com.dassault_systemes.modeler.sysml.model.sysml.ConjugatedPortDefinition;
import com.dassault_systemes.modeler.sysml.model.sysml.MetadataUsage;
import com.dassault_systemes.modeler.sysml.model.sysml.ActionUsage;
import com.dassault_systemes.modeler.sysml.model.sysml.SatisfyRequirementUsage;
import com.dassault_systemes.modeler.sysml.model.sysml.StateSubactionMembership;
import com.dassault_systemes.modeler.sysml.model.sysml.TransitionUsage;
import com.dassault_systemes.modeler.sysml.textual.core.interfaces.ICompilationUnit;
import com.dassault_systemes.modeler.sysml.textual.core.interfaces.IDiagnostic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.ci.persistence.IAttachedProject;
import com.nomagic.magicdraw.core.ProjectUtilities;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.uml.BaseElement;
import com.nomagic.task.EmptyProgressStatus;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * SysML v2 (KerML) model access for Cameo 2026x. Only this class and Sysml2Tools touch the v2 API, so the plugin
 * still loads on Cameo editions without SysML v2. Writes go through the official textual import: SysML text is
 * parsed by Cameo, then the new elements are moved under the requested owner. That keeps every element, membership
 * and reference exactly as Cameo itself would build it.
 */
final class V2 {

    private V2() {}

    // ------------------------------------------------------------------ project roots and lookup

    /** Root namespaces owned by the project itself (one per imported text file, plus the template's), not by used modules. */
    static List<Namespace> roots(Project p) {
        Set<String> attached = new HashSet<>();
        for (IAttachedProject ap : ProjectUtilities.getAllAttachedProjects(p)) {
            String[] loc = String.valueOf(ap.getLocationURI()).split("/");
            if (loc.length > 1 && !loc[1].isEmpty()) attached.add("/R" + loc[1] + "/");
        }
        List<Namespace> own = new ArrayList<>();
        for (Resource r : p.getPrimaryProject().getResourceSet().getResources()) {
            String uri = String.valueOf(r.getURI());
            if (attached.stream().anyMatch(uri::contains)) continue;
            for (EObject x : r.getContents()) if (x instanceof Namespace ns && ns.getOwner() == null) own.add(ns);
        }
        return own;
    }

    /** All root namespaces including the standard libraries. */
    static List<Namespace> allRoots(Project p) {
        List<Namespace> out = new ArrayList<>();
        for (Resource r : p.getPrimaryProject().getResourceSet().getResources()) {
            for (EObject x : r.getContents()) if (x instanceof Namespace ns && ns.getOwner() == null) out.add(ns);
        }
        return out;
    }

    static Element find(Project p, String ref) {
        Element e = findOpt(p, ref);
        if (e == null) throw new ToolError("No SysML v2 element with id or qualified name '" + ref + "'");
        return e;
    }

    static Element findOpt(Project p, String ref) {
        if (ref == null || ref.isEmpty()) return null;
        BaseElement b = p.getElementByID(ref);
        if (b instanceof Element e) return e;
        String[] seg = splitQn(ref);
        for (List<Namespace> roots : List.of(roots(p), allRoots(p))) {
            for (Namespace r : roots) {
                Element cur = r;
                for (String s : seg) {
                    cur = child(cur, s);
                    if (cur == null) break;
                }
                if (cur != null) return cur;
            }
        }
        return null;
    }

    /** Splits A::'B::C'::D into [A, B::C, D]: "::" inside quotes is part of a name. */
    static String[] splitQn(String qn) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < qn.length(); i++) {
            char ch = qn.charAt(i);
            if (ch == '\\' && quoted && i + 1 < qn.length()) { cur.append(ch).append(qn.charAt(++i)); continue; }
            if (ch == '\'') quoted = !quoted;
            if (!quoted && ch == ':' && i + 1 < qn.length() && qn.charAt(i + 1) == ':') {
                parts.add(unquote(cur.toString().trim()));
                cur.setLength(0);
                i++;
                continue;
            }
            cur.append(ch);
        }
        parts.add(unquote(cur.toString().trim()));
        return parts.toArray(new String[0]);
    }

    private static String unquote(String s) {
        if (s.length() < 2 || !s.startsWith("'") || !s.endsWith("'")) return s;
        return s.substring(1, s.length() - 1).replace("\\'", "'").replace("\\\\", "\\");
    }

    private static Element child(Element owner, String name) {
        if (!(owner instanceof Namespace ns)) return null;
        for (Element m : ns.getOwnedMember()) {
            if (name.equals(m.getDeclaredName()) || name.equals(m.getDeclaredShortName())) return m;
        }
        return null;
    }

    /** A feature of a type by name, including inherited features. Paths like "engine.mass" walk nested features. */
    static Feature feature(Element owner, String path) {
        Element cur = owner;
        Feature f = null;
        for (String s : path.split("\\.")) {
            f = null;
            if (cur instanceof Type t) {
                for (Feature x : t.getOwnedFeature()) if (s.equals(x.getName())) { f = x; break; }
                if (f == null) for (Feature x : t.getFeature()) if (s.equals(x.getName())) { f = x; break; }
            }
            if (f == null) return null;
            cur = f;
        }
        return f;
    }

    static boolean isOwnedBelow(Element e, Element root) {
        for (Element x = e.getOwner(); x != null; x = x.getOwner()) if (x == root) return true;
        return false;
    }

    // ------------------------------------------------------------------ JSON views

    static String id(Element e) {
        return e == null ? null : e instanceof BaseElement b ? b.getID() : e.getElementId();
    }

    static String kind(Element e) { return ((EObject) e).eClass().getName(); }

    static ObjectNode ref(Element e) {
        ObjectNode o = Catalog.JSON.createObjectNode().put("id", id(e));
        if (e.getDeclaredName() != null) o.put("name", e.getDeclaredName());
        o.put("kind", kind(e));
        String qn = e.getQualifiedName();
        if (qn != null) o.put("qualifiedName", qn);
        else if (e.getOwner() != null) {
            // Unnamed elements (e.g. constraints) still need a readable place: "Owner::QN::(constraint)".
            o.put("qualifiedName", qn(e.getOwner()) + "::(" + (keyword(e).isEmpty() ? kind(e) : keyword(e)) + ")");
        }
        return o;
    }

    static ObjectNode details(Element e, boolean evaluate) {
        ObjectNode o = ref(e);
        o.put("keyword", keyword(e));
        if (e.getDeclaredShortName() != null) o.put("shortName", e.getDeclaredShortName());
        Element owner = e.getOwner();
        if (owner != null) o.set("owner", ref(owner));
        String doc = documentation(e);
        if (doc != null) o.put("documentation", doc);
        if (e instanceof Type t) {
            o.put("isAbstract", t.isAbstract());
            ArrayNode sp = Catalog.JSON.createArrayNode();
            for (Specialization s : t.getOwnedSpecialization()) {
                if (s instanceof FeatureTyping || s.isImplied()) continue;
                Type g = s.getGeneral();
                if (g != null) sp.add(Catalog.JSON.createObjectNode().put("kind", kind(s)).put("general", qn(g)));
            }
            if (!sp.isEmpty()) o.set("specializes", sp);
        }
        if (e instanceof Feature f) {
            ArrayNode types = o.putArray("types");
            for (Type t : f.getType()) types.add(qn(t));
            if (f.getDirection() != null) o.put("direction", f.getDirection().getName());
            String m = multiplicity(f);
            if (m != null) o.put("multiplicity", m);
            Expression v = valueExpression(f);
            if (v != null) {
                o.put("valueExpression", expr(v));
                if (evaluate) o.set("value", evaluate(f));
            }
        }
        Boolean c = evaluate ? constraint(e) : null;
        if (c != null) o.put("constraintSatisfied", c);
        if (e instanceof Namespace ns) {
            ArrayNode a = o.putArray("members");
            for (Element m : ns.getOwnedMember()) if (!internal(m)) a.add(ref(m));
        }
        if (e instanceof Relationship r) {
            ArrayNode s = o.putArray("source"), t = o.putArray("target");
            r.getSource().forEach(x -> s.add(ref(x)));
            r.getTarget().forEach(x -> t.add(ref(x)));
        }
        return o;
    }

    /** Parts of expressions and multiplicities that the textual notation does not show as members. */
    static boolean internal(Element m) {
        return m instanceof MultiplicityRange || m instanceof Expression && m.getDeclaredName() == null
                || m.getDeclaredName() == null && m.getOwner() instanceof Expression;
    }

    static String qn(Element e) {
        String q = e.getQualifiedName();
        return q != null ? q : e.getName() != null ? e.getName() : id(e);
    }

    static String documentation(Element e) {
        for (Documentation d : e.getDocumentation()) if (d.getBody() != null) return d.getBody().trim();
        return null;
    }

    static Expression valueExpression(Feature f) {
        for (Relationship r : f.getOwnedRelationship()) if (r instanceof FeatureValue fv) return fv.getValue();
        return null;
    }

    static String multiplicity(Feature f) {
        for (Element m : f.getOwnedMember()) if (m instanceof MultiplicityRange r) return mult(r);
        return null;
    }

    private static String mult(MultiplicityRange r) {
        Expression lo = r.getLowerBound(), hi = r.getUpperBound();
        return lo == null || lo == hi ? expr(hi) : expr(lo) + ".." + expr(hi);
    }

    // ------------------------------------------------------------------ walking

    /** Every owned element below root, including relationships and the elements they own. */
    static void walkAll(Element root, Consumer<Element> visit) {
        Deque<Element> stack = new ArrayDeque<>(List.of(root));
        Set<Element> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            Element e = stack.pop();
            if (!seen.add(e)) continue;
            visit.accept(e);
            List<Relationship> rels = e.getOwnedRelationship();
            for (int i = rels.size() - 1; i >= 0; i--) {
                Relationship r = rels.get(i);
                stack.push(r);
            }
            if (e instanceof Relationship r) {
                List<Element> kids = r.getOwnedRelatedElement();
                for (int i = kids.size() - 1; i >= 0; i--) stack.push(kids.get(i));
            }
        }
    }

    /** Owned members (no relationships) below root, depth first. */
    static void walkMembers(Element root, Consumer<Element> visit) {
        walkAll(root, e -> { if (isStructural(e)) visit.accept(e); });
    }

    static List<Element> scope(Project p, String scopeRef) {
        if (scopeRef != null && !scopeRef.isEmpty()) return List.of(find(p, scopeRef));
        return new ArrayList<>(roots(p));
    }

    // ------------------------------------------------------------------ keywords

    private static final Map<String, String> KW = Map.ofEntries(
            Map.entry("Calculation", "calc"), Map.entry("Enumeration", "enum"), Map.entry("AnalysisCase", "analysis"),
            Map.entry("VerificationCase", "verification"), Map.entry("Reference", "ref"),
            Map.entry("PerformAction", "perform"), Map.entry("ExhibitState", "exhibit"),
            Map.entry("IncludeUseCase", "include"), Map.entry("SatisfyRequirement", "satisfy"),
            Map.entry("AssertConstraint", "assert constraint"), Map.entry("BindingConnectorAs", "binding"),
            Map.entry("SuccessionAs", "succession"), Map.entry("Flow", "flow"), Map.entry("SuccessionFlow", "succession flow"),
            Map.entry("EventOccurrence", "event occurrence"), Map.entry("AcceptAction", "accept"),
            Map.entry("SendAction", "send"), Map.entry("AssignmentAction", "assign"), Map.entry("IfAction", "if"),
            Map.entry("ForLoopAction", "for"), Map.entry("WhileLoopAction", "while"), Map.entry("UseCase", "use case"),
            Map.entry("Metadata", "metadata"), Map.entry("LibraryPackage", "library package"),
            Map.entry("Package", "package"), Map.entry("Namespace", "package"), Map.entry("Transition", "transition"),
            Map.entry("DecisionNode", "decide"), Map.entry("MergeNode", "merge"), Map.entry("ForkNode", "fork"),
            Map.entry("JoinNode", "join"), Map.entry("", ""));

    /** SysML textual keyword for an element: PartDefinition -> "part def", StateUsage -> "state". */
    static String keyword(Element e) { return keywordOf(kind(e)); }

    static String keywordOf(String eclass) {
        String base = eclass;
        String suffix = "";
        if (base.endsWith("Definition")) { base = base.substring(0, base.length() - 10); suffix = " def"; }
        else if (base.endsWith("Usage")) base = base.substring(0, base.length() - 5);
        String k = KW.get(base);
        if (k == null) k = base.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return (k + suffix).trim(); // plain Definition -> "def", plain Usage -> ""
    }

    /** Accepts "part def", "PartDefinition", "partDefinition", "part" and returns the textual keyword. */
    static String normalizeKind(String kind) {
        String k = kind.trim();
        if (k.contains(" ") || k.equals(k.toLowerCase(Locale.ROOT))) return k;
        String ec = Character.toUpperCase(k.charAt(0)) + k.substring(1);
        return keywordOf(ec.endsWith("Definition") || ec.endsWith("Usage") ? ec : ec + "Usage");
    }

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    static String name(String n) {
        if (n == null || n.isEmpty()) return "";
        return IDENT.matcher(n).matches() ? n : "'" + n.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** Qualified name as SysML text. Cameo's own qualified name is already quoted where needed. */
    static String qnText(Element e) {
        String q = e.getQualifiedName();
        if (q != null && !q.isEmpty()) return q;
        List<String> parts = new ArrayList<>();
        for (Element cur = e; cur != null && cur.getDeclaredName() != null; cur = cur.getOwner()) {
            if (cur instanceof Membership) continue;
            parts.add(0, name(cur.getDeclaredName()));
        }
        return String.join("::", parts);
    }

    /** Model elements, including relationships that are elements in their own right (connections, interface definitions). */
    static boolean isStructural(Element e) {
        return !(e instanceof Relationship) || e instanceof Type;
    }

    // ------------------------------------------------------------------ textual import

    record Parsed(ICompilationUnit unit, ArrayNode diagnostics, boolean hasErrors) {}

    static Parsed parse(Project p, String text) throws Exception {
        File f = File.createTempFile("cameo-mcp", ".sysml");
        try {
            Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
            ICompilationUnit cu = TextualModelHelper.importTextualModel(p, f, EmptyProgressStatus.getDefault());
            ArrayNode d = Catalog.JSON.createArrayNode();
            boolean err = false;
            for (IDiagnostic x : cu.getDiagnostics()) {
                String sev = String.valueOf(x.getSeverity());
                err |= sev.toUpperCase(Locale.ROOT).contains("ERROR");
                d.addObject().put("severity", sev).put("line", x.getLine()).put("column", x.getColumn()).put("message", x.getMessage());
            }
            return new Parsed(cu, d, err);
        } finally {
            f.delete();
        }
    }

    /** Text that parses the snippet as if it were written inside owner (owner's features and enclosing names resolve). */
    static String wrap(Element owner, String snippet, String tmpName) {
        StringBuilder sb = new StringBuilder("package ").append(tmpName).append(" {\n");
        for (Element a = owner; a != null; a = a.getOwner()) {
            if (a instanceof Namespace && !(a instanceof Membership) && a.getDeclaredName() != null && !(a instanceof Feature)) {
                sb.append("  private import ").append(qnText(a)).append("::*;\n");
            }
        }
        if (owner instanceof Type t && owner.getDeclaredName() != null) {
            sb.append("  ").append(keyword(owner)).append(" __mcp_w :> ").append(qnText(owner)).append(" {\n")
                    .append(snippet).append("\n  }\n");
        } else if (owner instanceof Type) {
            sb.append("  ").append(keyword(owner)).append(" __mcp_w {\n").append(snippet).append("\n  }\n");
        } else {
            sb.append(snippet).append('\n');
        }
        return sb.append("}\n").toString();
    }

    /** Result of adding text: the new top-level elements and the parser messages. */
    record Added(List<Element> elements, ArrayNode diagnostics) {}

    /**
     * Parse snippet in the context of owner and add the resulting elements to owner. Parsing and the import run
     * outside an editing session (Cameo's importer requires it); moving the elements and removing the temporary
     * root happen in one session, so that part is one undoable step.
     */
    static Added addText(Project p, Element owner, String snippet, boolean allowErrors) throws Exception {
        // Refuse before importing: the move below needs a new editing session, and a half-done add would leave
        // the temporary package in the model (for example while the user has a Specification dialog open).
        requireNoSession(p);
        // Readable on purpose: undoing only the last of the two steps shows this package; undoing again removes it.
        String tmp = "MCP_import_undo_again_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String text = wrap(owner, snippet, tmp);
        Parsed parsed = parse(p, text);
        if (parsed.hasErrors() && !allowErrors) {
            throw new ToolError("SysML text has errors; nothing was added. Lines refer to this text:\n" + text
                    + "\nDiagnostics: " + parsed.diagnostics());
        }
        Set<Namespace> before = new HashSet<>(roots(p));
        TextualModelHelper.textualModelIntoProject(p, parsed.unit().getTransientRootNs(), EmptyProgressStatus.getDefault());
        Namespace root = null;
        Element pkg = null;
        for (Namespace r : roots(p)) {
            Element c = child(r, tmp);
            if (c != null) { root = r; pkg = c; break; }
        }
        Element container = pkg == null ? null : owner instanceof Type ? child(pkg, "__mcp_w") : pkg;
        if (container == null) {
            for (Namespace r : roots(p)) if (!before.contains(r)) removeQuietly(p, r);
            throw new ToolError("Cameo imported the text but the new elements could not be found; the import was removed");
        }
        final Namespace tmpRoot = root;
        final Element from = container;
        List<Element> added = new ArrayList<>();
        try {
            Tools.inSession(p, "MCP: add SysML text", () -> {
                for (Relationship r : new ArrayList<>(from.getOwnedRelationship())) {
                    if (!(r instanceof Membership m)) continue;
                    owner.getOwnedRelationship().add(r);
                    Element el = m.getMemberElement();
                    if (el != null) added.add(el);
                }
                ModelElementsManager.getInstance().removeElement(tmpRoot);
                return null;
            });
        } catch (Exception | Error ex) {
            removeQuietly(p, tmpRoot); // never leave the temporary import behind
            throw ex;
        }
        return new Added(added, parsed.diagnostics());
    }

    static void requireNoSession(Project p) {
        if (SessionManager.getInstance().isSessionCreated(p)) {
            throw new ToolError("Cameo has an open editing session (for example a Specification dialog). Close it and try again; nothing was changed.");
        }
    }

    private static void removeQuietly(Project p, Element e) {
        try {
            Tools.inSession(p, "MCP: remove temporary import", () -> { ModelElementsManager.getInstance().removeElement(e); return null; });
        } catch (Exception ignored) {
            // the caller reports the original error
        }
    }

    /** Import a .sysml file as new root namespaces. */
    static List<Element> importFile(Project p, File f, boolean allowErrors) throws Exception {
        requireNoSession(p);
        Parsed parsed = parse(p, Files.readString(f.toPath(), StandardCharsets.UTF_8));
        if (parsed.hasErrors() && !allowErrors) throw new ToolError("File has errors; nothing imported: " + parsed.diagnostics());
        Set<Namespace> before = new HashSet<>(roots(p));
        TextualModelHelper.textualModelIntoProject(p, parsed.unit().getTransientRootNs(), EmptyProgressStatus.getDefault());
        List<Element> out = new ArrayList<>();
        for (Namespace r : roots(p)) if (!before.contains(r)) out.addAll(r.getOwnedMember());
        return out;
    }

    /** Delete an element together with the membership that owns it. */
    static void delete(Element e) throws Exception {
        Relationship owning = e.getOwningRelationship();
        Element target = owning instanceof OwningMembership ? owning : e;
        ModelElementsManager.getInstance().removeElement(target);
    }

    // ------------------------------------------------------------------ direct edits (call inside a session)

    static void setDocumentation(Element e, String text) {
        for (Documentation d : e.getDocumentation()) { d.setBody(text); return; }
        RichElementsFactory f = RichElementsFactory.get(e);
        OwningMembership m = f.createOwningMembership();
        e.getOwnedRelationship().add(m);
        Documentation d = f.createDocumentation();
        m.getOwnedRelatedElement().add(d);
        d.setBody(text);
    }

    static void setTypes(Feature f, List<Type> types) throws Exception {
        for (Relationship r : new ArrayList<>(f.getOwnedRelationship())) {
            if (r instanceof FeatureTyping) ModelElementsManager.getInstance().removeElement(r);
        }
        RichElementsFactory fac = RichElementsFactory.get(f);
        for (Type t : types) {
            FeatureTyping ft = fac.createFeatureTyping();
            f.getOwnedRelationship().add(ft);
            ft.setType(t);
            ft.setTypedFeature(f);
        }
    }

    static void addSpecialization(Type specific, Type general) {
        RichElementsFactory fac = RichElementsFactory.get(specific);
        if (specific instanceof Feature sf && general instanceof Feature gf) {
            Subsetting s = fac.createSubsetting();
            specific.getOwnedRelationship().add(s);
            s.setSubsettedFeature(gf);
            s.setSubsettingFeature(sf);
        } else if (specific instanceof Classifier sc && general instanceof Classifier gc) {
            Subclassification s = fac.createSubclassification();
            specific.getOwnedRelationship().add(s);
            s.setSuperclassifier(gc);
            s.setSubclassifier(sc);
        } else {
            throw new ToolError(kind(specific) + " cannot specialize " + kind(general));
        }
    }

    /** Replace a feature's value with a literal (number, boolean or string). */
    static void setLiteral(Feature f, JsonNode v) throws Exception {
        removeValue(f);
        RichElementsFactory fac = RichElementsFactory.get(f);
        FeatureValue fv = fac.createFeatureValue();
        f.getOwnedRelationship().add(fv);
        Expression lit;
        if (v.isBoolean()) { LiteralBoolean b = fac.createLiteralBoolean(); b.setValue(v.asBoolean()); lit = b; }
        else if (v.isIntegralNumber()) { LiteralInteger i = fac.createLiteralInteger(); i.setValue(v.asInt()); lit = i; }
        else if (v.isNumber()) { LiteralRational r = fac.createLiteralRational(); r.setValue(v.asDouble()); lit = r; }
        else { LiteralString s = fac.createLiteralString(); s.setValue(v.asText()); lit = s; }
        fv.getOwnedRelatedElement().add(lit);
    }

    static void removeValue(Feature f) throws Exception {
        for (Relationship r : new ArrayList<>(f.getOwnedRelationship())) {
            if (r instanceof FeatureValue) ModelElementsManager.getInstance().removeElement(r);
        }
    }

    /** Move the value and/or multiplicity of a donor feature (built from text) onto target, then delete the donor. */
    static void transplant(Feature donor, Feature target, boolean value, boolean multiplicity) throws Exception {
        if (value) {
            removeValue(target);
            for (Relationship r : new ArrayList<>(donor.getOwnedRelationship())) {
                if (r instanceof FeatureValue) target.getOwnedRelationship().add(r);
            }
        }
        if (multiplicity) {
            for (Relationship r : new ArrayList<>(target.getOwnedRelationship())) {
                if (r instanceof OwningMembership m && m.getMemberElement() instanceof MultiplicityRange) {
                    ModelElementsManager.getInstance().removeElement(r);
                }
            }
            for (Relationship r : new ArrayList<>(donor.getOwnedRelationship())) {
                if (r instanceof OwningMembership m && m.getMemberElement() instanceof MultiplicityRange) target.getOwnedRelationship().add(r);
            }
        }
        delete(donor);
    }

    // ------------------------------------------------------------------ evaluation

    /** Evaluate a feature's value expression in the context of its owning type. */
    static JsonNode evaluate(Feature f) {
        return evaluate(f, f.getOwningType() != null ? f.getOwningType() : f);
    }

    /** Evaluate f as seen from ctx: an inherited "total = m1 + m2" uses the m1 and m2 that ctx redefines. */
    static JsonNode evaluate(Feature f, Element ctx) {
        try {
            List<Element> r = ModelLevelExpressionEvaluatorEngine.evaluate(f, ctx);
            return values(r);
        } catch (RuntimeException ex) {
            return Catalog.JSON.getNodeFactory().textNode("evaluation failed: " + ex);
        }
    }

    private static JsonNode values(List<Element> r) {
        if (r == null || r.isEmpty()) return Catalog.JSON.nullNode();
        if (r.size() == 1) return literal(r.get(0));
        ArrayNode a = Catalog.JSON.createArrayNode();
        r.forEach(x -> a.add(literal(x)));
        return a;
    }

    static JsonNode literal(Element v) {
        var n = Catalog.JSON.getNodeFactory();
        if (v instanceof LiteralBoolean b) return n.booleanNode(b.isValue());
        if (v instanceof LiteralInteger i) return n.numberNode(i.getValue());
        if (v instanceof LiteralRational r) return n.numberNode(r.getValue());
        if (v instanceof LiteralString s) return n.textNode(s.getValue());
        if (v instanceof LiteralInfinity) return n.textNode("*");
        if (v instanceof Expression x) return n.textNode(expr(x));
        return n.textNode(qn(v));
    }

    /** For constraints (and requirements): the truth value of the result expression, or null if not a constraint. */
    static Boolean constraint(Element c) {
        Expression body = null;
        for (Relationship r : c.getOwnedRelationship()) {
            if (r instanceof ResultExpressionMembership rm) body = rm.getOwnedResultExpression();
        }
        if (body == null || !kind(c).startsWith("Constraint") && !kind(c).startsWith("AssertConstraint")) return null;
        try {
            Element ctx = c instanceof Feature f && f.getOwningType() != null ? f.getOwningType() : c;
            List<Element> r = ModelLevelExpressionEvaluatorEngine.evaluate(body, ctx);
            return r.size() == 1 && r.get(0) instanceof LiteralBoolean b ? b.isValue() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Values of every attribute with an expression and the result of every constraint below root. */
    static ObjectNode evaluateAll(Element root) {
        ObjectNode o = Catalog.JSON.createObjectNode();
        ArrayNode attrs = o.putArray("attributes"), cons = o.putArray("constraints");
        int pass = 0, fail = 0, unknown = 0;
        List<Element> all = new ArrayList<>();
        walkMembers(root, all::add);
        for (Element e : all) {
            if (e instanceof Feature f && !(e instanceof Expression) && e.getDeclaredName() != null
                    && !(e.getOwner() instanceof Expression) && valueExpression(f) != null) {
                attrs.add(ref(e).put("expression", expr(valueExpression(f))).set("value", evaluate(f)));
            }
            String k = kind(e);
            if (k.equals("ConstraintUsage") || k.equals("AssertConstraintUsage")) {
                Boolean b = constraint(e);
                ObjectNode row = ref(e).put("expression", constraintText(e));
                if (b == null) { row.putNull("satisfied"); unknown++; } else { row.put("satisfied", b); if (b) pass++; else fail++; }
                cons.add(row);
            }
        }
        o.putObject("summary").put("constraintsPassed", pass).put("constraintsFailed", fail).put("constraintsNotEvaluable", unknown);
        return o;
    }

    private static String constraintText(Element c) {
        for (Relationship r : c.getOwnedRelationship()) {
            if (r instanceof ResultExpressionMembership rm && rm.getOwnedResultExpression() != null) return expr(rm.getOwnedResultExpression());
        }
        return null;
    }

    // ------------------------------------------------------------------ relationships

    /** Relationships that point at or start from e anywhere in the project's own model. */
    static ObjectNode relationships(Project p, Element e) {
        ObjectNode o = Catalog.JSON.createObjectNode();
        ArrayNode owned = o.putArray("owned"), in = o.putArray("incoming");
        for (Relationship r : e.getOwnedRelationship()) {
            if (r instanceof Membership && !(r instanceof FeatureValue) || r.isImplied()) continue;
            owned.add(relJson(r));
        }
        for (Namespace root : roots(p)) {
            walkAll(root, x -> {
                if (x instanceof Relationship r && !(r instanceof Membership) && r.getOwningRelatedElement() != e
                        && r.getTarget().contains(e)) in.add(relJson(r));
                if (x instanceof SatisfyRequirementUsage s && s.getSatisfiedRequirement() == e) in.add(ref(s).put("satisfiedBy",
                        s.getSatisfyingFeature() == null ? null : qn(s.getSatisfyingFeature())));
            });
        }
        return o;
    }

    static ObjectNode relJson(Relationship r) {
        ObjectNode o = ref(r);
        ArrayNode s = o.putArray("source"), t = o.putArray("target");
        r.getSource().forEach(x -> s.add(qn(x)));
        r.getTarget().forEach(x -> t.add(qn(x)));
        return o;
    }

    // ------------------------------------------------------------------ textual export

    /** SysML text plus everything that could not be written exactly (so callers never mistake a partial export for a full one). */
    record Export(String text, List<String> gaps, int syntheticNames) {}

    /** Per-export state: names given to unnamed control nodes so successions can refer to them, and the gaps found. */
    private static final class Ser {
        final Map<Element, String> synthetic = new HashMap<>();
        final List<String> gaps = new ArrayList<>();
        final Map<String, Integer> counters = new HashMap<>();
    }

    private static final ThreadLocal<Ser> SER = new ThreadLocal<>();

    static Export export(List<? extends Element> elements) {
        Ser s = new Ser();
        SER.set(s);
        try {
            StringBuilder sb = new StringBuilder();
            for (Element e : elements) ser(e, 0, sb);
            return new Export(sb.toString(), s.gaps, s.synthetic.size());
        } finally {
            SER.remove();
        }
    }

    static String text(Element e) { return export(List.of(e)).text(); }

    private static void gap(Element e, String what) {
        Ser s = SER.get();
        if (s != null && s.gaps.size() < 200) s.gaps.add(what + " at " + (e == null ? "?" : qn(e) + " (" + kind(e) + ")"));
    }

    private static boolean isControlNode(Element e) {
        String k = kind(e);
        return k.equals("DecisionNode") || k.equals("MergeNode") || k.equals("ForkNode") || k.equals("JoinNode");
    }

    /** Name used in text: the declared name, or a generated one for unnamed control nodes. */
    private static String declared(Element e) {
        if (e.getDeclaredName() != null) return name(e.getDeclaredName());
        Ser s = SER.get();
        if (s != null && isControlNode(e)) {
            return s.synthetic.computeIfAbsent(e, x -> {
                String kw = keyword(x);
                return "_" + kw + s.counters.merge(kw, 1, Integer::sum);
            });
        }
        return null;
    }

    /** Prefix metadata (#keyword) on an element, written before its keyword. */
    private static List<String> prefixes(Element e) {
        List<String> out = new ArrayList<>();
        for (Relationship r : e.getOwnedRelationship()) {
            if (r instanceof OwningMembership && !(r instanceof FeatureMembership) && r.getOwnedRelatedElement().size() == 1
                    && r.getOwnedRelatedElement().get(0) instanceof MetadataUsage mu && mu.getDeclaredName() == null) {
                Element md = mu.getMetadataDefinition();
                if (md == null) continue;
                out.add("#" + (md.getDeclaredShortName() != null ? name(md.getDeclaredShortName()) : qnText(md)));
            }
        }
        return out;
    }

    private static boolean isPrefixMetadata(Relationship r) {
        return r instanceof OwningMembership && !(r instanceof FeatureMembership) && r.getOwnedRelatedElement().size() == 1
                && r.getOwnedRelatedElement().get(0) instanceof MetadataUsage mu && mu.getDeclaredName() == null
                && mu.getMetadataDefinition() != null;
    }

    private static boolean inState(Element e) {
        Element o = e.getOwner();
        return o != null && kind(o).contains("State");
    }

    private static void ser(Element e, int ind, StringBuilder sb) {
        String pad = "    ".repeat(ind);
        if (e instanceof Documentation d) {
            sb.append(pad).append("doc /* ").append(String.valueOf(d.getBody()).trim()).append(" */\n");
            return;
        }
        if (e instanceof Comment c) {
            sb.append(pad).append("comment /* ").append(String.valueOf(c.getBody()).trim()).append(" */\n");
            return;
        }
        if (e instanceof Namespace ns && !(e instanceof Type)) {
            if (e.getOwner() == null && e.getDeclaredName() == null) { body(ns, ind, sb); return; }
            sb.append(pad).append("package ").append(name(e.getDeclaredName())).append(" {\n");
            body(ns, ind + 1, sb);
            sb.append(pad).append("}\n");
            return;
        }
        if (!(e instanceof Type t)) {
            gap(e, "element kind not exported");
            sb.append(pad).append("// not exported: ").append(kind(e)).append(' ').append(Objects.toString(e.getDeclaredName(), "")).append('\n');
            return;
        }
        String k = kind(e);
        StringBuilder h = new StringBuilder(pad);
        if (t instanceof Feature f && f.getDirection() != null && !f.isEnd()) h.append(f.getDirection().getName()).append(' ');
        if (t.isAbstract() && !(t instanceof Feature) && !k.equals("EnumerationDefinition")) h.append("abstract ");
        for (String px : prefixes(e)) h.append(px).append(' ');
        if (e instanceof TransitionUsage tu) {
            // Inside states this is a transition; inside actions the same element is written as a guarded succession.
            boolean state = inState(e);
            h.append(state ? "transition" : "succession");
            if (e.getDeclaredName() != null) h.append(' ').append(name(e.getDeclaredName()));
            if (tu.getSource() != null) h.append(" first ").append(refName(tu.getSource(), e));
            for (AcceptActionUsage a : tu.getTriggerAction()) {
                Feature payload = a.getPayloadParameter();
                String pt = payload == null || payload.getType().isEmpty() ? null : typeRef(payload.getType().get(0));
                h.append(" accept ").append(pt != null ? pt : payload != null && payload.getName() != null ? name(payload.getName()) : "");
            }
            for (Expression g : tu.getGuardExpression()) h.append(" if ").append(expr(g));
            for (ActionUsage a : tu.getEffectAction()) {
                boolean owned = a.getOwner() == tu || a.getOwningRelationship() != null && a.getOwningRelationship().getOwningRelatedElement() == tu;
                h.append(" do ").append(owned ? "action" : "").append(owned && a.getDeclaredName() != null ? " " : "")
                        .append(a.getDeclaredName() != null ? name(a.getDeclaredName()) : owned ? "" : "action");
            }
            if (tu.getTarget() != null) h.append(" then ").append(refName(tu.getTarget(), e));
            sb.append(h).append(";\n");
            return;
        }
        if (e instanceof SatisfyRequirementUsage s) {
            h.append("satisfy ").append(s.getSatisfiedRequirement() == null ? "?" : qnText(s.getSatisfiedRequirement()));
            if (s.getSatisfyingFeature() != null) h.append(" by ").append(refName(s.getSatisfyingFeature(), e));
            sb.append(h).append(";\n");
            return;
        }
        String connector = connectorText(t, k);
        String payload = payloadType(t);
        String nm = declared(e);
        // perform / exhibit / include: either a reference to an existing behavior or a new one ("perform action x")
        String performed = null;
        if (k.equals("PerformActionUsage") || k.equals("ExhibitStateUsage") || k.equals("IncludeUseCaseUsage")) {
            performed = referenced(t, e);
            String kw = keyword(e);
            String own = k.equals("PerformActionUsage") ? "action" : k.equals("ExhibitStateUsage") ? "state" : "use case";
            if (performed != null) { h.append(kw).append(' ').append(performed); nm = null; }
            else h.append(kw).append(' ').append(own);
        }
        if (connector != null && nm == null && t instanceof Feature cf && cf.getOwnedTyping().isEmpty()) {
            if (k.endsWith("FlowUsage")) h.append(keyword(e)).append(payload == null ? "" : " of " + payload).append(' ');
            else if (k.equals("InterfaceUsage")) h.append("interface ");
            h.append(connector);
        } else {
            String kw = performed != null || k.equals("PerformActionUsage") || k.equals("ExhibitStateUsage") || k.equals("IncludeUseCaseUsage") ? "" : keyword(e);
            h.append(kw);
            if (e.getDeclaredShortName() != null) h.append(kw.isEmpty() ? "<" : " <").append(name(e.getDeclaredShortName())).append('>');
            if (nm != null) h.append(h.toString().isBlank() || h.charAt(h.length() - 1) == ' ' ? "" : " ").append(nm);
            if (t instanceof Feature f) {
                List<String> types = new ArrayList<>();
                for (FeatureTyping ft : f.getOwnedTyping()) if (ft.getType() != null) types.add(typeRef(ft.getType()));
                if (!types.isEmpty()) h.append(" : ").append(String.join(", ", types));
            }
            List<String> sub = new ArrayList<>(), red = new ArrayList<>();
            String refd = performed == null ? referenced(t, e) : null;
            for (Specialization s : t.getOwnedSpecialization()) {
                if (s instanceof FeatureTyping || s instanceof ReferenceSubsetting) continue;
                if (s.getGeneral() == null || s.isImplied()) continue;
                if (s instanceof Redefinition rd) red.add(refName(rd.getRedefinedFeature(), e));
                else if (s.getGeneral() instanceof Feature gf) {
                    // A redefinition is also a subsetting of the same feature; do not write it twice.
                    String n = refName(gf, e);
                    if (!red.contains(n)) sub.add(n);
                } else sub.add(typeRef(s.getGeneral()));
            }
            sub.removeAll(red);
            if (!sub.isEmpty()) h.append(" :> ").append(String.join(", ", sub));
            if (refd != null) h.append(" ::> ").append(refd);
            if (!red.isEmpty()) h.append(" :>> ").append(String.join(", ", red));
            if (t instanceof Feature f) {
                String m = multiplicity(f);
                if (m != null) h.append(" [").append(m).append(']');
                for (Relationship r : f.getOwnedRelationship()) {
                    if (r instanceof FeatureValue fv && fv.getValue() != null) {
                        h.append(fv.isDefault() ? " default = " : fv.isInitial() ? " := " : " = ").append(expr(fv.getValue()));
                    }
                }
            }
            if (connector != null) {
                if (k.endsWith("FlowUsage") && payload != null) h.append(" of ").append(payload);
                h.append(' ').append(connector);
            }
        }
        StringBuilder b = new StringBuilder();
        String ce = constraintText(e);
        if (ce != null) b.append(pad).append("    ").append(ce).append('\n');
        if (e instanceof Namespace ns) body(ns, ind + 1, b);
        if (b.length() == 0) sb.append(h).append(";\n");
        else sb.append(h).append(" {\n").append(b).append(pad).append("}\n");
    }

    /** Target of an owned reference subsetting (::>), as written in ctx, or null. */
    private static String referenced(Type t, Element ctx) {
        for (Relationship r : t.getOwnedRelationship()) {
            if (r instanceof ReferenceSubsetting rs && !rs.isImplied() && rs.getReferencedFeature() != null) return refName(rs.getReferencedFeature(), ctx);
        }
        return null;
    }

    private static String payloadType(Type t) {
        for (Feature f : t.getOwnedFeature()) {
            if (f instanceof PayloadFeature && !f.getType().isEmpty()) return typeRef(f.getType().get(0));
        }
        return null;
    }

    private static void body(Namespace ns, int ind, StringBuilder sb) {
        String pad = "    ".repeat(ind);
        for (Relationship r : ns.getOwnedRelationship()) {
            if (isPrefixMetadata(r)) continue; // written as #keyword in the header
            if (r instanceof NamespaceImport ni && ni.getImportedNamespace() != null) {
                sb.append(pad).append("import ").append(qnText(ni.getImportedNamespace())).append("::*;\n");
            } else if (r instanceof ElementFilterMembership fm) {
                if (fm.getCondition() != null) sb.append(pad).append("filter ").append(expr(fm.getCondition())).append(";\n");
            } else if (r instanceof StateSubactionMembership sm) {
                ActionUsage a = sm.getAction();
                String kw = sm.getKind().getName().toLowerCase(Locale.ROOT);
                if (a == null || a.getDeclaredName() == null && a.getOwnedMember().stream().noneMatch(x -> !internal(x))) {
                    sb.append(pad).append(kw).append(";\n");
                } else {
                    StringBuilder sub = new StringBuilder();
                    ser(a, ind, sub);
                    String line = sub.toString().replaceFirst("^(\\s*)perform action ", "$1action ").replaceFirst("^(\\s*)perform ", "$1");
                    sb.append(line.replaceFirst("^(\\s*)", "$1" + kw + " "));
                }
            } else if (r instanceof Membership m && m.getMemberElement() != null && "SuccessionAsUsage".equals(kind(m.getMemberElement()))
                    && entrySuccessionTarget(m.getMemberElement()) != null) {
                sb.append(pad).append("then ").append(entrySuccessionTarget(m.getMemberElement())).append(";\n");
            } else if (r instanceof ResultExpressionMembership || r instanceof FeatureValue) {
                // written as the body expression / value
            } else if (r instanceof Membership m) {
                Element el = m.getMemberElement();
                if (el == null || r.getOwningRelatedElement() != ns) continue;
                // implicit parts: multiplicities, conjugated port definitions, flow payloads, connector ends
                if (el instanceof Multiplicity || el instanceof ConjugatedPortDefinition || el instanceof PayloadFeature) continue;
                if (el instanceof Feature f && f.isEnd() && connectorText(ns, kind(ns)) != null) continue;
                if (r.getOwnedRelatedElement().contains(el)) ser(el, ind, sb);
            }
        }
    }

    /** For "entry; then s;" — the target of a succession that starts at the state's entry action, else null. */
    private static String entrySuccessionTarget(Element succession) {
        List<String> ends = new ArrayList<>();
        for (Feature f : ((Type) succession).getOwnedFeature()) {
            if (!f.isEnd()) continue;
            for (Relationship r : f.getOwnedRelationship()) if (r instanceof ReferenceSubsetting rs) ends.add(featureName(rs.getReferencedFeature()));
        }
        if (ends.size() != 2) return null;
        if ("entryAction".equals(ends.get(0))) return ends.get(1);
        Element owner = succession.getOwner();
        if (owner instanceof Namespace ns) {
            for (Relationship r : ns.getOwnedRelationship()) {
                if (r instanceof StateSubactionMembership sm && sm.getKind() == com.dassault_systemes.modeler.sysml.model.sysml.StateSubactionKind.ENTRY
                        && sm.getAction() != null && ends.get(0).equals(name(sm.getAction().getDeclaredName()))) return ends.get(1);
            }
        }
        return null;
    }

    private static String connectorText(Element t, String k) {
        if (!(t instanceof Type type)) return null;
        List<String> ends = new ArrayList<>();
        for (Feature f : type.getOwnedFeature()) {
            if (!f.isEnd()) continue;
            String n = null;
            for (Relationship r : f.getOwnedRelationship()) if (r instanceof ReferenceSubsetting rs) n = refName(rs.getReferencedFeature(), t);
            ends.add(n == null ? "?" : n);
        }
        if (ends.size() != 2) return null;
        String a = ends.get(0), b = ends.get(1);
        return switch (k) {
            case "ConnectionUsage", "InterfaceUsage" -> "connect " + a + " to " + b;
            case "BindingConnectorAsUsage" -> "bind " + a + " = " + b;
            case "SuccessionAsUsage" -> "first " + a + " then " + b;
            case "FlowUsage", "SuccessionFlowUsage" -> "from " + a + " to " + b;
            case "AllocationUsage" -> "allocate " + a + " to " + b;
            default -> null;
        };
    }

    private static String featureName(Feature f) {
        if (f == null) return "?";
        List<Feature> chain = f.getChainingFeature();
        if (chain != null && !chain.isEmpty()) {
            List<String> s = new ArrayList<>();
            for (Feature c : chain) s.add(featureName(c));
            return String.join(".", s);
        }
        String d = declared(f);
        if (d != null) return d;
        if (f.getName() != null) return name(f.getName());
        gap(f, "reference to an unnamed feature");
        return "?";
    }

    /** Name of a referenced feature as written in ctx: the plain name when it resolves there, else its qualified name. */
    private static String refName(Feature f, Element ctx) {
        if (f == null) return "?";
        List<Feature> chain = f.getChainingFeature();
        if (chain != null && !chain.isEmpty()) {
            List<String> s = new ArrayList<>();
            for (int i = 0; i < chain.size(); i++) s.add(i == 0 ? refName(chain.get(i), ctx) : featureName(chain.get(i)));
            return String.join(".", s);
        }
        if (f.getDeclaredName() == null && declared(f) != null) return declared(f);
        String n = f.getName();
        if (n == null) return featureName(f);
        if (resolvesTo(ctx, name(n), f)) return name(n);
        String q = qnText(f);
        return q.isEmpty() ? name(n) : q;
    }

    private static boolean resolvesTo(Element ctx, String text, Element target) {
        Namespace ns = ctx instanceof Namespace n ? n : ctx == null ? null : ctx.getOwningNamespace();
        if (ns == null) return false;
        try {
            Membership m = ns.resolve(text);
            return m != null && m.getMemberElement() == target;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String typeRef(Type t) {
        if (t instanceof ConjugatedPortDefinition c && c.getOriginalPortDefinition() != null) return "~" + qnText(c.getOriginalPortDefinition());
        String q = qnText(t);
        if (!q.isEmpty()) return q;
        if (t instanceof Feature f) return featureName(f);
        gap(t, "reference to an unnamed type");
        return "?";
    }

    /** Type named by an expression's result parameter (used by meta / as). */
    private static String resultType(Expression x) {
        Feature r = x.getResult();
        if (r != null) for (FeatureTyping ft : r.getOwnedTyping()) if (ft.getType() != null) return typeRef(ft.getType());
        return null;
    }

    /** SysML text for an expression; anything not understood is recorded as a gap. */
    static String expr(Expression x) {
        if (x == null) return "";
        if (x instanceof NullExpression) return "null";
        if (x instanceof LiteralBoolean b) return String.valueOf(b.isValue());
        if (x instanceof LiteralInteger i) return String.valueOf(i.getValue());
        if (x instanceof LiteralRational r) return String.valueOf(r.getValue());
        if (x instanceof LiteralString s) return "\"" + s.getValue().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        if (x instanceof LiteralInfinity) return "*";
        if (x instanceof MetadataAccessExpression ma) {
            Element r = ma.getReferencedElement();
            return r == null ? "?" : r instanceof Feature f ? featureName(f) : qnText(r);
        }
        if (x instanceof FeatureReferenceExpression fr) {
            // "a and b": the second operand is a reference to an unnamed body expression
            if (fr.getReferent() instanceof Expression body && body.getDeclaredName() == null) {
                for (Relationship r : body.getOwnedRelationship()) {
                    if (r instanceof ResultExpressionMembership rm && rm.getOwnedResultExpression() != null) return expr(rm.getOwnedResultExpression());
                }
                return expr(body);
            }
            return refName(fr.getReferent(), fr);
        }
        List<String> args = new ArrayList<>();
        for (Feature p : x.getOwnedFeature()) {
            Expression v = valueExpression(p);
            if (v != null) args.add(sub(v));
        }
        if (x instanceof FeatureChainExpression fc) {
            return (args.isEmpty() ? "?" : args.get(0)) + "." + featureName(fc.getTargetFeature());
        }
        if (x instanceof OperatorExpression op) {
            String o = op.getOperator();
            if ("[".equals(o) && args.size() == 2) return args.get(0) + " [" + args.get(1) + "]";
            if ("if".equals(o) && args.size() == 3) return "if " + args.get(0) + " ? " + args.get(1) + " else " + args.get(2);
            if ("#".equals(o) && args.size() == 2) return args.get(0) + "#(" + args.get(1) + ")";
            if (("meta".equals(o) || "as".equals(o)) && args.size() == 1 && resultType(x) != null) return args.get(0) + " " + o + " " + resultType(x);
            if (args.size() == 1) return o + " " + args.get(0);
            if (args.size() == 2) return args.get(0) + " " + o + " " + args.get(1);
            gap(x, "operator '" + o + "' with " + args.size() + " operands");
            return o + "(" + String.join(", ", args) + ")";
        }
        if (x instanceof InvocationExpression inv) {
            List<Type> types = inv.getType();
            String fn = types.isEmpty() ? "?" : typeRef(types.get(0));
            return fn + "(" + String.join(", ", args) + ")";
        }
        gap(x, "expression kind not exported");
        return "null /* not exported: " + kind(x) + " */";
    }

    private static String sub(Expression v) {
        String s = expr(v);
        return v instanceof OperatorExpression op && !(v instanceof FeatureChainExpression) && !"[".equals(op.getOperator()) ? "(" + s + ")" : s;
    }
}
