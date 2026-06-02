package com.campus.activity.model.query;

import java.util.List;
import java.util.Map;

public record QueryPlanDecision(QueryPlan plan,
                                boolean clarificationRequired,
                                List<String> clarificationOptions,
                                Map<String, Object> planPreview,
                                QueryMode queryMode,
                                String adminSql,
                                String summaryHint) {
    public static QueryPlanDecision executable(QueryPlan plan, Map<String, Object> planPreview) {
        return new QueryPlanDecision(plan, false, List.of(), withMode(planPreview, QueryMode.DSL), QueryMode.DSL, null, null);
    }

    public static QueryPlanDecision clarification(List<String> options, Map<String, Object> planPreview) {
        return new QueryPlanDecision(null, true, options, planPreview, QueryMode.DSL, null, null);
    }

    public static QueryPlanDecision adminSql(String sql, String summaryHint, Map<String, Object> planPreview) {
        return new QueryPlanDecision(null, false, List.of(), withMode(planPreview, QueryMode.ADMIN_SQL), QueryMode.ADMIN_SQL, sql, summaryHint);
    }

    public static QueryPlanDecision controlledSql(String sql, String summaryHint, Map<String, Object> planPreview) {
        return new QueryPlanDecision(null, false, List.of(), withMode(planPreview, QueryMode.CONTROLLED_SQL), QueryMode.CONTROLLED_SQL, sql, summaryHint);
    }

    private static Map<String, Object> withMode(Map<String, Object> preview, QueryMode mode) {
        if (preview == null) {
            return Map.of("queryMode", mode.name());
        }
        java.util.Map<String, Object> copy = new java.util.LinkedHashMap<>(preview);
        copy.putIfAbsent("queryMode", mode.name());
        return copy;
    }
}
