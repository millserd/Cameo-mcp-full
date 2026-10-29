package com.cameomcp.plugin.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Pure text rules: SysML v2 keywords, name quoting, qualified-name splitting, and the proofing checks. */
public class TextRulesTest {

    @Test
    public void keywordsFollowSysmlTextualNotation() {
        assertEquals("part def", V2.keywordOf("PartDefinition"));
        assertEquals("state", V2.keywordOf("StateUsage"));
        assertEquals("calc def", V2.keywordOf("CalculationDefinition"));
        assertEquals("def", V2.keywordOf("Definition"));      // plain definition (e.g. with #keyword metadata)
        assertEquals("", V2.keywordOf("Usage"));
        assertEquals("decide", V2.keywordOf("DecisionNode"));
        assertEquals("use case def", V2.keywordOf("UseCaseDefinition"));
        assertEquals("part def", V2.normalizeKind("PartDefinition"));
        assertEquals("requirement", V2.normalizeKind("requirement"));
        assertEquals("port", V2.normalizeKind("Port"));
    }

    @Test
    public void namesAreQuotedOnlyWhenNeeded() {
        assertEquals("Engine", V2.name("Engine"));
        assertEquals("'MCP Test'", V2.name("MCP Test"));
        assertEquals("'it\\'s'", V2.name("it's"));
    }

    @Test
    public void qualifiedNamesSplitOutsideQuotesOnly() {
        assertArrayEquals(new String[]{"Model", "MCP Test", "Drone"}, V2.splitQn("Model::'MCP Test'::Drone"));
        assertArrayEquals(new String[]{"A", "B::C", "D"}, V2.splitQn("A::'B::C'::D"));
        assertArrayEquals(new String[]{"it's"}, V2.splitQn("'it\\'s'"));
    }

    @Test
    public void proofingFindsAndFixesTextProblems() {
        List<MethodTools.Item> items = new ArrayList<>();
        items.add(new MethodTools.Item("1", "needs  speed", "RequirementDefinition", "requirement def",
                "The the rover shall recieve commands. TODO", null, true));
        items.add(new MethodTools.Item("2", "Engine", "PartDefinition", "part def", "Converts fuel to torque.", null, true));
        ObjectNode r = MethodTools.proof(items, 100, true);
        JsonNode by = r.path("summary").path("byRule");
        assertEquals(1, by.path("double-space").asInt());
        assertEquals(1, by.path("repeated-word").asInt());
        assertEquals(1, by.path("spelling").asInt());
        assertEquals(1, by.path("placeholder").asInt());
        assertEquals(1, by.path("naming-convention").asInt());
        assertFalse("a clean element has no findings", r.path("findings").toString().contains("\"id\":\"2\""));
        JsonNode plan = r.path("patchPlan");
        assertEquals(2, plan.size());
        assertEquals("needs speed", plan.get(0).path("value").asText());
        assertEquals("The rover shall receive commands. TODO", plan.get(1).path("value").asText());
    }
}
