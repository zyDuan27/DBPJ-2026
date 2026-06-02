package com.campus.activity.service;

import com.campus.activity.common.BusinessException;
import com.campus.activity.model.query.LlmQueryPlanDraft;
import com.campus.activity.model.query.LlmQueryPlanFilter;
import com.campus.activity.model.query.QueryFilter;
import com.campus.activity.model.query.QueryIntent;
import com.campus.activity.model.query.QueryPlan;
import com.campus.activity.model.query.QueryPlanDecision;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class QueryPlanValidator {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final Set<String> ALLOWED_FILTERS = Set.of(
            "startFrom", "startTo", "activityKeyword", "activityStatus", "activityStatusSet", "categoryKeyword", "campusKeyword",
            "venueKeyword", "organizerKeyword", "studentKeyword", "registrationStatus", "maxRating",
            "maxCreditScore", "unreadOnly", "notificationType", "evaluatedOnly"
    );
    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "activity", "campus", "venue", "student", "registration", "checkIn", "feedback", "credit", "notification"
    );
    private static final Set<String> ALLOWED_OPERATORS = Set.of("eq", "contains", "gte", "gt", "lte", "lt");
    private static final Set<String> ACTIVITY_STATUSES = Set.of(
            "DRAFT", "PENDING_REVIEW", "REJECTED", "PUBLISHED", "ONGOING", "FINISHED", "CANCELLED"
    );
    private static final Set<String> REGISTRATION_STATUSES = Set.of(
            "ENROLLED", "WAITLISTED", "CANCELLED", "CHECKED_IN", "ABSENT"
    );
    private static final Set<String> NOTIFICATION_TYPES = Set.of(
            "ACTIVITY_APPROVED", "ACTIVITY_REJECTED", "ACTIVITY_CANCELLED",
            "REGISTRATION_ENROLLED", "REGISTRATION_WAITLISTED", "REGISTRATION_CANCELLED"
    );
    private static final Map<String, String> FIELD_ALIASES = Map.ofEntries(
            Map.entry("activity.startTime", "startFrom"),
            Map.entry("activity.endTime", "startTo"),
            Map.entry("activity.title", "activityKeyword"),
            Map.entry("activity.name", "activityKeyword"),
            Map.entry("activity.status", "activityStatus"),
            Map.entry("activity.statuses", "activityStatusSet"),
            Map.entry("activity.category", "categoryKeyword"),
            Map.entry("category.name", "categoryKeyword"),
            Map.entry("activity.campus", "campusKeyword"),
            Map.entry("campus.name", "campusKeyword"),
            Map.entry("activity.venue", "venueKeyword"),
            Map.entry("venue.name", "venueKeyword"),
            Map.entry("venue.room", "venueKeyword"),
            Map.entry("activity.organizer", "organizerKeyword"),
            Map.entry("organizer.name", "organizerKeyword"),
            Map.entry("student.name", "studentKeyword"),
            Map.entry("student.no", "studentKeyword"),
            Map.entry("student.studentNo", "studentKeyword"),
            Map.entry("registration.status", "registrationStatus"),
            Map.entry("registration.evaluated", "evaluatedOnly"),
            Map.entry("feedback.exists", "evaluatedOnly"),
            Map.entry("feedback.rating", "maxRating"),
            Map.entry("credit.score", "maxCreditScore"),
            Map.entry("notification.unread", "unreadOnly"),
            Map.entry("notification.type", "notificationType")
    );

    public QueryPlanDecision validate(LlmQueryPlanDraft draft, Integer requestPage, Integer requestSize) {
        if (draft == null) {
            throw new BusinessException(40002, "LLM 未返回查询计划");
        }
        Map<String, Object> preview = preview(draft);
        if (Boolean.TRUE.equals(draft.getAmbiguity())) {
            return QueryPlanDecision.clarification(
                    defaultOptions(draft.getClarificationOptions()),
                    preview
            );
        }
        QueryIntent intent = parseIntent(draft.getIntent());
        validateDomain(draft.getDomain());
        List<QueryFilter> safeFilters = draft.getFilters().stream()
                .map(this::validateFilter)
                .toList();
        intent = normalizeIntent(intent, draft, safeFilters);
        QueryPlan plan = new QueryPlan(intent, sanitizePage(firstNonNull(draft.getPage(), requestPage)),
                sanitizeSize(firstNonNull(draft.getSize(), requestSize)));
        plan.setPlanner("llm");
        plan.setDistinct(Boolean.TRUE.equals(draft.getDistinct()));
        for (String field : draft.getSelectFields()) {
            rejectSensitiveField(field);
            plan.addSelectField(field);
        }
        for (String metric : draft.getMetrics()) {
            plan.addMetric(metric);
        }
        for (String group : draft.getGroupBy()) {
            plan.addGroupBy(group);
        }
        for (String order : draft.getOrderBy()) {
            plan.addOrderBy(order);
        }
        for (String exists : draft.getExists()) {
            plan.addExists(exists);
        }
        for (String notExists : draft.getNotExists()) {
            plan.addNotExists(notExists);
        }
        for (QueryFilter safeFilter : safeFilters) {
            plan.addFilter(safeFilter.key(), safeFilter.value());
        }
        enforceIntentDefaults(plan);
        if (plan.getFilters().isEmpty() && requiresNarrowing(intent)) {
            return QueryPlanDecision.clarification(defaultOptions(List.of()), preview);
        }
        return QueryPlanDecision.executable(plan, planPreview(plan, draft.getDomain()));
    }

    public Map<String, Object> planPreview(QueryPlan plan, String domain) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("planner", plan.getPlanner());
        preview.put("intent", plan.getIntent().name());
        preview.put("domain", domain == null || domain.isBlank() ? inferDomain(plan.getIntent()) : domain);
        preview.put("queryMode", plan.getPlanner().equals("rules") ? "RULE_FALLBACK" : "DSL");
        preview.put("filters", plan.getFilters());
        preview.put("selectFields", plan.getSelectFields());
        preview.put("exists", plan.getExists());
        preview.put("notExists", plan.getNotExists());
        preview.put("distinct", plan.isDistinct());
        preview.put("metrics", plan.getMetrics());
        preview.put("groupBy", plan.getGroupBy());
        preview.put("orderBy", plan.getOrderBy());
        preview.put("page", plan.getPage());
        preview.put("size", plan.getSize());
        return preview;
    }

    private QueryFilter validateFilter(LlmQueryPlanFilter filter) {
        if (filter == null || filter.getField() == null || filter.getField().isBlank()) {
            throw new BusinessException(40002, "查询计划包含空筛选字段");
        }
        String key = FIELD_ALIASES.getOrDefault(filter.getField(), filter.getField());
        if (!ALLOWED_FILTERS.contains(key)) {
            throw new BusinessException(40002, "查询计划包含未知筛选字段：" + filter.getField());
        }
        String operator = normalizeOperator(key, filter);
        if (!ALLOWED_OPERATORS.contains(operator)) {
            throw new BusinessException(40002, "查询计划包含非法筛选操作符：" + operator);
        }
        return new QueryFilter(key, normalizeValue(key, filter.getValue()));
    }

    public Map<String, Object> adminSqlPreview(LlmQueryPlanDraft draft) {
        Map<String, Object> preview = preview(draft);
        preview.put("queryMode", "ADMIN_SQL");
        preview.put("summaryHint", draft.getSummaryHint());
        return preview;
    }

    public Map<String, Object> controlledSqlPreview(LlmQueryPlanDraft draft) {
        Map<String, Object> preview = preview(draft);
        preview.put("queryMode", "CONTROLLED_SQL");
        preview.put("summaryHint", draft.getSummaryHint());
        return preview;
    }

    private QueryIntent normalizeIntent(QueryIntent intent, LlmQueryPlanDraft draft, List<QueryFilter> filters) {
        if ("campus".equalsIgnoreCase(draft.getDomain())
                && (intent == QueryIntent.ACTIVITY_LIST || intent == QueryIntent.CAMPUS_WITHOUT_ACTIVITY)
                && draft.getNotExists().stream().anyMatch(item -> item != null && item.toLowerCase(Locale.ROOT).contains("activity"))) {
            return QueryIntent.CAMPUS_WITHOUT_ACTIVITY;
        }
        if ("student".equalsIgnoreCase(draft.getDomain())
                && (intent == QueryIntent.ACTIVITY_REGISTRATION_LIST || intent == QueryIntent.ORGANIZER_PARTICIPANT_STUDENTS)
                && (draft.getDistinct() == null || Boolean.TRUE.equals(draft.getDistinct()))) {
            return QueryIntent.ORGANIZER_PARTICIPANT_STUDENTS;
        }
        if ("feedback".equalsIgnoreCase(draft.getDomain()) && intent == QueryIntent.MY_REGISTRATION_LIST) {
            return QueryIntent.MY_FEEDBACK_LIST;
        }
        if (intent == QueryIntent.ACTIVITY_LIST
                && filters.stream().anyMatch(filter -> "registrationStatus".equals(filter.key()) || "evaluatedOnly".equals(filter.key()))) {
            return QueryIntent.MY_REGISTRATION_LIST;
        }
        return intent;
    }

    private String normalizeOperator(String key, LlmQueryPlanFilter filter) {
        String operator = filter.getOperator() == null ? "eq" : filter.getOperator().toLowerCase(Locale.ROOT).trim();
        if (("unreadOnly".equals(key) || "evaluatedOnly".equals(key)) && ("true".equals(operator) || "false".equals(operator))) {
            return "eq";
        }
        return switch (operator) {
            case "=", "equals" -> "eq";
            case "like" -> "contains";
            case ">=" -> "gte";
            case ">" -> "gt";
            case "<=" -> "lte";
            case "<" -> "lt";
            default -> operator;
        };
    }

    private Object normalizeValue(String key, Object value) {
        if (value == null) {
            throw new BusinessException(40002, "查询计划筛选值不能为空");
        }
        return switch (key) {
            case "startFrom", "startTo" -> parseDateTime(value);
            case "activityStatus" -> enumValue(value, ACTIVITY_STATUSES, "活动状态");
            case "activityStatusSet" -> enumValues(value, ACTIVITY_STATUSES, "活动状态");
            case "registrationStatus" -> enumValue(value, REGISTRATION_STATUSES, "报名状态");
            case "notificationType" -> enumValue(value, NOTIFICATION_TYPES, "通知类型");
            case "maxRating" -> parseInteger(value, 1, 5, "评分上限");
            case "maxCreditScore" -> parseInteger(value, 0, 100, "信用分上限");
            case "unreadOnly", "evaluatedOnly" -> parseBoolean(value);
            default -> value.toString().trim();
        };
    }

    private void enforceIntentDefaults(QueryPlan plan) {
        if (plan.getIntent() == QueryIntent.LOW_RATING_FEEDBACK && plan.getFilters().stream().noneMatch(f -> "maxRating".equals(f.key()))) {
            plan.addFilter("maxRating", 2);
        }
        if (plan.getIntent() == QueryIntent.ABSENCE_LIST && plan.getFilters().stream().noneMatch(f -> "registrationStatus".equals(f.key()))) {
            plan.addFilter("registrationStatus", "ABSENT");
        }
        if (plan.getIntent() == QueryIntent.CHECK_IN_STATUS && plan.getFilters().stream().noneMatch(f -> "registrationStatus".equals(f.key()))) {
            plan.addFilter("registrationStatus", "ENROLLED");
        }
    }

    private boolean requiresNarrowing(QueryIntent intent) {
        return intent == QueryIntent.ACTIVITY_REGISTRATION_LIST
                || intent == QueryIntent.CHECK_IN_STATUS
                || intent == QueryIntent.ABSENCE_LIST
                || intent == QueryIntent.LOW_RATING_FEEDBACK;
    }

    private Map<String, Object> preview(LlmQueryPlanDraft draft) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("planner", "llm");
        preview.put("queryMode", draft.getQueryMode() == null || draft.getQueryMode().isBlank() ? "DSL" : draft.getQueryMode());
        preview.put("intent", draft.getIntent());
        preview.put("domain", draft.getDomain());
        preview.put("filters", draft.getFilters());
        preview.put("selectFields", draft.getSelectFields());
        preview.put("exists", draft.getExists());
        preview.put("notExists", draft.getNotExists());
        preview.put("distinct", draft.getDistinct());
        preview.put("metrics", draft.getMetrics());
        preview.put("groupBy", draft.getGroupBy());
        preview.put("orderBy", draft.getOrderBy());
        preview.put("page", draft.getPage());
        preview.put("size", draft.getSize());
        preview.put("ambiguity", draft.getAmbiguity());
        return preview;
    }

    private QueryIntent parseIntent(String value) {
        try {
            return QueryIntent.valueOf(value == null ? "" : value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(40002, "查询计划包含未知意图：" + value);
        }
    }

    private void validateDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return;
        }
        if (!ALLOWED_DOMAINS.contains(domain.trim())) {
            throw new BusinessException(40002, "查询计划包含非法结果域：" + domain);
        }
    }

    private void rejectSensitiveField(String field) {
        if (field == null) {
            return;
        }
        String lowered = field.toLowerCase(Locale.ROOT);
        if (lowered.contains("password") || lowered.contains("token") || lowered.contains("secret") || lowered.contains("hash")) {
            throw new BusinessException(40002, "查询计划包含敏感字段：" + field);
        }
    }

    private List<String> defaultOptions(List<String> options) {
        if (options != null && !options.isEmpty()) {
            return options.stream().filter(item -> item != null && !item.isBlank()).limit(4).toList();
        }
        return List.of("查询某个活动的报名名单", "按时间范围统计报名情况", "查询我的报名记录");
    }

    private LocalDateTime parseDateTime(Object value) {
        String text = value.toString().trim();
        if (text.length() == 10) {
            return LocalDate.parse(text).atStartOfDay();
        }
        return LocalDateTime.parse(text);
    }

    private String enumValue(Object value, Set<String> allowed, String label) {
        String text = value.toString().trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(text)) {
            throw new BusinessException(40002, "查询计划包含非法" + label + "：" + value);
        }
        return text;
    }

    private List<String> enumValues(Object value, Set<String> allowed, String label) {
        List<?> rawValues = value instanceof List<?> list ? list : List.of(value.toString().split(","));
        List<String> normalized = rawValues.stream()
                .map(item -> item.toString().trim().toUpperCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new BusinessException(40002, label + "不能为空");
        }
        for (String item : normalized) {
            if (!allowed.contains(item)) {
                throw new BusinessException(40002, "查询计划包含非法" + label + "：" + item);
            }
        }
        return normalized;
    }

    private Integer parseInteger(Object value, int min, int max, String label) {
        int number = value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
        if (number < min || number > max) {
            throw new BusinessException(40002, label + "超出允许范围");
        }
        return number;
    }

    private Boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private int sanitizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int sanitizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first == null ? second : first;
    }

    private String inferDomain(QueryIntent intent) {
        return switch (intent) {
            case ACTIVITY_LIST, WAITLIST_TOP -> "activity";
            case ACTIVITY_REGISTRATION_LIST, MY_REGISTRATION_LIST -> "registration";
            case CHECK_IN_STATUS, ABSENCE_LIST -> "checkIn";
            case LOW_RATING_FEEDBACK, MY_FEEDBACK_LIST -> "feedback";
            case CREDIT_RISK, CREDIT_RECORDS -> "credit";
            case NOTIFICATION_LIST -> "notification";
            case CAMPUS_WITHOUT_ACTIVITY -> "campus";
            case ORGANIZER_PARTICIPANT_STUDENTS -> "student";
        };
    }
}
