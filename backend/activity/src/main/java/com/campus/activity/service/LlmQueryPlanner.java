package com.campus.activity.service;

import com.campus.activity.common.BusinessException;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.Role;
import com.campus.activity.config.LlmProperties;
import com.campus.activity.model.query.LlmQueryPlanDraft;
import com.campus.activity.model.query.QueryMode;
import com.campus.activity.model.query.QueryPlanDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class LlmQueryPlanner {
    private static final Logger log = LoggerFactory.getLogger(LlmQueryPlanner.class);

    private final LlmProperties properties;
    private final LlmClient llmClient;
    private final NaturalQueryPromptBuilder promptBuilder;
    private final QueryPlanValidator validator;
    private final QueryPlanRepairer repairer;
    private final ObjectMapper objectMapper;

    public LlmQueryPlanner(LlmProperties properties,
                           LlmClient llmClient,
                           NaturalQueryPromptBuilder promptBuilder,
                           QueryPlanValidator validator,
                           QueryPlanRepairer repairer,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.validator = validator;
        this.repairer = repairer;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.isReady();
    }

    public QueryPlanDecision plan(String question, Integer page, Integer size, CurrentUser user) {
        long startedAt = System.currentTimeMillis();
        String content = llmClient.chatJson(promptBuilder.systemPrompt(), promptBuilder.userPrompt(question, page, size, user));
        try {
            QueryPlanDecision decision = decisionFromDraft(parse(content), page, size, user);
            log.info("LLM query plan generated in {} ms, intent={}",
                    System.currentTimeMillis() - startedAt,
                    logIntent(decision));
            return decision;
        } catch (RuntimeException firstFailure) {
            if (!properties.isRepairEnabled()) {
                throw firstFailure;
            }
            String repaired = repairer.repair(question, page, size, user, content, firstFailure.getMessage());
            try {
                QueryPlanDecision decision = decisionFromDraft(parse(repaired), page, size, user);
                log.info("LLM query plan repaired in {} ms, intent={}",
                        System.currentTimeMillis() - startedAt,
                        logIntent(decision));
                return decision;
            } catch (RuntimeException secondFailure) {
                if (secondFailure instanceof BusinessException businessException) {
                    throw businessException;
                }
                throw new BusinessException(40002, "LLM 查询计划无法通过校验：" + secondFailure.getMessage());
            }
        }
    }

    private QueryPlanDecision decisionFromDraft(LlmQueryPlanDraft draft, Integer page, Integer size, CurrentUser user) {
        if ("ADMIN_SQL".equalsIgnoreCase(draft.getQueryMode())) {
            if (!properties.isAdminSqlEnabled()
                    || user.role() != Role.ADMIN) {
                throw new BusinessException(40301, "当前角色无权使用管理员 SQL 草稿模式");
            }
            return QueryPlanDecision.adminSql(draft.getSql(), draft.getSummaryHint(), validator.adminSqlPreview(draft));
        }
        if ("CONTROLLED_SQL".equalsIgnoreCase(draft.getQueryMode())) {
            if (!isControlledSqlAllowed()) {
                throw new BusinessException(40301, "当前配置未启用受控 SQL 草稿模式");
            }
            return QueryPlanDecision.controlledSql(draft.getSql(), draft.getSummaryHint(), validator.controlledSqlPreview(draft));
        }
        return validator.validate(draft, page, size);
    }

    private boolean isControlledSqlAllowed() {
        return properties.isControlledSqlEnabled()
                && ("CONTROLLED_ALL".equalsIgnoreCase(properties.getSqlMode())
                || "CONTROLLED_ONLY".equalsIgnoreCase(properties.getSqlMode()));
    }

    private String logIntent(QueryPlanDecision decision) {
        if (decision.queryMode() == QueryMode.ADMIN_SQL || decision.queryMode() == QueryMode.CONTROLLED_SQL) {
            return decision.queryMode().name();
        }
        return decision.plan() == null ? "CLARIFICATION" : decision.plan().getIntent().name();
    }

    private LlmQueryPlanDraft parse(String content) {
        try {
            return objectMapper.readValue(content, LlmQueryPlanDraft.class);
        } catch (Exception ex) {
            throw new BusinessException(40002, "LLM 查询计划不是合法 JSON：" + ex.getMessage());
        }
    }
}
