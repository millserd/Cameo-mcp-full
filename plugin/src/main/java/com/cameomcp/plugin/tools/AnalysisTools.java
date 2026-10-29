package com.cameomcp.plugin.tools;

import com.cameomcp.catalog.Catalog;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.jmi.helpers.CoreHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityFinalNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.FlowFinalNode;
import com.nomagic.uml2.ext.magicdraw.classes.mddependencies.Dependency;
import com.nomagic.uml2.ext.magicdraw.commonbehaviors.mdbasicbehaviors.Behavior;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.InitialNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.ActivityNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityParameterNode;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.*;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.ext.magicdraw.statemachines.mdbehaviorstatemachines.*;

import java.util.*;

import static com.cameomcp.plugin.tools.Tool.Mode.READ;

final class AnalysisTools {

    private AnalysisTools() {}

    static void register(Map<String, Tool> m) {
        Tools.add(m, "cameo_get_traceability_graph", READ, c -> {
            Element root = c.el("elementId");
            String dir = c.str("direction", "both");
            int depth = c.i("depth", 2);
            Set<String> types = lower(c.strs("relationshipTypes"));
            ObjectNode o = Catalog.JSON.createObjectNode();
            ArrayNode nodes = o.putArray("nodes");
            ArrayNode edges = o.putArray("edges");
            Set<Element> seen = new HashSet<>(List.of(root));
            Set<Element> seenRel = new HashSet<>();
            nodes.add(Model.ref(root).put("depth", 0));
            Deque<Element> frontier = new ArrayDeque<>(List.of(root));
            for (int d = 1; d <= depth && !frontier.isEmpty(); d++) {
                Deque<Element> next = new ArrayDeque<>();
                for (Element e : frontier) {
                    for (Element r : Model.relationships(e, dir)) {
                        if (!types.isEmpty() && !typeMatch(r, types)) continue;
                        if (!seenRel.add(r)) continue;
                        Element s = CoreHelper.getClientElement(r);
                        Element t = CoreHelper.getSupplierElement(r);
                        if (s == null || t == null) continue;
                        edges.add(Model.ref(r).put("sourceId", s.getID()).put("targetId", t.getID()));
                        Element other = s == e ? t : s;
                        if (seen.add(other)) {
                            nodes.add(Model.ref(other).put("depth", d));
                            next.add(other);
                        }
                    }
                }
                frontier = next;
            }
            return o;
        });

        Tools.add(m, "cameo_trace_coverage_analysis", READ, c -> {
            Element scope = c.has("scopeId") ? c.el("scopeId") : c.project().getPrimaryModel();
            Stereotype req = Model.stereotype(c.project(), "Requirement");
            List<Element> reqs = new ArrayList<>();
            Model.walk(scope, e -> { if (StereotypesHelper.hasStereotypeOrDerived(e, req)) reqs.add(e); });
            int satisfied = 0, verified = 0;
            ArrayNode rows = Catalog.JSON.createArrayNode();
            ArrayNode unlinked = Catalog.JSON.createArrayNode();
            for (Element r : reqs) {
                List<String> sat = new ArrayList<>(), ver = new ArrayList<>(), other = new ArrayList<>();
                for (Element rel : Model.relationships(r, "in")) {
                    Element src = CoreHelper.getClientElement(rel);
                    String n = src == null ? "?" : src.getID();
                    if (hasSt(rel, "Satisfy")) sat.add(n);
                    else if (hasSt(rel, "Verify")) ver.add(n);
                    else other.add(n);
                }
                if (!sat.isEmpty()) satisfied++;
                if (!ver.isEmpty()) verified++;
                ObjectNode row = Model.ref(r);
                row.set("satisfiedBy", Catalog.JSON.valueToTree(sat));
                row.set("verifiedBy", Catalog.JSON.valueToTree(ver));
                if (!other.isEmpty()) row.set("otherIncoming", Catalog.JSON.valueToTree(other));
                rows.add(row);
                if (sat.isEmpty() && ver.isEmpty()) unlinked.add(Model.ref(r));
            }
            ObjectNode o = Catalog.JSON.createObjectNode().put("requirements", reqs.size())
                    .put("satisfied", satisfied).put("verified", verified)
                    .put("satisfiedPercent", pct(satisfied, reqs.size())).put("verifiedPercent", pct(verified, reqs.size()));
            o.set("unlinked", unlinked);
            if (c.b("includeDetails", true)) o.set("details", rows);
            return o;
        });

        Tools.add(m, "cameo_find_circular_dependencies", READ, c -> {
            Element scope = c.has("scopeId") ? c.el("scopeId") : c.project().getPrimaryModel();
            Set<String> types = lower(c.strs("relationshipTypes"));
            Set<Element> inScope = new HashSet<>();
            Model.walk(scope, inScope::add);
            Map<Element, List<Element>> graph = new HashMap<>();
            for (Element e : inScope) {
                if (!(e instanceof DirectedRelationship || e instanceof Dependency || e instanceof Generalization)) continue;
                if (!types.isEmpty() && !typeMatch(e, types)) continue;
                Element s = CoreHelper.getClientElement(e), t = CoreHelper.getSupplierElement(e);
                if (s != null && t != null && inScope.contains(s) && inScope.contains(t) && s != t)
                    graph.computeIfAbsent(s, k -> new ArrayList<>()).add(t);
            }
            ArrayNode cycles = Catalog.JSON.createArrayNode();
            for (List<Element> scc : tarjan(graph)) {
                if (scc.size() < 2) continue;
                ArrayNode cy = cycles.addArray();
                scc.forEach(e -> cy.add(Model.ref(e)));
            }
            return Catalog.JSON.createObjectNode().put("cycles", cycles.size()).set("components", cycles);
        });

        Tools.add(m, "cameo_get_orphan_elements", READ, c -> {
            Project p = c.project();
            Element scope = c.has("scopeId") ? c.el("scopeId") : p.getPrimaryModel();
            Set<Element> shown = new HashSet<>();
            for (DiagramPresentationElement d : p.getDiagrams()) {
                if (Model.nearestPackage(d.getDiagram()) == null) continue;
                d.ensureLoaded();
                shown.addAll(d.getUsedModelElements(true));
            }
            ArrayNode a = Catalog.JSON.createArrayNode();
            int[] total = {0};
            int limit = c.i("limit", 300);
            Model.walk(scope, e -> {
                if (e == scope || !(e instanceof Classifier || e instanceof Package) || e instanceof Diagram) return;
                if (e instanceof Stereotype || e instanceof Behavior && e.getOwner() instanceof Classifier) return;
                if (shown.contains(e) || !Model.relationships(e, "both").isEmpty()) return;
                if (total[0]++ < limit) a.add(Model.summary(e));
            });
            return Catalog.JSON.createObjectNode().put("total", total[0]).set("orphans", a);
        });

        Tools.add(m, "cameo_find_dead_end_flows", READ, c -> {
            Element e = c.el("activityId");
            if (!(e instanceof Activity act)) throw new ToolError("Not an activity: " + e.getHumanType());
            ArrayNode noOut = Catalog.JSON.createArrayNode(), noIn = Catalog.JSON.createArrayNode();
            for (ActivityNode n : act.getNode()) {
                boolean finalNode = n instanceof ActivityFinalNode || n instanceof FlowFinalNode;
                boolean start = n instanceof InitialNode || n instanceof ActivityParameterNode;
                if (!finalNode && !(n instanceof ActivityParameterNode) && n.getOutgoing().isEmpty()) noOut.add(Model.ref(n));
                if (!start && n.getIncoming().isEmpty()) noIn.add(Model.ref(n));
            }
            ObjectNode o = Catalog.JSON.createObjectNode().put("nodes", act.getNode().size());
            o.set("noOutgoing", noOut);
            o.set("noIncoming", noIn);
            return o;
        });

        Tools.add(m, "cameo_check_state_reachability", READ, c -> {
            Element e = c.el("stateMachineId");
            if (!(e instanceof StateMachine sm)) throw new ToolError("Not a state machine: " + e.getHumanType());
            List<Vertex> all = new ArrayList<>();
            Model.walk(sm, x -> { if (x instanceof Vertex v) all.add(v); });
            Deque<Vertex> todo = new ArrayDeque<>();
            for (Vertex v : all) if (v instanceof Pseudostate ps && ps.getKind() == PseudostateKindEnum.INITIAL) todo.add(v);
            if (todo.isEmpty()) throw new ToolError("No initial pseudostate; reachability needs a start");
            Set<Vertex> reached = new HashSet<>(todo);
            while (!todo.isEmpty()) {
                Vertex v = todo.pop();
                for (Transition t : v.getOutgoing()) if (t.getTarget() != null && reached.add(t.getTarget())) todo.push(t.getTarget());
                if (v instanceof State s) for (Region r : s.getRegion()) for (Vertex sub : r.getSubvertex()) {
                    if (sub instanceof Pseudostate ps && ps.getKind() == PseudostateKindEnum.INITIAL && reached.add(sub)) todo.push(sub);
                }
            }
            ArrayNode unreachable = Catalog.JSON.createArrayNode(), deadEnds = Catalog.JSON.createArrayNode();
            for (Vertex v : all) {
                if (!reached.contains(v)) unreachable.add(Model.ref(v));
                else if (v instanceof State s && !(v instanceof FinalState) && v.getOutgoing().isEmpty() && s.getRegion().isEmpty())
                    deadEnds.add(Model.ref(v));
            }
            ObjectNode o = Catalog.JSON.createObjectNode().put("vertices", all.size()).put("reachable", reached.size());
            o.set("unreachable", unreachable);
            o.set("deadEnds", deadEnds);
            return o;
        });

        Tools.add(m, "cameo_calculate_metrics", READ, c -> {
            Element scope = c.has("scopeId") ? c.el("scopeId") : c.project().getPrimaryModel();
            Map<String, Integer> byType = new TreeMap<>(), bySt = new TreeMap<>();
            int[] n = {0, 0, 0, 0};
            Model.walk(scope, e -> {
                n[0]++;
                byType.merge(e.getHumanType(), 1, Integer::sum);
                StereotypesHelper.getStereotypes(e).forEach(s -> bySt.merge(s.getName(), 1, Integer::sum));
                if (e instanceof Relationship) n[1]++;
                if (e instanceof Diagram) n[2]++;
                int depth = 0;
                for (Element x = e; x != scope && x != null; x = x.getOwner()) depth++;
                n[3] = Math.max(n[3], depth);
            });
            ObjectNode o = Catalog.JSON.createObjectNode().put("elements", n[0]).put("relationships", n[1])
                    .put("diagrams", n[2]).put("maxDepth", n[3]);
            o.set("byType", Catalog.JSON.valueToTree(byType));
            o.set("byStereotype", Catalog.JSON.valueToTree(bySt));
            return o;
        });
    }

