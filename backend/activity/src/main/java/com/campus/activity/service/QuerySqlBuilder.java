package com.campus.activity.service;

import com.campus.activity.common.BusinessException;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.Role;
import com.campus.activity.model.query.QueryExecution;
import com.campus.activity.model.query.QueryFilter;
import com.campus.activity.model.query.QueryIntent;
import com.campus.activity.model.query.QueryPlan;
import com.campus.activity.model.query.QueryTemplate;
import com.campus.activity.model.vo.QueryColumnVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QuerySqlBuilder {
    private final Map<QueryIntent, QueryTemplate> templates = new EnumMap<>(QueryIntent.class);

    public QuerySqlBuilder() {
        registerTemplates();
    }

    public QueryExecution build(QueryPlan plan, CurrentUser user) {
        QueryTemplate template = templates.get(plan.getIntent());
        if (template == null) {
            throw new BusinessException(40002, "暂不支持该类查询");
        }

        List<String> where = new ArrayList<>(template.defaultWhere());
        Map<String, Object> params = new LinkedHashMap<>();
        applyRoleScope(plan, user, where, params);
        applyFilters(plan, template, where, params);

        String whereSql = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        String groupSql = template.groupBy() == null || template.groupBy().isBlank() ? "" : " GROUP BY " + template.groupBy();
        String orderSql = template.orderBy() == null || template.orderBy().isBlank() ? "" : " ORDER BY " + template.orderBy();
        String baseSql = template.fromSql() + whereSql + groupSql;
        String sql = baseSql + orderSql + " LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) FROM (" + baseSql + ") q";
        params.put("limit", plan.getSize());
        params.put("offset", (plan.getPage() - 1) * plan.getSize());
        return new QueryExecution(sql, countSql, preview(sql), params, template.columns());
    }

    private void applyRoleScope(QueryPlan plan, CurrentUser user, List<String> where, Map<String, Object> params) {
        QueryIntent intent = plan.getIntent();
        if (user.role() == Role.ADMIN) {
            return;
        }
        if (user.role() == Role.STUDENT) {
            switch (intent) {
                case ACTIVITY_LIST -> where.add("a.status IN ('PUBLISHED', 'ONGOING', 'FINISHED')");
                case MY_REGISTRATION_LIST -> {
                    where.add("r.student_id = :currentUserId");
                    params.put("currentUserId", user.id());
                }
                case CREDIT_RECORDS -> {
                    where.add("cr.student_id = :currentUserId");
                    params.put("currentUserId", user.id());
                }
                case NOTIFICATION_LIST -> {
                    where.add("n.recipient_id = :currentUserId");
                    params.put("currentUserId", user.id());
                }
                default -> throw new BusinessException(40301, "当前角色无权执行该自然语言查询");
            }
            return;
        }
        switch (intent) {
            case ACTIVITY_LIST -> {
                where.add("a.organizer_id = :currentUserId");
                params.put("currentUserId", user.id());
            }
            case ACTIVITY_REGISTRATION_LIST, CHECK_IN_STATUS, ABSENCE_LIST, LOW_RATING_FEEDBACK, WAITLIST_TOP -> {
                where.add("a.organizer_id = :currentUserId");
                params.put("currentUserId", user.id());
            }
            case NOTIFICATION_LIST -> {
                where.add("n.recipient_id = :currentUserId");
                params.put("currentUserId", user.id());
            }
            default -> throw new BusinessException(40301, "当前角色无权执行该自然语言查询");
        }
    }

    private void applyFilters(QueryPlan plan, QueryTemplate template, List<String> where, Map<String, Object> params) {
        for (QueryFilter filter : plan.getFilters()) {
            String sql = template.filterWhere().get(filter.key());
            if (sql == null) {
                throw new BusinessException(40002, "查询计划包含不允许的筛选字段：" + filter.key());
            }
            where.add(sql);
            params.put(filter.key(), filter.value());
        }
    }

    private String preview(String sql) {
        if (!sql.stripLeading().toUpperCase().startsWith("SELECT")) {
            throw new BusinessException(40002, "自然语言查询只允许 SELECT");
        }
        return sql.replaceAll("\\s+", " ").trim();
    }

    private void registerTemplates() {
        templates.put(QueryIntent.ACTIVITY_LIST, new QueryTemplate(
                List.of(
                        col("activityId", "活动ID", "number"),
                        col("activityTitle", "活动名称", "string"),
                        col("startTime", "开始时间", "datetime"),
                        col("endTime", "结束时间", "datetime"),
                        col("campusName", "校区", "string"),
                        col("venueName", "场地", "string"),
                        col("categoryName", "分类", "string"),
                        col("organizerName", "组织者", "string"),
                        col("status", "状态", "string"),
                        col("currentEnrollment", "当前报名", "number")
                ),
                """
                SELECT a.activity_id AS activityId, a.title AS activityTitle,
                       a.start_time AS startTime, a.end_time AS endTime,
                       c.campus_name AS campusName, v.venue_name AS venueName,
                       cat.category_name AS categoryName, u.username AS organizerName,
                       a.status, a.current_enrollment AS currentEnrollment
                FROM Activity a
                JOIN Venue v ON a.venue_id = v.venue_id
                JOIN Campus c ON v.campus_id = c.campus_id
                JOIN Category cat ON a.category_id = cat.category_id
                JOIN User u ON a.organizer_id = u.user_id
                """,
                List.of(),
                commonActivityFilters("u"),
                "",
                "a.start_time DESC, a.activity_id DESC"
        ));
        templates.put(QueryIntent.ACTIVITY_REGISTRATION_LIST, registrationTemplate("r.registration_time DESC"));
        templates.put(QueryIntent.MY_REGISTRATION_LIST, registrationTemplate("a.start_time DESC"));
        templates.put(QueryIntent.CHECK_IN_STATUS, registrationTemplate("a.start_time DESC, r.status"));
        templates.put(QueryIntent.ABSENCE_LIST, registrationTemplate("a.start_time DESC"));
        templates.put(QueryIntent.WAITLIST_TOP, new QueryTemplate(
                List.of(col("activityId", "活动ID", "number"), col("activityTitle", "活动名称", "string"),
                        col("waitlistedCount", "候补人数", "number"), col("organizerName", "组织者", "string")),
                """
                SELECT a.activity_id AS activityId, a.title AS activityTitle,
                       COUNT(r.registration_id) AS waitlistedCount, u.username AS organizerName
                FROM Activity a
                JOIN User u ON a.organizer_id = u.user_id
                LEFT JOIN Registration r ON a.activity_id = r.activity_id AND r.status = 'WAITLISTED'
                """,
                List.of(),
                commonActivityFilters("u"),
                "a.activity_id, a.title, u.username",
                "waitlistedCount DESC, a.activity_id DESC"
        ));
        templates.put(QueryIntent.LOW_RATING_FEEDBACK, new QueryTemplate(
                List.of(col("feedbackId", "反馈ID", "number"), col("activityTitle", "活动名称", "string"),
                        col("studentName", "学生", "string"), col("rating", "评分", "number"),
                        col("content", "反馈内容", "string"), col("updatedAt", "更新时间", "datetime")),
                """
                SELECT f.feedback_id AS feedbackId, a.title AS activityTitle,
                       su.username AS studentName, f.rating, f.content, f.updated_at AS updatedAt
                FROM ActivityFeedback f
                JOIN Activity a ON f.activity_id = a.activity_id
                JOIN User su ON f.student_id = su.user_id
                JOIN Venue v ON a.venue_id = v.venue_id
                JOIN Campus c ON v.campus_id = c.campus_id
                JOIN Category cat ON a.category_id = cat.category_id
                JOIN User ou ON a.organizer_id = ou.user_id
                """,
                List.of(),
                merge(commonActivityFilters("ou"), Map.of(
                        "maxRating", "f.rating <= :maxRating",
                        "studentKeyword", "(su.username LIKE CONCAT('%', :studentKeyword, '%') OR su.student_no LIKE CONCAT('%', :studentKeyword, '%'))"
                )),
                "",
                "f.rating ASC, f.updated_at DESC"
        ));
        templates.put(QueryIntent.CREDIT_RISK, new QueryTemplate(
                List.of(col("studentId", "学生ID", "number"), col("studentName", "学生", "string"),
                        col("studentNo", "学号", "string"), col("creditScore", "信用分", "number")),
                """
                SELECT credit.studentId, credit.studentName, credit.studentNo, credit.creditScore
                FROM (
                    SELECT u.user_id AS studentId, u.username AS studentName, u.student_no AS studentNo,
                           100 + COALESCE(SUM(cr.change_value), 0) AS creditScore
                    FROM User u
                    LEFT JOIN CreditRecord cr ON u.user_id = cr.student_id
                    WHERE u.role = 'STUDENT'
                    GROUP BY u.user_id, u.username, u.student_no
                ) credit
                """,
                List.of(),
                Map.of(
                        "studentKeyword", "(credit.studentName LIKE CONCAT('%', :studentKeyword, '%') OR credit.studentNo LIKE CONCAT('%', :studentKeyword, '%'))",
                        "maxCreditScore", "credit.creditScore <= :maxCreditScore"
                ),
                "",
                "credit.creditScore ASC, credit.studentId ASC"
        ));
        templates.put(QueryIntent.CREDIT_RECORDS, new QueryTemplate(
                List.of(col("recordId", "流水ID", "number"), col("studentName", "学生", "string"),
                        col("activityTitle", "活动", "string"),
                        col("changeValue", "变化值", "number"), col("reasonType", "原因类型", "string"),
                        col("reason", "说明", "string"), col("createdAt", "创建时间", "datetime")),
                """
                SELECT cr.record_id AS recordId, su.username AS studentName, a.title AS activityTitle, cr.change_value AS changeValue,
                       cr.reason_type AS reasonType, cr.reason, cr.created_at AS createdAt
                FROM CreditRecord cr
                LEFT JOIN Activity a ON cr.activity_id = a.activity_id
                JOIN User su ON cr.student_id = su.user_id
                LEFT JOIN Venue v ON a.venue_id = v.venue_id
                LEFT JOIN Campus c ON v.campus_id = c.campus_id
                LEFT JOIN Category cat ON a.category_id = cat.category_id
                LEFT JOIN User ou ON a.organizer_id = ou.user_id
                """,
                List.of(),
                merge(commonActivityFilters("ou"), Map.of(
                        "studentKeyword", "(su.username LIKE CONCAT('%', :studentKeyword, '%') OR su.student_no LIKE CONCAT('%', :studentKeyword, '%'))"
                )),
                "",
                "cr.created_at DESC, cr.record_id DESC"
        ));
        templates.put(QueryIntent.NOTIFICATION_LIST, new QueryTemplate(
                List.of(col("notificationId", "通知ID", "number"), col("type", "类型", "string"),
                        col("title", "标题", "string"), col("content", "内容", "string"),
                        col("read", "已读", "boolean"), col("createdAt", "创建时间", "datetime")),
                """
                SELECT n.notification_id AS notificationId, n.type, n.title, n.content,
                       n.is_read AS `read`, n.created_at AS createdAt
                FROM Notification n
                """,
                List.of(),
                Map.of("unreadOnly", "n.is_read = 0", "notificationType", "n.type = :notificationType"),
                "",
                "n.is_read ASC, n.created_at DESC, n.notification_id DESC"
        ));
    }

    private QueryTemplate registrationTemplate(String orderBy) {
        Map<String, String> filters = merge(commonActivityFilters("ou"), Map.of(
                "registrationStatus", "r.status = :registrationStatus",
                "studentKeyword", "(u.username LIKE CONCAT('%', :studentKeyword, '%') OR u.student_no LIKE CONCAT('%', :studentKeyword, '%'))",
                "evaluatedOnly", "EXISTS (SELECT 1 FROM ActivityFeedback f WHERE f.registration_id = r.registration_id)"
        ));
        return new QueryTemplate(
                List.of(
                        col("registrationId", "报名ID", "number"),
                        col("activityId", "活动ID", "number"),
                        col("activityTitle", "活动名称", "string"),
                        col("studentName", "学生", "string"),
                        col("studentNo", "学号", "string"),
                        col("registrationStatus", "报名状态", "string"),
                        col("queueNo", "候补序号", "number"),
                        col("registrationTime", "报名时间", "datetime"),
                        col("checkInTime", "签到时间", "datetime")
                ),
                """
                SELECT r.registration_id AS registrationId, a.activity_id AS activityId, a.title AS activityTitle,
                       u.username AS studentName, u.student_no AS studentNo,
                       r.status AS registrationStatus, r.queue_no AS queueNo,
                       r.registration_time AS registrationTime, r.check_in_time AS checkInTime
                FROM Registration r FORCE INDEX (idx_registration_activity_status_queue_time)
                JOIN Activity a ON r.activity_id = a.activity_id
                JOIN User u ON r.student_id = u.user_id
                JOIN Venue v ON a.venue_id = v.venue_id
                JOIN Campus c ON v.campus_id = c.campus_id
                JOIN Category cat ON a.category_id = cat.category_id
                JOIN User ou ON a.organizer_id = ou.user_id
                """,
                List.of(),
                filters,
                "",
                orderBy
        );
    }

    private Map<String, String> commonActivityFilters(String organizerAlias) {
        return Map.of(
                "startFrom", "a.start_time >= :startFrom",
                "startTo", "a.start_time < :startTo",
                "activityKeyword", "a.title LIKE CONCAT('%', :activityKeyword, '%')",
                "activityStatus", "a.status = :activityStatus",
                "categoryKeyword", "cat.category_name LIKE CONCAT('%', :categoryKeyword, '%')",
                "campusKeyword", "c.campus_name LIKE CONCAT('%', :campusKeyword, '%')",
                "venueKeyword", "(v.venue_name LIKE CONCAT('%', :venueKeyword, '%') OR v.room_number LIKE CONCAT('%', :venueKeyword, '%'))",
                "organizerKeyword", organizerAlias + ".username LIKE CONCAT('%', :organizerKeyword, '%')"
        );
    }

    private Map<String, String> merge(Map<String, String> left, Map<String, String> right) {
        Map<String, String> merged = new LinkedHashMap<>(left);
        merged.putAll(right);
        return merged;
    }

    private QueryColumnVO col(String key, String label, String type) {
        return new QueryColumnVO(key, label, type);
    }
}
