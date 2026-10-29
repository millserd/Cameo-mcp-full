package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.ci.persistence.IAttachedProject;
import com.nomagic.magicdraw.copypaste.CopyPasting;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.ProjectUtilities;
import com.nomagic.magicdraw.core.project.ProjectDescriptor;
import com.nomagic.magicdraw.core.project.ProjectDescriptorsFactory;
import com.nomagic.magicdraw.core.project.ProjectsManager;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.uml.ConvertElementInfo;
import com.nomagic.magicdraw.uml.Refactoring;
import com.nomagic.uml2.ext.jmi.helpers.CoreHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.commonbehaviors.mdbasicbehaviors.Behavior;
import com.nomagic.uml2.ext.magicdraw.commonbehaviors.mdbasicbehaviors.OpaqueBehavior;
import com.nomagic.uml2.ext.magicdraw.commonbehaviors.mdcommunications.*;
import com.nomagic.uml2.ext.magicdraw.commonbehaviors.mdsimpletime.TimeExpression;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.ext.magicdraw.statemachines.mdbehaviorstatemachines.State;
import com.nomagic.uml2.ext.magicdraw.statemachines.mdbehaviorstatemachines.Transition;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import static com.cameomcp.plugin.tools.Tool.Mode.*;

final class ModelTools {

    private ModelTools() {}

    /** The SysML v2 Community edition only opens SysML v2 projects. */
    static boolean communityMode() {
        String n = Application.runtime().getApplicationName();
        return n != null && n.contains("Community");
    }