    // ---------------------------------------------------------------- helpers

    private static Set<String> lower(List<String> l) {
        Set<String> s = new HashSet<>();
        l.forEach(x -> s.add(x.toLowerCase(Locale.ROOT)));
        return s;
    }

    private static boolean typeMatch(Element r, Set<String> types) {
        if (types.contains(r.getHumanType().toLowerCase(Locale.ROOT).replace(" ", ""))) return true;
        if (types.contains(r.eClass().getName().toLowerCase(Locale.ROOT))) return true;
        for (Stereotype s : StereotypesHelper.getStereotypes(r)) if (types.contains(s.getName().toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static boolean hasSt(Element e, String name) {
        for (Stereotype s : StereotypesHelper.getStereotypes(e)) if (name.equalsIgnoreCase(s.getName())) return true;
        return false;
    }

    private static double pct(int a, int b) {
        return b == 0 ? 0 : Math.round(1000.0 * a / b) / 10.0;
    }

    /** Strongly connected components (iterative-enough for model sizes; recursion depth = longest chain). */
    private static List<List<Element>> tarjan(Map<Element, List<Element>> g) {
        Map<Element, Integer> index = new HashMap<>(), low = new HashMap<>();
        Deque<Element> stack = new ArrayDeque<>();
        Set<Element> onStack = new HashSet<>();
        List<List<Element>> out = new ArrayList<>();
        int[] counter = {0};
        for (Element v : g.keySet()) if (!index.containsKey(v)) strong(v, g, index, low, stack, onStack, out, counter);
        return out;
    }

    private static void strong(Element v, Map<Element, List<Element>> g, Map<Element, Integer> index, Map<Element, Integer> low,
                               Deque<Element> stack, Set<Element> onStack, List<List<Element>> out, int[] counter) {
        index.put(v, counter[0]);
        low.put(v, counter[0]++);
        stack.push(v);
        onStack.add(v);
        for (Element w : g.getOrDefault(v, List.of())) {
            if (!index.containsKey(w)) {
                strong(w, g, index, low, stack, onStack, out, counter);
                low.put(v, Math.min(low.get(v), low.get(w)));
            } else if (onStack.contains(w)) {
                low.put(v, Math.min(low.get(v), index.get(w)));
            }
        }
        if (low.get(v).equals(index.get(v))) {
            List<Element> comp = new ArrayList<>();
            Element w;
            do {
                w = stack.pop();
                onStack.remove(w);
                comp.add(w);
            } while (w != v);
            out.add(comp);
        }
    }
}
