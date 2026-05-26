package com.campus.activity.model.query;

import java.util.List;
import java.util.Map;

public record QueryPlanDecision(QueryPlan plan,
                                boolean clarificationRequired,
                                List<String> clarificationOptions,
                                Map<String, Object> planPreview) {
    public static QueryPlanDecision executable(QueryPlan plan, Map<String, Object> planPreview) {
        return new QueryPlanDecision(plan, false, List.of(), planPreview);
    }

    public static QueryPlanDecision clarification(List<String> options, Map<String, Object> planPreview) {
        return new QueryPlanDecision(null, true, options, planPreview);
    }
}
