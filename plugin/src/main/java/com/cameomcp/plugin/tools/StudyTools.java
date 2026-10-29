package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.jmi.helpers.ModelHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;

import java.lang.reflect.Method;
import java.util.*;

import static com.cameomcp.plugin.tools.Tool.Mode.*;

/**
 * Parametrics, trade studies and simulation. SysML v2 projects are evaluated with Cameo's built-in model-level
 * expression evaluator (no Simulation Toolkit needed). SysML v1 parametrics and executable simulation need the
 * Cameo Simulation Toolkit, which is called by reflection so the plugin works without it.
 */
final class StudyTools {

    private StudyTools() {}

    static final String SIM = "com.nomagic.magicdraw.simulation.SimulationManager";

    static boolean v2(Project p) { return Tools.v2Available && p.getPrimaryModel() == null; }

    static java.lang.Class<?> simulation() {
        try {
            return java.lang.Class.forName(SIM, true, StudyTools.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    static ToolError noToolkit() {
        return new ToolError("Cameo Simulation Toolkit is not installed (or not licensed) in this Cameo, so SysML v1 "
                + "parametrics and executable simulation are not available. SysML v2 models are evaluated without it: "
                + "use cameo_evaluate_parametrics on a SysML v2 project.");
    }

    static void register(Map<String, Tool> m) {
        Tools.add(m, "cameo_evaluate_parametrics", APP, c -> {
            Project p = c.project();
            JsonNode inputs = c.node("inputs");
            if (v2(p)) return V2Study.evaluate(p, c.str("elementId"), inputs);
            Element e = c.el("elementId");
            java.lang.Class<?> sim = simulation();
            if (sim != null) {
                Map<String, Object> out = simulate(sim, e, null, c.strs("outputs"), inputs);
                return Catalog.JSON.createObjectNode().put("engine", "Cameo Simulation Toolkit").set("outputs", Catalog.JSON.valueToTree(safe(out)));
            }
            // No toolkit: report the parametric model (equations and current values) without solving it.
            ObjectNode o = Catalog.JSON.createObjectNode().put("engine", "none (Simulation Toolkit not installed)")
                    .put("note", "Equations and current default values are listed but not solved.");
            ArrayNode vals = o.putArray("values"), cons = o.putArray("constraints");
            Model.walk(e, x -> {
                if (x instanceof Property pr && pr.getDefaultValue() != null) {
                    vals.add(Model.ref(pr).put("value", String.valueOf(ModelHelper.getValueSpecificationValue(pr.getDefaultValue()))));
                }
                if (x instanceof Constraint k && k.getSpecification() != null) {
                    cons.add(Model.ref(k).put("specification", String.valueOf(ModelHelper.getValueSpecificationValue(k.getSpecification()))));
                }
            });
            return o;
        });

        Tools.add(m, "cameo_run_trade_study", APP, c -> {
            Project p = c.project();
            JsonNode crit = c.node("criteria");
            if (!crit.isArray() || crit.isEmpty()) throw new ToolError("criteria: give [{\"name\":\"mass\",\"weight\":2,\"goal\":\"min\"}, ...]");
            List<String> alts = c.strs("alternatives");
            if (alts.size() < 2) throw new ToolError("alternatives: give at least two element ids or qualified names");
            // values[alt][criterion]
            List<ObjectNode> rows = new ArrayList<>();
            double[][] v = new double[alts.size()][crit.size()];
            for (int a = 0; a < alts.size(); a++) {
                ObjectNode row = Catalog.JSON.createObjectNode();
                ObjectNode vals = row.putObject("values");
                for (int k = 0; k < crit.size(); k++) {
                    String name = crit.get(k).path("name").asText();
                    Double d = v2(p) ? V2Study.number(p, alts.get(a), name) : v1Number(c.find(alts.get(a)), name);
                    if (d == null) throw new ToolError("Alternative '" + alts.get(a) + "' has no numeric value for '" + name + "'");
                    v[a][k] = d;
                    vals.put(name, d);
                }
                row.set("alternative", v2(p) ? V2Study.ref(p, alts.get(a)) : Model.ref(c.find(alts.get(a))));
                rows.add(row);
            }
            double wsum = 0;
            for (JsonNode k : crit) wsum += k.path("weight").asDouble(1);
            for (int a = 0; a < alts.size(); a++) {
                double score = 0;
                ObjectNode norm = rows.get(a).putObject("normalized");
                ArrayNode fails = Catalog.JSON.createArrayNode();
                for (int k = 0; k < crit.size(); k++) {
                    JsonNode cr = crit.get(k);
                    double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                    for (double[] r : v) { lo = Math.min(lo, r[k]); hi = Math.max(hi, r[k]); }
                    boolean min = "min".equalsIgnoreCase(cr.path("goal").asText("max"));
                    double n = hi == lo ? 1 : min ? (hi - v[a][k]) / (hi - lo) : (v[a][k] - lo) / (hi - lo);
                    norm.put(cr.path("name").asText(), round(n));
                    score += cr.path("weight").asDouble(1) * n;
                    if (cr.has("limit")) {
                        double lim = cr.get("limit").asDouble();
                        if (min ? v[a][k] > lim : v[a][k] < lim) fails.add(cr.path("name").asText());
                    }
                }
                rows.get(a).put("score", round(score / wsum)).put("feasible", fails.isEmpty());
                if (!fails.isEmpty()) rows.get(a).set("violatedLimits", fails);
            }
            rows.sort((x, y) -> {
                int f = Boolean.compare(y.path("feasible").asBoolean(), x.path("feasible").asBoolean());
                return f != 0 ? f : Double.compare(y.path("score").asDouble(), x.path("score").asDouble());
            });
            ObjectNode o = Catalog.JSON.createObjectNode().put("method", "weighted sum of min-max normalized criteria");
            ArrayNode ranking = o.putArray("ranking");
            for (int i = 0; i < rows.size(); i++) ranking.add(rows.get(i).put("rank", i + 1));
            o.set("criteria", crit);
            o.set("recommended", rows.get(0).get("alternative"));
            return o;
        });

        Tools.add(m, "cameo_get_simulation_status", READ, c -> {
            java.lang.Class<?> sim = simulation();
            ObjectNode o = Catalog.JSON.createObjectNode().put("simulationToolkit", sim != null)
                    .put("sysml2Evaluation", Tools.v2Available);
            Project p = c.projectOrNull();
            if (p != null && p.getPrimaryModel() != null) {
                ArrayNode configs = o.putArray("simulationConfigs");
                Stereotype st = StereotypesHelper.getStereotype(p, "SimulationConfig");
                if (st != null) for (Element e : StereotypesHelper.getExtendedElements(st)) configs.add(Model.ref(e));
            }
            if (sim == null) o.put("note", noToolkit().getMessage());
            return o;
        });

        Tools.add(m, "cameo_run_simulation", BACKGROUND, c -> {
            java.lang.Class<?> sim = simulation();
            if (sim == null) throw noToolkit();
            Element target = c.el("targetId");
            Element config = c.elOpt("configId");
            Map<String, Object> out = simulate(sim, target, config, c.strs("outputs"), c.node("inputs"));
            return Catalog.JSON.createObjectNode().set("outputs", Catalog.JSON.valueToTree(safe(out)));
        });
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> simulate(java.lang.Class<?> sim, Element target, Element config, List<String> outputs, JsonNode inputs) throws Exception {
        Map<String, Object> in = inputs != null && inputs.isObject() ? Catalog.JSON.convertValue(inputs, Map.class) : new HashMap<>();
        Object r;
        if (config != null) {
            Method mm = sim.getMethod("simulate", Element.class, Element.class, Collection.class, Map.class);
            r = mm.invoke(null, config, target, outputs, in);
        } else {
            Method mm = sim.getMethod("simulate", Element.class, Collection.class, Map.class);
            r = mm.invoke(null, target, outputs, in);
        }
        return r == null ? Map.of() : (Map<String, Object>) r;
    }

    private static Map<String, Object> safe(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k, v instanceof Number || v instanceof Boolean || v == null ? v : String.valueOf(v)));
        return out;
    }

    private static double round(double d) { return Math.round(d * 10000) / 10000.0; }

    /** A numeric default value of a v1 property (including inherited), or null. */
    static Double v1Number(Element alt, String name) {
        if (!(alt instanceof Classifier cl)) return null;
        for (NamedElement ne : cl.getInheritedMember()) if (ne instanceof Property p && name.equals(p.getName())) return num(p);
        for (Property p : cl.getAttribute()) if (name.equals(p.getName())) return num(p);
        return null;
    }

    private static Double num(Property p) {
        if (p.getDefaultValue() == null) return null;
        Object v = ModelHelper.getValueSpecificationValue(p.getDefaultValue());
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }
}
