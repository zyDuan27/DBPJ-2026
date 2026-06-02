package com.campus.activity.service;

import com.campus.activity.model.query.QueryDomain;
import com.campus.activity.model.query.QueryMetadataField;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class QueryMetadataRegistry {
    private final Map<String, QueryMetadataField> fields = new LinkedHashMap<>();
    private final Set<String> allowedTables = Set.of(
            "activity", "campus", "venue", "category", "user", "registration",
            "activityfeedback", "creditrecord", "notification"
    );
    private final Set<String> sensitiveTokens = Set.of(
            "password", "token", "secret", "hash", "credential", "api_key", "apikey", "phone"
    );

    public QueryMetadataRegistry() {
        register("activity.id", "活动ID", "a.activity_id", QueryDomain.ACTIVITY, "number", true, true, true, false);
        register("activity.title", "活动名称", "a.title", QueryDomain.ACTIVITY, "string", true, true, false, false);
        register("activity.startTime", "开始时间", "a.start_time", QueryDomain.ACTIVITY, "datetime", true, true, false, false);
        register("activity.status", "活动状态", "a.status", QueryDomain.ACTIVITY, "string", true, true, false, false);
        register("campus.id", "校区ID", "c.campus_id", QueryDomain.CAMPUS, "number", true, true, true, false);
        register("campus.name", "校区名称", "c.campus_name", QueryDomain.CAMPUS, "string", true, true, false, false);
        register("campus.location", "位置", "c.location", QueryDomain.CAMPUS, "string", true, false, false, false);
        register("venue.name", "场地", "v.venue_name", QueryDomain.VENUE, "string", true, true, false, false);
        register("student.name", "学生姓名", "u.username", QueryDomain.STUDENT, "string", true, true, false, false);
        register("student.no", "学号", "u.student_no", QueryDomain.STUDENT, "string", true, true, false, false);
        register("student.phone", "手机号", "u.phone", QueryDomain.STUDENT, "string", true, false, false, true);
        register("registration.status", "报名状态", "r.status", QueryDomain.REGISTRATION, "string", true, true, false, false);
        register("registration.count", "报名人数", "COUNT(r.registration_id)", QueryDomain.REGISTRATION, "number", false, true, true, false);
        register("feedback.id", "反馈ID", "f.feedback_id", QueryDomain.FEEDBACK, "number", true, true, true, false);
        register("feedback.rating", "评分", "f.rating", QueryDomain.FEEDBACK, "number", true, true, true, false);
        register("feedback.content", "评价内容", "f.content", QueryDomain.FEEDBACK, "string", true, false, false, false);
        register("feedback.updatedAt", "更新时间", "f.updated_at", QueryDomain.FEEDBACK, "datetime", true, true, false, false);
        register("credit.score", "信用分", "credit.creditScore", QueryDomain.CREDIT, "number", true, true, true, false);
        register("notification.type", "通知类型", "n.type", QueryDomain.NOTIFICATION, "string", true, true, false, false);
    }

    public boolean isAllowedTable(String tableName) {
        return tableName != null && allowedTables.contains(normalize(tableName));
    }

    public boolean containsSensitiveToken(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return sensitiveTokens.stream().anyMatch(normalized::contains);
    }

    public Map<String, QueryMetadataField> fields() {
        return Map.copyOf(fields);
    }

    public Set<String> allowedTables() {
        return allowedTables;
    }

    private void register(String key, String label, String sqlExpression, QueryDomain domain, String type,
                          boolean filterable, boolean sortable, boolean aggregateable, boolean adminOnly) {
        fields.put(key, new QueryMetadataField(key, label, sqlExpression, domain, type, filterable, sortable, aggregateable, adminOnly));
    }

    private String normalize(String value) {
        return value.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }
}
