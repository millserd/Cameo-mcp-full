package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.export.image.ImageExporter;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.jmi.helpers.CoreHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.cameomcp.plugin.tools.Tool.Mode.*;

/**
 * Methodology packs (package skeletons plus conformance checks), review packets and text proofing.
 * Works on SysML v1 (UML) and SysML v2 projects; v2 access goes through V2Items so this class loads without v2.
 */
final class MethodTools {

    private MethodTools() {}

    /** A model element seen the same way for v1 and v2. */
    record Item(String id, String name, String kind, String keyword, String doc, String path, boolean definition) {
        ObjectNode ref() {
            ObjectNode o = Catalog.JSON.createObjectNode().put("id", id).put("name", name).put("kind", kind);
            if (path != null) o.put("path", path);
            return o;
        }
    }

    // ------------------------------------------------------------------ packs

    record Section(String name, String purpose, List<String> expects) {}

    record Pack(String id, String title, String description, List<Section> sections) {}

    static final List<Pack> PACKS = List.of(
            new Pack("magicgrid", "MagicGrid", "Dassault Systèmes' MagicGrid framework: problem domain (black box and white box), "
                    + "solution domain and verification, organized by requirements, behavior, structure and parameters.", List.of(
                    new Section("1 Problem Domain", "Stakeholder view of the problem, before any solution is chosen", List.of()),
                    new Section("1 Problem Domain::Stakeholder Needs", "Needs captured from stakeholders", List.of("requirement")),
                    new Section("1 Problem Domain::Use Cases", "System use cases and their actors", List.of("use case")),
                    new Section("1 Problem Domain::System Context", "System of interest and the external systems it interacts with", List.of("part")),
                    new Section("1 Problem Domain::Measures of Effectiveness", "MoEs the solution is judged by", List.of("attribute")),
                    new Section("1 Problem Domain::Functional Analysis", "White-box functions derived from use cases", List.of("action")),
                    new Section("2 Solution Domain", "The chosen solution architecture", List.of()),
                    new Section("2 Solution Domain::System Requirements", "Requirements the solution must meet", List.of("requirement")),
                    new Section("2 Solution Domain::System Structure", "Subsystems, components, ports and interfaces", List.of("part", "port", "interface")),
                    new Section("2 Solution Domain::System Behavior", "Activities and state machines of the solution", List.of("action", "state")),
                    new Section("2 Solution Domain::System Parameters", "Constraints and calculations for analysis", List.of("constraint", "calc")),
                    new Section("3 Verification", "Test cases and analysis that verify the requirements", List.of("verification", "analysis")))),
            new Pack("sysml2-basic", "SysML v2 starter", "A small, flat layout for SysML v2 models.", List.of(
                    new Section("Requirements", "Requirement definitions and usages", List.of("requirement")),
                    new Section("Structure", "Part, port, item and interface definitions", List.of("part", "port")),
                    new Section("Behavior", "Actions and states", List.of("action", "state")),
                    new Section("Interfaces", "Interface and connection definitions", List.of("interface")),
                    new Section("Analysis", "Constraints, calculations and analysis cases", List.of("constraint", "calc")),
                    new Section("Verification", "Verification cases", List.of("verification")))),
            new Pack("oosem", "OOSEM", "INCOSE Object-Oriented Systems Engineering Method: from mission analysis through logical "
                    + "and physical architecture.", List.of(
                    new Section("Mission Analysis", "Enterprise context, mission requirements and MoEs", List.of("requirement", "part")),
                    new Section("Black Box System", "System as a black box: requirements, use cases, interfaces", List.of("requirement", "use case", "interface")),
                    new Section("Logical Architecture", "Logical components and their interactions", List.of("part", "action")),
                    new Section("Physical Architecture", "Hardware, software, data and operator components allocated from logical", List.of("part", "allocation")),
                    new Section("Analysis", "Parametric and trade-study models", List.of("constraint", "calc", "analysis")),
                    new Section("Verification", "Verification cases and traceability", List.of("verification")))));