    static void register(Map<String, Tool> m) {
        // ---------------------------------------------------------------- projects
        Tools.add(m, "cameo_get_project", READ, c -> {
            Project p = c.project();
            ObjectNode o = Catalog.JSON.createObjectNode().put("name", p.getName()).put("dirty", p.isDirty())
                    .put("remote", p.isRemote()).put("file", p.getFileName())
                    .put("location", String.valueOf(p.getPrimaryProject().getLocationURI()));
            o.put("kind", p.getPrimaryModel() == null ? "sysml2" : "uml");
            if (p.getPrimaryModel() != null) o.set("primaryModel", Model.ref(p.getPrimaryModel()));
            ArrayNode models = o.putArray("models");
            p.getModels().forEach(x -> models.add(Model.ref(x)));
            o.put("diagramCount", p.getDiagrams().size());
            return o;
        });

        Tools.add(m, "cameo_list_open_projects", READ, c -> {
            ProjectsManager pm = Application.getInstance().getProjectsManager();
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (Project p : pm.getProjects()) {
                a.addObject().put("name", p.getName()).put("id", p.getID()).put("active", p == pm.getActiveProject())
                        .put("dirty", p.isDirty()).put("remote", p.isRemote());
            }
            return Catalog.JSON.createObjectNode().set("projects", a);
        });

        Tools.add(m, "cameo_set_active_project", APP, c -> {
            Project p = openProject(c.str("project"));
            Application.getInstance().getProjectsManager().setActiveProject(p);
            return Catalog.JSON.createObjectNode().put("active", p.getName());
        });

        Tools.add(m, "cameo_create_new_project", APP, c -> {
            ProjectsManager pm = Application.getInstance().getProjectsManager();
            String kind = c.str("kind", "auto").toLowerCase(Locale.ROOT);
            String template = c.opt("templatePath");
            if (template == null && (kind.equals("sysml2") || kind.equals("auto") && communityMode())) {
                File t = new File(Application.environment().getInstallRoot(), "templates/SysML v2/SysML v2.mdszip");
                if (!t.isFile()) throw new ToolError("SysML v2 template not found at " + t + "; pass templatePath");
                template = t.getAbsolutePath();
            }
            Project p;
            if (template != null) {
                if (!new File(template).isFile()) throw new ToolError("No such template: " + template);
                p = pm.createProjectFromTemplate(template);
            } else {
                p = pm.createProject();
            }
            if (p == null) throw new ToolError("Cameo did not create a project");
            if (c.has("filePath")) {
                File f = new File(c.str("filePath"));
                if (!pm.saveProject(ProjectDescriptorsFactory.createLocalProjectDescriptor(p, f), true))
                    throw new ToolError("Project created but could not be saved to " + f);
            }
            ObjectNode o = Catalog.JSON.createObjectNode().put("name", p.getName())
                    .put("kind", p.getPrimaryModel() == null ? "sysml2" : "uml");
            if (template != null) o.put("template", template);
            if (p.getPrimaryModel() != null) o.put("primaryModelId", p.getPrimaryModel().getID());
            return o;
        });

        Tools.add(m, "cameo_open_project_file", APP, c -> {
            File f = new File(c.str("filePath"));
            if (!f.isFile()) throw new ToolError("No such file: " + f);
            ProjectsManager pm = Application.getInstance().getProjectsManager();
            pm.loadProject(ProjectDescriptorsFactory.createProjectDescriptor(f.toURI()), true);
            Project p = pm.getActiveProject();
            return Catalog.JSON.createObjectNode().put("opened", p == null ? null : p.getName());
        });

        Tools.add(m, "cameo_save_project", APP, c -> {
            Project p = c.project();
            ProjectsManager pm = Application.getInstance().getProjectsManager();
            ProjectDescriptor d;
            if (c.has("filePath")) d = ProjectDescriptorsFactory.createLocalProjectDescriptor(p, new File(c.str("filePath")));
            else {
                if (p.isRemote()) throw new ToolError("This is a Teamwork Cloud project; use cameo_commit_project to save it to the server");
                d = ProjectDescriptorsFactory.getDescriptorForProject(p);
                if (d == null || p.getFileName() == null) throw new ToolError("Project has never been saved; pass filePath");
            }
            if (!pm.saveProject(d, true)) throw new ToolError("Cameo reported that saving failed");
            return Catalog.JSON.createObjectNode().put("saved", p.getName()).put("file", p.getFileName());
        });

        Tools.add(m, "cameo_close_project", APP, c -> {
            Project p = c.has("project") ? openProject(c.str("project")) : c.project();
            String name = p.getName();
            if (p.isDirty() && !c.b("discardChanges", false))
                throw new ToolError("Project '" + name + "' has unsaved changes. Save it first, or pass discardChanges=true.");
            Application.getInstance().getProjectsManager().closeProjectNoSave(p);
            return Catalog.JSON.createObjectNode().put("closed", name);
        });

        Tools.add(m, "cameo_list_attached_modules", READ, c -> {
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (IAttachedProject ap : ProjectUtilities.getAttachedProjects(c.project().getPrimaryProject())) {
                a.addObject().put("name", ap.getName()).put("location", String.valueOf(ap.getLocationURI()));
            }
            ArrayNode shared = Catalog.JSON.createArrayNode();
            ProjectUtilities.getSharedPackages(c.project().getPrimaryProject()).forEach(pk -> shared.add(Model.ref(pk)));
            ObjectNode o = Catalog.JSON.createObjectNode();
            o.set("modules", a);
            o.set("sharedPackages", shared);
            return o;
        });

        // ---------------------------------------------------------------- reading
        Tools.add(m, "cameo_query_elements", READ, c -> {
            Project p = c.project();
            Element scope = c.has("scopeId") ? c.el("scopeId") : c.model();
            String type = c.opt("type");
            String stereo = c.opt("stereotype");
            String nameQ = c.opt("name");
            Pattern rx = nameQ == null ? null : Pattern.compile(c.b("regex", false) ? nameQ : Pattern.quote(nameQ), Pattern.CASE_INSENSITIVE);
            int limit = c.i("limit", 200);
            boolean recursive = c.b("recursive", true);
            ArrayNode a = Catalog.JSON.createArrayNode();
            int[] total = {0};
            List<Element> pool = new ArrayList<>();
            if (recursive) Model.walk(scope, pool::add);
            else pool.addAll(scope.getOwnedElement());
            for (Element e : pool) {
                if (e == scope && recursive) continue;
                if (type != null && !matchesType(e, type)) continue;
                if (stereo != null && StereotypesHelper.getStereotypes(e).stream().noneMatch(s -> stereo.equalsIgnoreCase(s.getName()))) continue;
                if (rx != null) {
                    String n = Model.name(e);
                    if (n == null || !rx.matcher(n).find()) continue;
                }
                if (total[0]++ < limit) a.add(Model.summary(e));
            }
            ObjectNode o = Catalog.JSON.createObjectNode().put("total", total[0]).put("returned", a.size());
            o.set("elements", a);
            return o;
        });

        Tools.add(m, "cameo_get_element", READ, c -> {
            Element e = c.el("elementId");
            ObjectNode o = Model.details(e);
            if (c.b("includeRelationships", false)) {
                ArrayNode r = o.putArray("relationships");
                Model.relationships(e, "both").forEach(x -> r.add(Model.relJson(x)));
            }
            return o;
        });

        Tools.add(m, "cameo_get_containment_tree", READ, c -> {
            Element root = c.has("rootId") ? c.el("rootId") : c.model();
            int depth = c.i("depth", 2);
            int[] budget = {c.i("limit", 500)};
            return tree(root, depth, budget);
        });

        Tools.add(m, "cameo_get_relationships", READ, c -> {
            Element e = c.el("elementId");
            String type = c.opt("type");
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (Element r : Model.relationships(e, c.str("direction", "both"))) {
                if (type == null || matchesType(r, type)) a.add(Model.relJson(r));
            }
            return Catalog.JSON.createObjectNode().set("relationships", a);
        });

        // ---------------------------------------------------------------- writing
        Tools.add(m, "cameo_create_element", WRITE, c -> {
            Project p = c.project();
            Element parent = c.has("parentId") ? c.el("parentId") : c.model();
            Element e = Model.create(p, c.str("type"), parent, c.opt("name"));
            applyCommon(p, e, c.args);
            return Model.summary(e);
        });

        Tools.add(m, "cameo_create_elements", WRITE, c -> {
            Project p = c.project();
            Map<String, Element> refs = new LinkedHashMap<>();
            ArrayNode created = Catalog.JSON.createArrayNode();
            for (JsonNode spec : c.node("elements")) {
                Element parent = spec.has("parent") ? resolve(c, refs, spec.get("parent").asText()) : c.model();
                Element e = Model.create(p, need(spec, "type"), parent, spec.path("name").asText(null));
                applyCommon(p, e, spec);
                if (spec.has("ref")) refs.put(spec.get("ref").asText(), e);
                created.add(Model.ref(e).put("ref", spec.path("ref").asText(null)));
            }
            ArrayNode rels = Catalog.JSON.createArrayNode();
            for (JsonNode spec : c.node("relationships")) {
                Element r = Model.relate(p, need(spec, "type"), resolve(c, refs, need(spec, "source")),
                        resolve(c, refs, need(spec, "target")), spec.path("name").asText(null));
                applyCommon(p, r, spec);
                rels.add(Model.relJson(r));
            }
            ObjectNode o = Catalog.JSON.createObjectNode();
            o.set("elements", created);
            o.set("relationships", rels);
            return o;
        });

        Tools.add(m, "cameo_modify_element", WRITE, c -> {
            Element e = c.el("elementId");
            applyCommon(c.project(), e, c.args, true);
            return Model.summary(e);
        });

        Tools.add(m, "cameo_batch_modify_elements", WRITE, c -> {
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (JsonNode spec : c.node("modifications")) {
                Element e = c.find(need(spec, "elementId"));
                applyCommon(c.project(), e, spec, true);
                a.add(Model.ref(e));
            }
            return Catalog.JSON.createObjectNode().set("modified", a);
        });

        Tools.add(m, "cameo_move_element", WRITE, c -> {
            Element e = c.el("elementId");
            Element to = c.el("newParentId");
            ModelElementsManager.getInstance().moveElement(e, to);
            return Model.summary(e);
        });

        Tools.add(m, "cameo_delete_element", WRITE, c -> {
            List<Element> els = c.has("elementIds") ? c.els("elementIds") : List.of(c.el("elementId"));
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (Element e : els) {
                if (!e.isEditable()) throw new ToolError("'" + Model.name(e) + "' (" + e.getID() + ") is read-only or locked");
                a.add(Model.ref(e));
                ModelElementsManager.getInstance().removeElement(e);
            }
            return Catalog.JSON.createObjectNode().set("deleted", a);
        });

        Tools.add(m, "cameo_clone_element", WRITE, c -> {
            Element e = c.el("elementId");
            Element parent = c.has("targetParentId") ? c.el("targetParentId") : e.getOwner();
            Element copy = CopyPasting.copyPasteElement(e, parent, c.b("increaseElementNames", true));
            if (copy == null) throw new ToolError("Cameo could not copy this element here");
            return Model.summary(copy);
        });

        Tools.add(m, "cameo_convert_element_type", WRITE, c -> {
            Element e = c.el("elementId");
            String target = c.str("targetType");
            Element probe = Model.instantiate(c.project(), target);
            Element out;
            try {
                out = Refactoring.Converting.convert(e, new ConvertElementInfo(probe.getClassType()));
            } finally {
                probe.dispose(); // only used to learn the metaclass; never added to the model
            }
            if (out == null) throw new ToolError("Cameo cannot convert " + e.getHumanType() + " to " + target);
            return Model.summary(out);
        });

        Tools.add(m, "cameo_replace_element", WRITE, c -> {
            Element old = c.el("elementId");
            Element with = c.el("replacementId");
            Element out = Refactoring.Replacing.replace(old, with, new ConvertElementInfo(with.getClassType()));
            return Catalog.JSON.createObjectNode().set("result", out == null ? null : Model.ref(out));
        });

        Tools.add(m, "cameo_reverse_relationship", WRITE, c -> {
            Element r = c.el("relationshipId");
            Refactoring.RelationReversing.reverseRelationDirection(r);
            return Model.relJson(r);
        });

        Tools.add(m, "cameo_reconnect_relationship", WRITE, c -> {
            Element r = c.el("relationshipId");
            if (c.has("sourceId")) CoreHelper.setClientElement(r, c.el("sourceId"));
            if (c.has("targetId")) CoreHelper.setSupplierElement(r, c.el("targetId"));
            return Model.relJson(r);
        });

        Tools.add(m, "cameo_create_relationship", WRITE, c -> {
            Element r = Model.relate(c.project(), c.str("type"), c.el("sourceId"), c.el("targetId"), c.opt("name"));
            applyCommon(c.project(), r, c.args);
            return Model.relJson(r);
        });

        Tools.add(m, "cameo_apply_stereotype", WRITE, c -> {
            Element e = c.el("elementId");
            Stereotype s = stereotypeArg(c);
            if (!StereotypesHelper.canApplyStereotype(e, s))
                throw new ToolError("Stereotype «" + s.getName() + "» cannot be applied to a " + e.getHumanType());
            StereotypesHelper.addStereotype(e, s);
            return Model.ref(e);
        });

        Tools.add(m, "cameo_remove_stereotype", WRITE, c -> {
            Element e = c.el("elementId");
            StereotypesHelper.removeStereotype(e, stereotypeArg(c));
            return Model.ref(e);
        });

        Tools.add(m, "cameo_set_tagged_values", WRITE, c -> {
            Element e = c.el("elementId");
            Iterator<Map.Entry<String, JsonNode>> it = c.node("values").fields();
            if (!it.hasNext()) throw new ToolError("Pass values: {tagName: value}");
            while (it.hasNext()) {
                var en = it.next();
                Model.set(c.project(), e, en.getKey(), en.getValue());
            }
            return Model.details(e).get("taggedValues");
        });

        // ---------------------------------------------------------------- behavior
        Tools.add(m, "cameo_set_transition_trigger", WRITE, c -> {
            Project p = c.project();
            Element te = c.el("transitionId");
            if (!(te instanceof Transition t)) throw new ToolError("Not a transition: " + te.getHumanType());
            if (c.b("replace", true)) {
                for (Trigger old : new ArrayList<>(t.getTrigger())) {
                    Event ev = old.getEvent();
                    ModelElementsManager.getInstance().removeElement(old);
                    // the event was created for this trigger; drop it when nothing else uses it
                    if (ev != null && ev.get_triggerOfEvent().isEmpty() && ev.isEditable()) ModelElementsManager.getInstance().removeElement(ev);
                }
            }
            if (c.has("kind")) {
                Event ev = switch (c.str("kind").toLowerCase(Locale.ROOT)) {
                    case "signal" -> {
                        SignalEvent se = p.getElementsFactory().createSignalEventInstance();
                        se.setSignal((Signal) typed(c.el("signalId"), Signal.class));
                        yield se;
                    }
                    case "call" -> {
                        CallEvent ce = p.getElementsFactory().createCallEventInstance();
                        ce.setOperation((Operation) typed(c.el("operationId"), Operation.class));
                        yield ce;
                    }
                    case "change" -> {
                        ChangeEvent ch = p.getElementsFactory().createChangeEventInstance();
                        ch.setChangeExpression(opaque(p, c.str("expression")));
                        yield ch;
                    }
                    case "time" -> {
                        TimeEvent tev = p.getElementsFactory().createTimeEventInstance();
                        TimeExpression when = p.getElementsFactory().createTimeExpressionInstance();
                        when.setExpr(opaque(p, c.str("expression")));
                        tev.setWhen(when);
                        tev.setRelative(c.b("relative", true));
                        yield tev;
                    }
                    case "any" -> p.getElementsFactory().createAnyReceiveEventInstance();
                    default -> throw new ToolError("kind must be signal, call, change, time or any");
                };
                ModelElementsManager.getInstance().addElement(ev, Model.nearestPackage(t));
                Trigger tr = p.getElementsFactory().createTriggerInstance();
                tr.setEvent(ev);
                ModelElementsManager.getInstance().addElement(tr, t);
            }
            if (c.has("guard")) {
                Constraint g = p.getElementsFactory().createConstraintInstance();
                ModelElementsManager.getInstance().addElement(g, t);
                g.setSpecification(opaque(p, c.str("guard")));
                t.setGuard(g);
            }
            if (c.has("effect")) t.setEffect(behavior(p, t, c.node("effect")));
            return Model.details(t);
        });

        Tools.add(m, "cameo_set_state_behaviors", WRITE, c -> {
            Project p = c.project();
            Element se = c.el("stateId");
            if (!(se instanceof State s)) throw new ToolError("Not a state: " + se.getHumanType());
            if (c.has("entry")) s.setEntry(behavior(p, s, c.node("entry")));
            if (c.has("doActivity")) s.setDoActivity(behavior(p, s, c.node("doActivity")));
            if (c.has("exit")) s.setExit(behavior(p, s, c.node("exit")));
            ObjectNode o = Model.ref(s);
            if (s.getEntry() != null) o.set("entry", Model.ref(s.getEntry()));
            if (s.getDoActivity() != null) o.set("doActivity", Model.ref(s.getDoActivity()));
            if (s.getExit() != null) o.set("exit", Model.ref(s.getExit()));
            return o;
        });
    }

