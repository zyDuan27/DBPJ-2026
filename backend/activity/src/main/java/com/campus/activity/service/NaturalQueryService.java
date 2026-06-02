package com.campus.activity.service;

import com.campus.activity.common.AuthContext;
import com.campus.activity.common.BusinessException;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.Role;
import com.campus.activity.model.dto.NaturalQueryRequest;
import com.campus.activity.model.query.QueryExecution;
import com.campus.activity.model.query.QueryPlan;
import com.campus.activity.model.query.QueryPlanDecision;
import com.campus.activity.model.vo.NaturalQueryResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class NaturalQueryService {
    private static final Logger log = LoggerFactory.getLogger(NaturalQueryService.class);

    private final QueryIntentParser queryIntentParser;
    private final LlmQueryPlanner llmQueryPlanner;
    private final LlmResultSummarizer llmResultSummarizer;
    private final QueryPlanValidator queryPlanValidator;
    private final QuerySqlBuilder querySqlBuilder;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public NaturalQueryService(QueryIntentParser queryIntentParser,
                               LlmQueryPlanner llmQueryPlanner,
                               LlmResultSummarizer llmResultSummarizer,
                               QueryPlanValidator queryPlanValidator,
                               QuerySqlBuilder querySqlBuilder,
                               NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.queryIntentParser = queryIntentParser;
        this.llmQueryPlanner = llmQueryPlanner;
        this.llmResultSummarizer = llmResultSummarizer;
        this.queryPlanValidator = queryPlanValidator;
        this.querySqlBuilder = querySqlBuilder;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public NaturalQueryResultVO query(NaturalQueryRequest request) {
        CurrentUser user = AuthContext.get();
        QueryPlanDecision decision = decidePlan(request, user);
        if (decision.clarificationRequired()) {
            return clarificationResult(decision, request);
        }
        QueryPlan plan = decision.plan();
        QueryExecution execution = querySqlBuilder.build(plan, user);
        Long total = namedParameterJdbcTemplate.queryForObject(execution.countSql(), execution.params(), Long.class);
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(execution.sql(), execution.params());
        long safeTotal = total == null ? 0 : total;
        String fallbackSummary = summarize(plan, rows, safeTotal);
        return new NaturalQueryResultVO(
                llmResultSummarizer.summarize(request.question(), plan, rows, safeTotal, user, fallbackSummary),
                execution.columns(),
                rows,
                user.role() == Role.ADMIN ? execution.sqlPreview() : null,
                user.role() == Role.ADMIN ? decision.planPreview() : null,
                false,
                List.of(),
                plan.getIntent().name(),
                plan.getPage(),
                plan.getSize(),
                safeTotal
        );
    }

    private QueryPlanDecision decidePlan(NaturalQueryRequest request, CurrentUser user) {
        if (isAmbiguousRegistrationQuestion(request.question())) {
            return QueryPlanDecision.clarification(
                    registrationClarificationOptions(user),
                    Map.of("planner", "clarification", "reason", "报名查询缺少活动、范围或主体")
            );
        }
        if (llmQueryPlanner.isEnabled()) {
            try {
                return llmQueryPlanner.plan(request.question(), request.page(), request.size(), user);
            } catch (BusinessException ex) {
                log.warn("LLM query planning failed, fallback to rule parser: {}", ex.getMessage());
            } catch (RuntimeException ignored) {
                log.warn("LLM query planning failed, fallback to rule parser: {}", ignored.getMessage());
                // Keep the query feature usable when the model endpoint is unavailable.
            }
        }
        QueryPlan plan = queryIntentParser.parse(request.question(), request.page(), request.size());
        return QueryPlanDecision.executable(plan, queryPlanValidator.planPreview(plan, null));
    }

    private NaturalQueryResultVO clarificationResult(QueryPlanDecision decision, NaturalQueryRequest request) {
        return new NaturalQueryResultVO(
                "这个问题还不够具体，请选择一个查询方向后继续。",
                List.of(),
                List.of(),
                null,
                decision.planPreview(),
                true,
                decision.clarificationOptions(),
                null,
                request.page() == null ? 1 : request.page(),
                request.size() == null ? 20 : Math.min(Math.max(request.size(), 1), 50),
                0
        );
    }

    private boolean isAmbiguousRegistrationQuestion(String question) {
        if (question == null) {
            return false;
        }
        String text = question.trim().replaceAll("\\s+", "");
        return text.matches(".*(报名情况|报名信息|报名数据).*")
                && !text.matches(".*(我的|某个|候补|取消|已取消|名单|本月|今天|明天|昨天).*")
                && !hasSpecificActivityPrefix(text);
    }

    private boolean hasSpecificActivityPrefix(String text) {
        for (String marker : List.of("的报名情况", "的报名信息", "的报名数据")) {
            int index = text.indexOf(marker);
            if (index <= 0) {
                continue;
            }
            String prefix = text.substring(0, index)
                    .replace("查询", "")
                    .replace("查看", "")
                    .replace("查一下", "")
                    .replace("活动", "")
                    .trim();
            if (!prefix.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private List<String> registrationClarificationOptions(CurrentUser user) {
        if (user.role() == Role.STUDENT) {
            return List.of("查询我的报名记录", "查询明天的活动", "查询我的未读通知");
        }
        List<String> options = new java.util.ArrayList<>();
        String activitySql = """
                SELECT title
                FROM Activity
                WHERE (:isAdmin = 1 OR organizer_id = :currentUserId)
                  AND status IN ('PUBLISHED', 'ONGOING', 'FINISHED', 'PENDING_REVIEW')
                ORDER BY start_time DESC, activity_id DESC
                LIMIT 3
                """;
        Map<String, Object> params = Map.of(
                "isAdmin", user.role() == Role.ADMIN ? 1 : 0,
                "currentUserId", user.id()
        );
        for (String title : namedParameterJdbcTemplate.queryForList(activitySql, params, String.class)) {
            options.add("查询" + title + "的报名名单");
        }
        options.add("查询候补人数最多的活动");
        if (user.role() == Role.ADMIN) {
            options.add("查询本月活动报名情况");
        } else {
            options.add("查询我的活动报名情况");
        }
        return options.stream().filter(item -> item != null && !item.isBlank()).limit(5).toList();
    }

    private String summarize(QueryPlan plan, List<Map<String, Object>> rows, long total) {
        if (total == 0) {
            return "没有查询到匹配数据，可以换一个关键词或缩小条件再试。";
        }
        return switch (plan.getIntent()) {
            case ACTIVITY_LIST -> "共查询到 " + total + " 个活动，当前返回 " + rows.size() + " 条。";
            case ACTIVITY_REGISTRATION_LIST -> "共查询到 " + total + " 条报名记录。";
            case WAITLIST_TOP -> "共统计 " + total + " 个活动的候补人数，已按候补人数从高到低排序。";
            case MY_REGISTRATION_LIST -> "共查询到 " + total + " 条与你相关的报名记录。";
            case CHECK_IN_STATUS -> "共查询到 " + total + " 条签到相关记录。";
            case ABSENCE_LIST -> "共查询到 " + total + " 条缺勤记录。";
            case LOW_RATING_FEEDBACK -> "共查询到 " + total + " 条低评分反馈。";
            case CREDIT_RISK -> "共查询到 " + total + " 名学生的信用分，已按信用分从低到高排序。";
            case CREDIT_RECORDS -> "共查询到 " + total + " 条信用流水。";
            case NOTIFICATION_LIST -> "共查询到 " + total + " 条通知。";
        };
    }
}
