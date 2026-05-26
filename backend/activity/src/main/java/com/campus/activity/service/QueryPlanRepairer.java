package com.campus.activity.service;

import com.campus.activity.common.CurrentUser;
import org.springframework.stereotype.Component;

@Component
public class QueryPlanRepairer {
    private final LlmClient llmClient;
    private final NaturalQueryPromptBuilder promptBuilder;

    public QueryPlanRepairer(LlmClient llmClient, NaturalQueryPromptBuilder promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }

    public String repair(String question, Integer page, Integer size, CurrentUser user, String invalidJson, String errorMessage) {
        String repairPrompt = promptBuilder.userPrompt(question, page, size, user)
                + "\n上一次 JSON 不合法，错误原因：" + errorMessage
                + "\n上一次输出：" + invalidJson
                + "\n请只返回修复后的合法 JSON。";
        return llmClient.chatJson(promptBuilder.systemPrompt(), repairPrompt);
    }
}