    // ---------------------------------------------------------------- helpers

    private static Project openProject(String nameOrId) {
        for (Project p : Application.getInstance().getProjectsManager().getProjects())
            if (nameOrId.equals(p.getName()) || nameOrId.equals(p.getID())) return p;
        throw new ToolError("No open project named '" + nameOrId + "' (see cameo_list_open_projects)");
    }

    private static boolean matchesType(Element e, String type) {
        String t = type.toLowerCase(Locale.ROOT).replace(" ", "");
        if (e.getHumanType().toLowerCase(Locale.ROOT).replace(" ", "").equals(t)) return true;
        if (e.eClass().getName().equalsIgnoreCase(t)) return true;
        for (Stereotype s : StereotypesHelper.getStereotypes(e)) if (s.getName().equalsIgnoreCase(t)) return true;
        return false;
    }

    private static ObjectNode tree(Element e, int depth, int[] budget) {
        ObjectNode o = Model.ref(e);
        Collection<Element> kids = e.getOwnedElement();
        List<Element> visible = new ArrayList<>();
        for (Element k : kids) if (k instanceof NamedElement || k instanceof Comment) visible.add(k);
        if (visible.isEmpty()) return o;
        if (depth <= 0 || budget[0] <= 0) {
            o.put("childCount", visible.size());
            return o;
        }
        ArrayNode a = o.putArray("children");
        for (Element k : visible) {
            if (budget[0]-- <= 0) { a.add("... truncated (raise limit)"); break; }
            a.add(tree(k, depth - 1, budget));
        }
        return o;
    }

