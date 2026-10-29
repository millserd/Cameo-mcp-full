package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.cameomcp.plugin.CameoLog;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.annotation.Annotation;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.esi.EsiUtils;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.plugins.Plugin;
import com.nomagic.magicdraw.plugins.PluginUtils;
import com.nomagic.magicdraw.teamwork2.locks.ILockProjectService;
import com.nomagic.magicdraw.teamwork2.locks.LockInfo;
import com.nomagic.magicdraw.uml.BaseElement;
import com.nomagic.magicdraw.validation.RuleViolationResult;
import com.nomagic.magicdraw.validation.ValidationHelper;
import com.nomagic.magicdraw.validation.ValidationRunData;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.jmi.helpers.TagsHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Profile;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.StringWriter;
import java.util.*;

import static com.cameomcp.plugin.tools.Tool.Mode.*;

final class AdvancedTools {

    private AdvancedTools() {}

    /** Results of the last validation runs, by run id (small, bounded). */
    private static final Map<String, ObjectNode> RUNS = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ObjectNode> e) { return size() > 10; }
    });

    static void register(Map<String, Tool> m) {
        // ---------------------------------------------------------------- scripting
        Tools.add(m, "cameo_execute_macro", APP, c -> {
            if (Tools.guard == null || !Tools.guard.isMacrosAllowed())
                throw new ToolError("Script execution is disabled. The user can enable 'Allow Groovy/Jython Macro Execution' "
                        + "in Cameo under MCP > MCP Server Status.");
            String lang = c.str("language", "groovy").toLowerCase(Locale.ROOT);
            String engineName = switch (lang) {
                case "groovy" -> "groovy";
                case "javascript", "js" -> "rhino";
                case "python", "jython" -> "python";
                case "beanshell", "bsh" -> "beanshell";
                default -> throw new ToolError("language: groovy, javascript, python, beanshell");
            };
            ScriptEngine engine = engine(engineName);
            Project p = c.projectOrNull();
            engine.put("project", p);
            engine.put("application", Application.getInstance());
            StringWriter out = new StringWriter();
            engine.getContext().setWriter(out);
            engine.getContext().setErrorWriter(out);
            String script = c.str("script");
            Object result;
            if (p != null && c.b("session", true)) {
                SessionManager sm = SessionManager.getInstance();
                if (sm.isSessionCreated(p)) throw new ToolError("Cameo already has an open editing session; finish it or call cameo_reset_session");
                sm.createSession(p, "MCP: macro");
                try {
                    result = engine.eval(script);
                    if (sm.isSessionCreated(p)) sm.closeSession(p);
                } catch (Throwable t) {
                    if (sm.isSessionCreated(p)) sm.cancelSession(p);
                    throw new ToolError("Script failed: " + t);
                }
            } else {
                try {
                    result = engine.eval(script);
                } catch (Throwable t) {
                    throw new ToolError("Script failed: " + t);
                }
            }
            CameoLog.info("Executed " + lang + " macro (" + script.length() + " chars)");
            ObjectNode o = Catalog.JSON.createObjectNode().put("output", out.toString());
            o.put("result", result == null ? null : String.valueOf(result));
            return o;
        });

        // ---------------------------------------------------------------- validation
        Tools.add(m, "cameo_list_validation_suites", READ, c -> {
            ArrayNode a = Catalog.JSON.createArrayNode();
            for (Element e : suites(c.project())) {
                ObjectNode s = Model.summary(e);
                int rules = 0;
                for (Element x : e.getOwnedElement()) if (x instanceof Constraint) rules++;
                a.add(s.put("rules", rules));
            }
            return Catalog.JSON.createObjectNode().set("suites", a);
        });

        Tools.add(m, "cameo_run_validation", READ, c -> {
            Project p = c.project();
            String ref = c.str("suite");
            Package suite = null;
            for (Element e : suites(p)) {
                if (ref.equals(e.getID()) || ref.equalsIgnoreCase(Model.name(e))) suite = (Package) e;
            }
            if (suite == null) throw new ToolError("No validation suite '" + ref + "' (see cameo_list_validation_suites)");
            EnumerationLiteral sev = Annotation.getSeverityLevel(p, c.str("minSeverity", "debug"));
            if (sev == null) throw new ToolError("minSeverity: debug, info, warning, error, fatal");
            List<Element> scope = c.has("scopeIds") ? c.els("scopeIds") : List.of();
            ValidationRunData run = new ValidationRunData(suite, scope.isEmpty(), scope, sev);
            Collection<RuleViolationResult> results = ValidationHelper.validate(run, "MCP validation", null);
            String runId = UUID.randomUUID().toString().substring(0, 8);
            ObjectNode o = Catalog.JSON.createObjectNode().put("runId", runId).put("suite", suite.getName())
                    .put("violations", results.size());
            ArrayNode a = o.putArray("results");
            int limit = c.i("limit", 300);
            Map<String, Integer> bySeverity = new TreeMap<>();
            for (RuleViolationResult r : results) {
                String severity = r.getAnnotation().getSeverity() == null ? "?" : r.getAnnotation().getSeverity().getName();
                bySeverity.merge(severity, 1, Integer::sum);
                if (a.size() >= limit) continue;
                ObjectNode x = a.addObject().put("severity", severity).put("message", r.getErrorMessage())
                        .put("rule", r.getRule() == null ? null : r.getRule().getName());
                BaseElement be = r.getElement();
                if (be instanceof Element el) x.set("element", Model.ref(el));
            }
            o.set("bySeverity", Catalog.JSON.valueToTree(bySeverity));
            RUNS.put(runId, o);
            return o;
        });

        Tools.add(m, "cameo_get_validation_results", READ, c -> {
            ObjectNode r = RUNS.get(c.str("runId"));
            if (r == null) throw new ToolError("Unknown or expired runId (the last 10 runs are kept)");
            return r;
        });

        // ---------------------------------------------------------------- profiles
        Tools.add(m, "cameo_list_profiles", READ, c -> {
            ArrayNode a = Catalog.JSON.createArrayNode();
            List<Element> profiles = new ArrayList<>();
            for (Package pk : c.project().getModels()) Model.walk(pk, e -> { if (e instanceof Profile) profiles.add(e); });
            for (Element pr : profiles) {
                a.add(Model.summary(pr).put("stereotypes", StereotypesHelper.getStereotypesByProfile((Profile) pr).size()));
            }
            return Catalog.JSON.createObjectNode().set("profiles", a);
        });

        Tools.add(m, "cameo_get_profile_details", READ, c -> {
            Element e = c.el("profileId");
            if (!(e instanceof Profile pr)) throw new ToolError("Not a profile: " + e.getHumanType());
            ObjectNode o = Model.summary(pr);
            ArrayNode a = o.putArray("stereotypes");
            for (Stereotype s : StereotypesHelper.getStereotypesByProfile(pr)) {
                ObjectNode sj = a.addObject().put("id", s.getID()).put("name", s.getName());
                ArrayNode metas = sj.putArray("metaclasses");
                StereotypesHelper.getBaseClasses(s).forEach(mc -> metas.add(mc.getName()));
                ArrayNode tags = sj.putArray("tags");
                for (Property t : TagsHelper.getPropertiesWithDerivedOrdered(s)) {
                    if (t.getName() == null || t.getName().startsWith("base_")) continue;
                    tags.addObject().put("name", t.getName()).put("type", t.getType() == null ? null : t.getType().getName());
                }
            }
            return o;
        });

        Tools.add(m, "cameo_create_stereotype", WRITE, c -> {
            Project p = c.project();
            Element owner = c.el("profileId");
            List<Class> metas = new ArrayList<>();
            for (String n : c.strs("metaclasses")) {
                Class mc = StereotypesHelper.getMetaClassByName(p, n);
                if (mc == null) throw new ToolError("Unknown metaclass '" + n + "'");
                metas.add(mc);
            }
            if (metas.isEmpty()) throw new ToolError("Give at least one metaclass (e.g. Class, Property, Package)");
            Stereotype s = StereotypesHelper.createStereotype(owner, c.str("name"), metas);
            return Model.summary(s);
        });

        Tools.add(m, "cameo_create_tag_definition", WRITE, c -> {
            Project p = c.project();
            Element se = c.el("stereotypeId");
            if (!(se instanceof Stereotype s)) throw new ToolError("Not a stereotype: " + se.getHumanType());
            Property tag = p.getElementsFactory().createPropertyInstance();
            tag.setName(c.str("name"));
            ModelElementsManager.getInstance().addElement(tag, s);
            if (c.has("typeId")) tag.setType((Type) c.el("typeId"));
            else tag.setType(primitive(p, c.str("type", "String")));
            if (c.has("multiplicity")) Model.set(p, tag, "multiplicity", c.node("multiplicity"));
            return Model.summary(tag);
        });

        // ---------------------------------------------------------------- Teamwork Cloud
        Tools.add(m, "cameo_get_teamwork_project", READ, c -> {
            Project p = c.project();
            ObjectNode o = Catalog.JSON.createObjectNode().put("name", p.getName()).put("remote", p.isRemote());
            if (!p.isRemote()) return o;
            EsiUtils.EsiBranchInfo b = EsiUtils.getCurrentBranch(p.getPrimaryProject());
            if (b != null) o.putObject("branch").put("name", b.getName()).put("id", String.valueOf(b.getID()))
                    .put("latestRevision", b.getLatestRevision());
            ILockProjectService locks = EsiUtils.getLockService(p);
            if (locks != null) o.put("elementsLockedByMe", locks.getLockedByMe().size());
            return o;
        });

        Tools.add(m, "cameo_lock_elements", APP, c -> {
            ILockProjectService locks = lockService(c);
            boolean ok = locks.lockElements(c.els("elementIds"), c.b("recursive", false), null);
            if (!ok) throw new ToolError("Teamwork Cloud refused the lock (someone else may hold it)");
            return Catalog.JSON.createObjectNode().put("locked", c.strs("elementIds").size());
        });

        Tools.add(m, "cameo_unlock_elements", APP, c -> {
            ILockProjectService locks = lockService(c);
            boolean ok = locks.unlockElements(c.els("elementIds"), c.b("recursive", false), null);
            if (!ok) throw new ToolError("Unlock failed (uncommitted changes must be committed or discarded first)");
            return Catalog.JSON.createObjectNode().put("unlocked", c.strs("elementIds").size());
        });

        Tools.add(m, "cameo_get_locks", READ, c -> {
            ILockProjectService locks = lockService(c);
            ArrayNode a = Catalog.JSON.createArrayNode();
            int limit = c.i("limit", 300);
            for (Element e : locks.getLockedElements()) {
                if (a.size() >= limit) break;
                LockInfo li = locks.getLockInfo(e);
                ObjectNode x = Model.ref(e);
                if (li != null) x.put("user", li.getUser()).put("since", String.valueOf(li.getDate()));
                a.add(x);
            }
            return Catalog.JSON.createObjectNode().set("locks", a);
        });

        Tools.add(m, "cameo_commit_project", APP, c -> {
            Project p = c.project();
            if (!p.isRemote()) throw new ToolError("Not a Teamwork Cloud project; use cameo_save_project");
            EsiUtils.commitProject(p, c.str("comment"), null, null, false, c.strs("tags"));
            return Catalog.JSON.createObjectNode().put("committed", p.getName());
        });

        Tools.add(m, "cameo_update_project", APP, c -> {
            Project p = c.project();
            if (!p.isRemote()) throw new ToolError("Not a Teamwork Cloud project");
            EsiUtils.updateProject(p);
            return Catalog.JSON.createObjectNode().put("updated", p.getName());
        });
    }

    // ---------------------------------------------------------------- helpers

    private static ScriptEngine engine(String name) {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        for (Plugin pl : PluginUtils.getPlugins()) {
            if ("AutomatonPlugin".equals(pl.getDescriptor().getID())) loaders.add(pl.getClass().getClassLoader());
        }
        loaders.add(Thread.currentThread().getContextClassLoader());
        loaders.add(AdvancedTools.class.getClassLoader());
        for (ClassLoader cl : loaders) {
            ScriptEngine e = new ScriptEngineManager(cl).getEngineByName(name);
            if (e != null) {
                e.getContext().setAttribute("javax.script.filename", "mcp-macro", ScriptContext.ENGINE_SCOPE);
                return e;
            }
        }
        throw new ToolError("No '" + name + "' script engine found (the Automaton plugin ships Groovy, Rhino JavaScript, Jython and BeanShell)");
    }

    private static List<Element> suites(Project p) {
        Stereotype st;
        try {
            st = Model.stereotype(p, "validationSuite");
        } catch (ToolError e) {
            return List.of();
        }
        return StereotypesHelper.getStereotypedElements(st);
    }

    private static ILockProjectService lockService(Ctx c) {
        Project p = c.project();
        if (!p.isRemote()) throw new ToolError("Locks exist only in Teamwork Cloud projects");
        ILockProjectService s = EsiUtils.getLockService(p);
        if (s == null) throw new ToolError("No lock service for this project");
        return s;
    }

    private static Type primitive(Project p, String name) {
        for (String qn : List.of("UML Standard Profile::UML2 Metamodel::PrimitiveTypes::" + name,
                "UML Standard Profile::MagicDraw Profile::datatypes::" + name)) {
            Element e = com.nomagic.magicdraw.uml.Finder.byQualifiedName().find(p, qn);
            if (e instanceof Type t) return t;
        }
        throw new ToolError("Unknown primitive type '" + name + "' (String, Integer, Boolean, Real, UnlimitedNatural), or pass typeId");
    }
}
