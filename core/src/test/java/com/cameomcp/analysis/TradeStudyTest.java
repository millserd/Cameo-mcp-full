package com.cameomcp.analysis;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TradeStudyTest {

    // cost (min, w2), range (max, w3), mass (min, w1, limit 3.0) for options A, B, C
    static final double[][] V = {{100, 20, 2.5}, {150, 35, 3.1}, {90, 15, 1.8}};
    static final List<TradeStudy.Criterion> C = List.of(
            new TradeStudy.Criterion("cost", 2, true, null),
            new TradeStudy.Criterion("range", 3, false, null),
            new TradeStudy.Criterion("mass", 1, true, 3.0));

    @Test
    public void ranksFeasibleFirstThenByScore() {
        List<TradeStudy.Result> r = TradeStudy.rank(V, C);
        assertEquals(2, r.get(0).alternative());        // C: score 0.5, feasible
        assertEquals(0.5, r.get(0).score(), 1e-9);
        assertEquals(0, r.get(1).alternative());        // A: 0.4797
        assertEquals(0.4797, r.get(1).score(), 1e-4);
        assertEquals(1, r.get(2).alternative());        // B: 0.5 but breaks the mass limit
        assertEquals(List.of("mass"), r.get(2).violatedLimits());
        assertEquals(3, r.get(2).rank());
    }

    @Test
    public void equalValuesScoreFullMarks() {
        List<TradeStudy.Result> r = TradeStudy.rank(new double[][]{{5}, {5}}, List.of(new TradeStudy.Criterion("x", 1, false, null)));
        assertEquals(1.0, r.get(0).score(), 0);
        assertEquals(1.0, r.get(1).score(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroWeightIsRejected() {
        new TradeStudy.Criterion("cost", 0, true, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeWeightIsRejected() {
        new TradeStudy.Criterion("cost", -1, true, null);
    }
}