    private static String need(JsonNode spec, String key) {
        JsonNode n = spec.get(key);
        if (n == null || n.asText().isEmpty()) throw new ToolError("Each entry needs '" + key + "': " + spec);
        return n.asText();
    }

    private static Element resolve(Ctx c, Map<String, Element> refs, String key) {
        Element e = refs.get(key);
        return e != null ? e : c.find(key);
    }

    /** name / documentation / stereotypes / properties / multiplicity... shared by every create/modify tool. */
    private static void applyCommon(Project p, Element e, JsonNode spec) {
        applyCommon(p, e, spec, false);
    }

    private static void applyCommon(Project p, Element e, JsonNode spec, boolean rename) {
        if (rename && spec.has("name")) Model.set(p, e, "name", spec.get("name"));
        if (spec.has("documentation")) Model.set(p, e, "documentation", spec.get("documentation"));
        for (JsonNode s : spec.path("stereotypes")) {
            Stereotype st = Model.stereotype(p, s.asText());
            if (!StereotypesHelper.hasStereotype(e, st)) StereotypesHelper.addStereotype(e, st);
        }
        Iterator<Map.Entry<String, JsonNode>> it = spec.path("properties").fields();
        while (it.hasNext()) {
            var en = it.next();
            Model.set(p, e, en.getKey(), en.getValue());
        }
    }

