package com.cameomcp.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Weighted-sum trade study: each criterion is min-max normalized across the alternatives (1 = best),
 * weighted, and divided by the total weight. Alternatives that break a limit are ranked after all feasible ones.
 */
public final class TradeStudy {

    /** One criterion: weight must be positive; minimize=true means smaller is better; limit may be null. */
    public record Criterion(String name, double weight, boolean minimize, Double limit) {
        public Criterion {
            if (!(weight > 0) || Double.isInfinite(weight)) throw new IllegalArgumentException("Criterion '" + name + "' needs a weight greater than 0");
        }
    }

    public record Result(int alternative, double score, double[] normalized, List<String> violatedLimits, int rank) {
        public boolean feasible() { return violatedLimits.isEmpty(); }
    }

    private TradeStudy() {}

    /** values[alternative][criterion]; returns the results ordered by rank (best first). */
    public static List<Result> rank(double[][] values, List<Criterion> criteria) {
        if (criteria.isEmpty()) throw new IllegalArgumentException("Give at least one criterion");
        if (values.length < 2) throw new IllegalArgumentException("Give at least two alternatives");
        double wsum = 0;
        for (Criterion c : criteria) wsum += c.weight();
        List<Result> out = new ArrayList<>();
        for (int a = 0; a < values.length; a++) {
            double score = 0;
            double[] norm = new double[criteria.size()];
            List<String> fails = new ArrayList<>();
            for (int k = 0; k < criteria.size(); k++) {
                Criterion c = criteria.get(k);
                double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
                for (double[] row : values) { lo = Math.min(lo, row[k]); hi = Math.max(hi, row[k]); }
                double v = values[a][k];
                double n = hi == lo ? 1 : c.minimize() ? (hi - v) / (hi - lo) : (v - lo) / (hi - lo);
                norm[k] = round(n);
                score += c.weight() * n;
                if (c.limit() != null && (c.minimize() ? v > c.limit() : v < c.limit())) fails.add(c.name());
            }
            out.add(new Result(a, round(score / wsum), norm, fails, 0));
        }
        out.sort(Comparator.comparing((Result r) -> !r.feasible()).thenComparing(Result::score, Comparator.reverseOrder()));
        List<Result> ranked = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            Result r = out.get(i);
            ranked.add(new Result(r.alternative(), r.score(), r.normalized(), r.violatedLimits(), i + 1));
        }
        return ranked;
    }

    private static double round(double d) { return Math.round(d * 10000) / 10000.0; }
}
