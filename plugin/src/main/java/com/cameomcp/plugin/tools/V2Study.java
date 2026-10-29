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
        Feature f = V2.feature(V2.find(p, altRef), featurePath);
        if (f == null) return null;
        JsonNode v = V2.evaluate(f);
        return v.isNumber() ? v.asDouble() : null;
    }

    static ObjectNode ref(Project p, String r) { return V2.ref(V2.find(p, r)); }
}