    private static Stereotype stereotypeArg(Ctx c) {
        String s = c.str("stereotype");
        Element byId = c.project().getElementByID(s) instanceof Element el ? el : null;
        return byId instanceof Stereotype st ? st : Model.stereotype(c.project(), s);
    }

    private static Element typed(Element e, java.lang.Class<?> type) {
        if (!type.isInstance(e)) throw new ToolError("Expected a " + type.getSimpleName() + ", got " + e.getHumanType());
        return e;
    }

    private static OpaqueExpression opaque(Project p, String body) {
        OpaqueExpression x = p.getElementsFactory().createOpaqueExpressionInstance();
        x.getBody().add(body);
        x.getLanguage().add("English");
        return x;
    }

    /** A behavior given as an existing element id (string) or as {name, body, language} for a new OpaqueBehavior. */
    private static Behavior behavior(Project p, Element owner, JsonNode spec) throws Exception {
        if (spec.isTextual()) {
            Object b = p.getElementByID(spec.asText());
            if (!(b instanceof Behavior beh)) throw new ToolError("No behavior with id " + spec.asText());
            return beh;
        }
        OpaqueBehavior ob = p.getElementsFactory().createOpaqueBehaviorInstance();
        ob.setName(spec.path("name").asText(""));
        if (spec.has("body")) {
            ob.getBody().add(spec.get("body").asText());
            ob.getLanguage().add(spec.path("language").asText("English"));
        }
        ModelElementsManager.getInstance().addElement(ob, owner);
        return ob;
    }
}