    static Pack pack(String id) {
        for (Pack p : PACKS) if (p.id().equalsIgnoreCase(id) || p.title().equalsIgnoreCase(id)) return p;
        throw new ToolError("Unknown methodology pack '" + id + "'. Available: " + PACKS.stream().map(Pack::id).toList());
    }

    static boolean v2(Project p) { return StudyTools.v2(p); }

    static void register(Map<String, Tool> m) {
        Tools.add(m, "cameo_list_methodology_packs", READ, c -> {
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (Pack p : PACKS) {
                ObjectNode o = a.addObject().put("id", p.id()).put("title", p.title()).put("description", p.description());
                ArrayNode s = o.putArray("packages");
                for (Section sec : p.sections()) {
                    ObjectNode so = s.addObject().put("path", sec.name()).put("purpose", sec.purpose());
                    ArrayNode ex = so.putArray("expects");
                    sec.expects().forEach(ex::add);
                }
            }
            return Catalog.JSON.createObjectNode().set("packs", a);
        });

        Tools.add(m, "cameo_apply_methodology_pack", APP, c -> {
            Project p = c.project();
            Pack pack = pack(c.str("pack"));
            String rootName = c.str("rootName", pack.title());
            if (v2(p)) return V2Items.applyPack(p, c.opt("ownerId"), rootName, pack);
            Element owner = c.has("ownerId") ? c.el("ownerId") : c.model();
            return Tools.inSession(p, "MCP: apply " + pack.title(), () -> {
                Element root = Model.create(p, "Package", owner, rootName);
                CoreHelper.setComment(root, pack.description());
                Map<String, Element> made = new HashMap<>();
                ArrayNode a = Catalog.JSON.createArrayNode();
                for (Section s : pack.sections()) {
                    int cut = s.name().lastIndexOf("::");
                    Element parent = cut < 0 ? root : made.get(s.name().substring(0, cut));
                    Element pkg = Model.create(p, "Package", parent, cut < 0 ? s.name() : s.name().substring(cut + 2));
                    CoreHelper.setComment(pkg, s.purpose());
                    made.put(s.name(), pkg);
                    a.add(Model.ref(pkg).put("path", s.name()));
                }
                ObjectNode o = Catalog.JSON.createObjectNode();
                o.set("root", Model.ref(root));
                return o.set("packages", a);
            });
        });

        Tools.add(m, "cameo_check_methodology_conformance", READ, c -> {
            Project p = c.project();
            Pack pack = pack(c.str("pack"));
            Map<String, List<Item>> bySection = new LinkedHashMap<>();
            String root = c.opt("rootId");
            for (Section s : pack.sections()) {
                List<Item> items = v2(p) ? V2Items.sectionItems(p, root, pack.title(), s.name()) : v1SectionItems(c, root, pack.title(), s.name());
                bySection.put(s.name(), items);
            }
            ArrayNode rows = Catalog.JSON.createArrayNode();
            int ok = 0;
            for (Section s : pack.sections()) {
                List<Item> items = bySection.get(s.name());
                ObjectNode r = rows.addObject().put("package", s.name()).put("exists", items != null);
                if (items == null) { r.put("status", "missing"); continue; }
                ArrayNode missing = r.putArray("missingKinds");
                ObjectNode counts = r.putObject("counts");
                for (String kw : s.expects()) {
                    long n = items.stream().filter(i -> matchesKind(i, kw)).count();
                    counts.put(kw, n);
                    if (n == 0) missing.add(kw);
                }
                String status = missing.isEmpty() ? "ok" : "incomplete";
                if (status.equals("ok")) ok++;
                r.put("status", status).put("elements", items.size());
            }
            return Catalog.JSON.createObjectNode().put("pack", pack.id()).put("conformingPackages", ok)
                    .put("totalPackages", pack.sections().size())
                    .put("score", Math.round(100.0 * ok / pack.sections().size())).set("packages", rows);
        });

        // ---------------------------------------------------------------- proofing
        Tools.add(m, "cameo_proof_model_text", READ, c -> {
            Project p = c.project();
            List<Item> items = items(c, p, c.opt("scopeId"));
            return proof(items, c.i("limit", 500), c.b("checkNaming", true));
        });

        Tools.add(m, "cameo_apply_proofing_patch", APP, c -> {
            Project p = c.project();
            JsonNode patches = c.node("patches");
            if (!patches.isArray() || patches.isEmpty()) throw new ToolError("patches: give [{\"elementId\":..., \"field\":\"name|documentation\", \"value\":...}]");
            boolean dry = c.b("dryRun", false);
            ArrayNode done = Catalog.JSON.createArrayNode();
            Tools.Body<Object> apply = () -> {
                for (JsonNode pt : patches) {
                    String id = pt.path("elementId").asText(), field = pt.path("field").asText(), value = pt.path("value").asText();
                    if (!field.equals("name") && !field.equals("documentation")) throw new ToolError("field must be name or documentation");
                    String before = v2(p) ? V2Items.set(p, id, field, value, dry) : v1Set(c.find(id), field, value, dry);
                    done.addObject().put("elementId", id).put("field", field).put("before", before).put("after", value);
                }
                return null;
            };
            if (dry) apply.run(); else Tools.inSession(p, "MCP: apply proofing patch", apply);
            return Catalog.JSON.createObjectNode().put("applied", !dry).put("count", done.size()).set("changes", done);
        });

        // ---------------------------------------------------------------- review packet
        Tools.add(m, "cameo_generate_review_packet", APP, c -> {
            Project p = c.project();
            File dir = new File(c.str("directory"));
            dir.mkdirs();
            String scopeId = c.opt("scopeId");
            List<Item> items = items(c, p, scopeId);
            ObjectNode proof = proof(items, 1000, true);
            ObjectNode packet = Catalog.JSON.createObjectNode().put("project", p.getName())
                    .put("generated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .put("modelKind", v2(p) ? "SysML v2" : "UML/SysML v1");
            ObjectNode kinds = packet.putObject("elementsByKind");
            new TreeMap<>(countBy(items)).forEach(kinds::put);
            ArrayNode files = packet.putArray("files");
            ObjectNode validation, analysis = null;
            if (v2(p)) {
                validation = V2Items.validate(p, scopeId);
                analysis = V2Items.evaluate(p, scopeId);
                ObjectNode ex = V2Items.export(p, scopeId);
                File f = new File(dir, "model.sysml");
                Files.writeString(f.toPath(), ex.path("text").asText(), StandardCharsets.UTF_8);
                files.add(f.getName());
                ex.remove("text");
                packet.set("textExport", ex);
            } else {
                validation = v1Coverage(c, p, scopeId);
                File dd = new File(dir, "diagrams");
                int max = c.i("maxDiagrams", 50), n = 0;
                Element scope = scopeId != null ? c.find(scopeId) : null;
                for (DiagramPresentationElement d : p.getDiagrams()) {
                    if (n >= max) break;
                    if (scope != null && !DiagramTools.isInside(d.getDiagram(), scope)) continue;
                    dd.mkdirs();
                    File f = new File(dd, safeName(d.getName()) + "-" + (n + 1) + ".png");
                    d.ensureLoaded();
                    ImageExporter.export(d, ImageExporter.PNG, f, false, 96, 100);
                    if (f.isFile()) { files.add("diagrams/" + f.getName()); n++; }
                }
            }
            packet.set("validation", validation);
            if (analysis != null) packet.set("analysis", analysis);
            packet.set("proofing", proof.get("summary"));
            File json = new File(dir, "findings.json");
            ObjectNode findings = Catalog.JSON.createObjectNode();
            findings.set("validation", validation);
            if (analysis != null) findings.set("analysis", analysis);
            findings.set("proofing", proof);
            Catalog.JSON.writerWithDefaultPrettyPrinter().writeValue(json, findings);
            files.add(json.getName());
            File md = new File(dir, "review.md");
            Files.writeString(md.toPath(), markdown(packet, validation, analysis, proof), StandardCharsets.UTF_8);
            files.insert(0, md.getName());
            packet.put("directory", dir.getAbsolutePath());
            return packet;
        });
    }

    // ------------------------------------------------------------------ items

    static List<Item> items(Ctx c, Project p, String scopeId) {
        if (v2(p)) return V2Items.items(p, scopeId);
        List<Item> out = new ArrayList<>();
        Element scope = scopeId != null ? c.find(scopeId) : c.model();
        Model.walk(scope, e -> {
            if (!(e instanceof NamedElement ne) || e instanceof com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Comment) return;
            if (ne.getName() == null || ne.getName().isEmpty() && !(e instanceof Classifier)) return;
            out.add(new Item(e.getID(), ne.getName(), e.getHumanType(), v1Keyword(e), CoreHelper.getComment(e), ne.getQualifiedName(), e instanceof Classifier));
        });
        return out;
    }

    private static String v1Keyword(Element e) {
        List<Stereotype> st = StereotypesHelper.getStereotypes(e);
        String h = (st.isEmpty() ? e.getHumanType() : st.get(0).getName()).toLowerCase(Locale.ROOT);
        if (h.contains("requirement")) return "requirement";
        if (h.contains("use case") || h.equals("usecase")) return "use case";
        if (h.equals("block") || h.equals("part property") || h.equals("property")) return "part";
        if (h.contains("port")) return "port";
        if (h.contains("interface")) return "interface";
        if (h.contains("activity") || h.contains("action")) return "action";
        if (h.contains("state")) return "state";
        if (h.contains("constraint")) return "constraint";
        if (h.contains("value") || h.contains("measure")) return "attribute";
        if (h.contains("testcase") || h.contains("test case")) return "verification";
        if (h.contains("allocate")) return "allocation";
        return h;
    }

    static boolean matchesKind(Item i, String kw) {
        String k = i.keyword() == null ? "" : i.keyword();
        return k.equals(kw) || k.equals(kw + " def") || k.startsWith(kw + " ") || k.contains(kw);
    }

    private static List<Item> v1SectionItems(Ctx c, String rootId, String packTitle, String path) {
        Element root = rootId != null ? c.find(rootId) : null;
        if (root == null) {
            for (Element e : c.model().getOwnedElement()) if (e instanceof Package pk && packTitle.equals(pk.getName())) root = e;
            if (root == null) root = c.model();
        }
        Element cur = root;
        for (String seg : path.split("::")) {
            Element next = null;
            for (Element e : cur.getOwnedElement()) if (e instanceof NamedElement ne && seg.equals(ne.getName())) next = e;
            if (next == null) return null;
            cur = next;
        }
        List<Item> out = new ArrayList<>();
        Model.walk(cur, e -> {
            if (e instanceof NamedElement ne && e != null) out.add(new Item(e.getID(), ne.getName(), e.getHumanType(), v1Keyword(e), null, null, e instanceof Classifier));
        });
        return out;
    }

    private static String v1Set(Element e, String field, String value, boolean dry) {
        String before = field.equals("name") ? ((NamedElement) e).getName() : CoreHelper.getComment(e);
        if (!dry) {
            if (field.equals("name")) ((NamedElement) e).setName(value);
            else CoreHelper.setComment(e, value);
        }
        return before;
    }

    private static ObjectNode v1Coverage(Ctx c, Project p, String scopeId) {
        ObjectNode o = Catalog.JSON.createObjectNode();
        Stereotype req = Model.stereotype(p, "Requirement");
        if (req == null) return o.put("note", "No SysML Requirement stereotype in this project");
        Element scope = scopeId != null ? c.find(scopeId) : c.model();
        int total = 0, sat = 0, ver = 0;
        ArrayNode un = o.putArray("unsatisfiedRequirements");
        List<Element> reqs = new ArrayList<>();
        Model.walk(scope, e -> { if (StereotypesHelper.hasStereotypeOrDerived(e, req)) reqs.add(e); });
        for (Element r : reqs) {
            total++;
            boolean s = false, v = false;
            for (Element rel : Model.relationships(r, "in")) {
                for (Stereotype st : StereotypesHelper.getStereotypes(rel)) {
                    s |= st.getName().equals("Satisfy");
                    v |= st.getName().equals("Verify");
                }
            }
            if (s) sat++; else if (un.size() < 100) un.add(Model.ref(r));
            if (v) ver++;
        }
        return o.put("requirements", total).put("satisfied", sat).put("verified", ver);
    }

    private static String safeName(String s) { return s == null ? "diagram" : s.replaceAll("[^A-Za-z0-9._-]+", "_"); }

    static Map<String, Integer> countBy(List<Item> items) {
        Map<String, Integer> m = new HashMap<>();
        for (Item i : items) m.merge(i.kind(), 1, Integer::sum);
        return m;
    }

    // ------------------------------------------------------------------ proofing rules

    private static final Map<String, String> TYPOS = Map.ofEntries(
            Map.entry("teh", "the"), Map.entry("recieve", "receive"), Map.entry("seperate", "separate"),
            Map.entry("occured", "occurred"), Map.entry("untill", "until"), Map.entry("adress", "address"),
            Map.entry("enviroment", "environment"), Map.entry("requirment", "requirement"), Map.entry("perfomance", "performance"),
            Map.entry("maintainance", "maintenance"), Map.entry("accomodate", "accommodate"), Map.entry("definately", "definitely"),
            Map.entry("existance", "existence"), Map.entry("dependant", "dependent"), Map.entry("interupt", "interrupt"),
            Map.entry("comunication", "communication"), Map.entry("sucess", "success"), Map.entry("wich", "which"),
            Map.entry("lenght", "length"), Map.entry("heigth", "height"), Map.entry("throught", "through"));
    private static final Pattern WORD = Pattern.compile("\\b[\\p{L}']+\\b");
    private static final Pattern REPEAT = Pattern.compile("\\b(\\w+)\\s+\\1\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\b(TODO|TBD|TBC|FIXME|XXX)\\b|\\?\\?\\?");
    private static final Pattern DEFAULT_NAME = Pattern.compile("^(Untitled|unnamed|New ?\\w+|(Block|Part|Package|Class|Requirement|Activity|Action|State|part|attribute|port)\\d+)$");
    private static final Pattern UPPER_CAMEL = Pattern.compile("^[A-Z][A-Za-z0-9]*( [A-Z0-9][A-Za-z0-9]*)*$");
    private static final Pattern LOWER_CAMEL = Pattern.compile("^[a-z][A-Za-z0-9]*$");

    static ObjectNode proof(List<Item> items, int limit, boolean naming) {
        ArrayNode findings = Catalog.JSON.createArrayNode();
        ArrayNode patches = Catalog.JSON.createArrayNode();
        Map<String, Integer> counts = new TreeMap<>();
        for (Item i : items) {
            check(i, "name", i.name(), naming, findings, patches, counts, limit);
            if (i.doc() != null) check(i, "documentation", i.doc(), false, findings, patches, counts, limit);
            if (i.definition() && (i.doc() == null || i.doc().isBlank()) && i.name() != null
                    && (i.keyword().contains("requirement") || i.keyword().endsWith(" def") || i.keyword().equals("block"))) {
                hit(findings, counts, limit, i, "documentation", "missing-documentation", "info", "No documentation", null);
            }
        }
        ObjectNode o = Catalog.JSON.createObjectNode();
        ObjectNode sum = o.putObject("summary").put("elementsChecked", items.size())
                .put("findings", counts.values().stream().mapToInt(x -> x).sum()).put("autoFixable", patches.size());
        ObjectNode by = sum.putObject("byRule");
        counts.forEach(by::put);
        o.set("findings", findings);
        o.set("patchPlan", patches);
        o.put("howToApply", "Review patchPlan, then pass it (or a subset) as 'patches' to cameo_apply_proofing_patch.");
        return o;
    }

    private static void check(Item i, String field, String text, boolean naming, ArrayNode findings, ArrayNode patches,
                              Map<String, Integer> counts, int limit) {
        if (text == null) return;
        String fixed = text;
        if (!text.equals(text.strip())) { hit(findings, counts, limit, i, field, "surrounding-whitespace", "warning", "Leading or trailing whitespace", null); fixed = fixed.strip(); }
        if (fixed.contains("  ")) { hit(findings, counts, limit, i, field, "double-space", "info", "Repeated spaces", null); fixed = fixed.replaceAll(" {2,}", " "); }
        Matcher rep = REPEAT.matcher(fixed);
        if (rep.find() && !rep.group(1).matches("\\d+")) {
            hit(findings, counts, limit, i, field, "repeated-word", "warning", "Repeated word '" + rep.group(1) + "'", null);
            fixed = REPEAT.matcher(fixed).replaceAll("$1");
        }
        Matcher w = WORD.matcher(fixed);
        StringBuilder sb = new StringBuilder();
        boolean typo = false;
        while (w.find()) {
            String t = TYPOS.get(w.group().toLowerCase(Locale.ROOT));
            if (t != null) {
                typo = true;
                if (Character.isUpperCase(w.group().charAt(0))) t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
                hit(findings, counts, limit, i, field, "spelling", "warning", "'" + w.group() + "' should be '" + t + "'", null);
                w.appendReplacement(sb, t);
            }
        }
        w.appendTail(sb);
        if (typo) fixed = sb.toString();
        if (PLACEHOLDER.matcher(text).find()) hit(findings, counts, limit, i, field, "placeholder", "warning", "Contains a placeholder (TODO/TBD/FIXME)", null);
        if (field.equals("name")) {
            if (DEFAULT_NAME.matcher(text.strip()).matches()) hit(findings, counts, limit, i, field, "default-name", "warning", "Looks like a default name", null);
            if (naming && !text.isBlank()) {
                boolean def = i.definition();
                String n = fixed;
                if (def && !UPPER_CAMEL.matcher(n).matches() && n.matches("[A-Za-z0-9 _]+")) {
                    String s = toCamel(n, true);
                    hit(findings, counts, limit, i, field, "naming-convention", "info", "Definitions/types are usually UpperCamelCase", s);
                } else if (!def && i.keyword() != null && !i.keyword().equals("package") && !i.keyword().contains("requirement")
                        && i.kind().endsWith("Usage") && !LOWER_CAMEL.matcher(n).matches() && n.matches("[A-Za-z0-9 _]+")) {
                    hit(findings, counts, limit, i, field, "naming-convention", "info", "Usages/features are usually lowerCamelCase", toCamel(n, false));
                }
            }
        }
        if (!fixed.equals(text)) patches.addObject().put("elementId", i.id()).put("field", field).put("value", fixed).put("was", text);
    }

    private static String toCamel(String s, boolean upper) {
        StringBuilder b = new StringBuilder();
        boolean up = upper;
        for (char ch : s.toCharArray()) {
            if (ch == ' ' || ch == '_') { up = true; continue; }
            b.append(up ? Character.toUpperCase(ch) : b.length() == 0 && !upper ? Character.toLowerCase(ch) : ch);
            up = false;
        }
        return b.toString();
    }

    private static void hit(ArrayNode findings, Map<String, Integer> counts, int limit, Item i, String field, String rule,
                            String sev, String msg, String suggestion) {
        counts.merge(rule, 1, Integer::sum);
        if (findings.size() >= limit) return;
        ObjectNode f = i.ref().put("field", field).put("rule", rule).put("severity", sev).put("message", msg);
        if (suggestion != null) f.put("suggestion", suggestion);
        findings.add(f);
    }

    // ------------------------------------------------------------------ markdown

    private static String markdown(ObjectNode packet, ObjectNode validation, ObjectNode analysis, ObjectNode proof) {
        StringBuilder s = new StringBuilder();
        s.append("# Model review: ").append(packet.path("project").asText()).append("\n\n");
        s.append("Generated ").append(packet.path("generated").asText()).append(" · ").append(packet.path("modelKind").asText()).append("\n\n");
        s.append("## Contents\n\n| Kind | Count |\n|---|---:|\n");
        packet.path("elementsByKind").fields().forEachRemaining(e -> s.append("| ").append(e.getKey()).append(" | ").append(e.getValue().asInt()).append(" |\n"));
        s.append("\n## Validation\n\n");
        if (validation.has("byRule")) {
            s.append("Issues: ").append(validation.path("issueCount").asInt()).append("\n\n| Rule | Count |\n|---|---:|\n");
            validation.path("byRule").fields().forEachRemaining(e -> s.append("| ").append(e.getKey()).append(" | ").append(e.getValue().asInt()).append(" |\n"));
            s.append('\n');
            int n = 0;
            for (JsonNode i : validation.path("issues")) {
                if (n++ >= 25) { s.append("- …see findings.json\n"); break; }
                s.append("- **").append(i.path("severity").asText()).append("** ").append(i.path("qualifiedName").asText(i.path("name").asText()))
                        .append(": ").append(i.path("message").asText()).append('\n');
            }
        } else {
            s.append("Requirements: ").append(validation.path("requirements").asInt()).append(", satisfied: ")
                    .append(validation.path("satisfied").asInt()).append(", verified: ").append(validation.path("verified").asInt()).append('\n');
        }
        if (analysis != null) {
            s.append("\n## Parametric evaluation\n\n");
            JsonNode sum = analysis.path("summary");
            s.append("Constraints passed: ").append(sum.path("constraintsPassed").asInt()).append(", failed: ")
                    .append(sum.path("constraintsFailed").asInt()).append(", not evaluable: ").append(sum.path("constraintsNotEvaluable").asInt()).append("\n\n");
            for (JsonNode c : analysis.path("constraints")) {
                s.append("- ").append(c.path("satisfied").isNull() ? "❔" : c.path("satisfied").asBoolean() ? "✅" : "❌").append(' ')
                        .append(c.path("qualifiedName").asText()).append(" `").append(c.path("expression").asText()).append("`\n");
            }
            int n = 0;
            for (JsonNode a : analysis.path("attributes")) {
                if (n++ == 0) s.append("\n| Attribute | Expression | Value |\n|---|---|---|\n");
                s.append("| ").append(a.path("qualifiedName").asText()).append(" | `").append(a.path("expression").asText()).append("` | ")
                        .append(a.path("value").toString()).append(" |\n");
            }
        }
        s.append("\n## Text proofing\n\n");
        JsonNode ps = proof.path("summary");
        s.append("Findings: ").append(ps.path("findings").asInt()).append(" (auto-fixable: ").append(ps.path("autoFixable").asInt()).append(")\n\n");
        ps.path("byRule").fields().forEachRemaining(e -> s.append("- ").append(e.getKey()).append(": ").append(e.getValue().asInt()).append('\n'));
        JsonNode tx = packet.path("textExport");
        if (!tx.isMissingNode() && !tx.path("complete").asBoolean(true)) {
            s.append("\n## SysML text export\n\nmodel.sysml is incomplete; these parts could not be written exactly:\n\n");
            for (JsonNode g : tx.path("gaps")) s.append("- ").append(g.asText()).append('\n');
        }
        s.append("\n## Files\n\n");
        for (JsonNode f : packet.path("files")) s.append("- ").append(f.asText()).append('\n');
        return s.toString();
    }
}
