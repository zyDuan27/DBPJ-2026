package com.campus.activity.service;

import com.campus.activity.common.CurrentUser;
import com.campus.activity.config.LlmProperties;
import com.campus.activity.model.query.QueryPlan;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class LlmResultSummarizer {
    private final LlmQueryPlanner llmQueryPlanner;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final LlmProperties properties;

    public LlmResultSummarizer(LlmQueryPlanner llmQueryPlanner, LlmClient llmClient, ObjectMapper objectMapper, LlmProperties properties) {
        this.llmQueryPlanner = llmQueryPlanner;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String summarize(String question, QueryPlan plan, List<Map<String, Object>> rows, long total, CurrentUser user, String fallback) {
        if (!llmQueryPlanner.isEnabled() || !properties.isSummaryEnabled() || rows.isEmpty()) {
            return fallback;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "question", question,
                    "role", user.role().name(),
                    "intent", plan.getIntent().name(),
                    "total", total,
                    "sampleRows", rows.stream().limit(10).toList()
            ));
            String content = llmClient.chatJson(
                    "你是校园活动系统的数据摘要助手。只能基于给定 JSON 做简短中文摘要，不要编造数据。输出 {\"summary\":\"...\"}。",
                    payload
            );
            Map<?, ?> result = objectMapper.readValue(content, Map.class);
            Object summary = result.get("summary");
            return summary instanceof String text && !text.isBlank() ? text : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
