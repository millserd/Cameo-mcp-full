package com.cameomcp.plugin.tools;

import com.dassault_systemes.modeler.kerml.model.kerml.Element;
import com.dassault_systemes.modeler.kerml.model.kerml.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.core.Project;

import java.util.Iterator;
import java.util.Map;

/** SysML v2 side of StudyTools; kept separate so StudyTools loads on Cameo editions without SysML v2. */
final class V2Study {

    private V2Study() {}

    /** Evaluate attributes and constraints below an element, optionally with what-if input values (rolled back afterwards). */
    static ObjectNode evaluate(Project p, String ref, JsonNode inputs) throws Exception {
        Element e = V2.find(p, ref);
        if (inputs == null || !inputs.isObject() || inputs.isEmpty()) return V2.evaluateAll(e).put("engine", "Cameo SysML v2 expression evaluator");
        ObjectNode o = Tools.inRolledBackSession(p, "MCP: what-if evaluation", () -> {
            for (Iterator<Map.Entry<String, JsonNode>> it = inputs.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> in = it.next();
                Feature f = V2.feature(e, in.getKey());
                if (f == null) throw new ToolError("No feature '" + in.getKey() + "' under " + V2.qn(e));
                if (f.getOwningType() != e && !V2.isOwnedBelow(f, e)) {
                    throw new ToolError("'" + in.getKey() + "' is inherited; what-if inputs must be features owned in " + V2.qn(e)
                            + " (or redefine it there first)");
                }
                V2.setLiteral(f, in.getValue());
            }
            return V2.evaluateAll(e);
        });
        o.put("engine", "Cameo SysML v2 expression evaluator").put("whatIf", true).set("inputs", inputs);
        o.put("note", "Inputs were applied temporarily and rolled back; the model is unchanged.");
        return o;
    }

    static Double number(Project p, String altRef, String featurePath) {
        Element alt = V2.find(p, altRef);
        Feature f = V2.feature(alt, featurePath);
        if (f == null) return null;
        // Evaluate in the alternative itself, so inherited formulas use the alternative's own (redefined) values.
        int dot = featurePath.lastIndexOf('.');
        Element ctx = dot < 0 ? alt : V2.feature(alt, featurePath.substring(0, dot));
        JsonNode v = V2.evaluate(f, ctx == null ? alt : ctx);
        return v.isNumber() ? v.asDouble() : null;
    }

    static ObjectNode ref(Project p, String r) { return V2.ref(V2.find(p, r)); }

    /** Report every change to the project's own SysML v2 elements (not the libraries) to sink. */
    static void listen(Project p, java.util.function.BiConsumer<com.nomagic.magicdraw.uml.BaseElement, String> sink) {
        org.eclipse.emf.ecore.util.EContentAdapter adapter = new org.eclipse.emf.ecore.util.EContentAdapter() {
            @Override
            public void notifyChanged(org.eclipse.emf.common.notify.Notification n) {
                super.notifyChanged(n);
                if (n.isTouch() || !(n.getNotifier() instanceof com.nomagic.magicdraw.uml.BaseElement b)) return;
                Object f = n.getFeature();
                sink.accept(b, f instanceof org.eclipse.emf.ecore.ENamedElement ne ? ne.getName() : String.valueOf(f));
            }
        };
        java.util.Set<org.eclipse.emf.ecore.resource.Resource> done = new java.util.HashSet<>();
        for (com.dassault_systemes.modeler.kerml.model.kerml.Namespace r : V2.roots(p)) {
            org.eclipse.emf.ecore.resource.Resource res = ((org.eclipse.emf.ecore.EObject) r).eResource();
            if (res != null && done.add(res)) res.eAdapters().add(adapter);
        }
    }

    /** A SysML v2 element by id or qualified name, as the BaseElement Cameo's UI works with. */
    static com.nomagic.magicdraw.uml.BaseElement findBase(Project p, String ref) {
        Element e = V2.find(p, ref);
        if (e instanceof com.nomagic.magicdraw.uml.BaseElement b) return b;
        throw new ToolError("Cannot select " + ref + " in the containment tree");
    }
}
