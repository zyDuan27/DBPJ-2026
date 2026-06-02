package com.campus.activity.service;

import com.campus.activity.common.BusinessException;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.Role;
import com.campus.activity.model.query.ControlledSqlExecution;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ControlledSqlCompiler {
    private static final int MAX_LIMIT = 50;
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?i)\\b(?:from|join)\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?(?:\\s+(?:as\\s+)?([a-zA-Z_][a-zA-Z0-9_]*))?");
    private static final Pattern VIEW_REFERENCE_PATTERN = Pattern.compile("(?i)\\b(from|join)\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?(?:\\s+(?:as\\s+)?(?!where\\b|join\\b|left\\b|right\\b|inner\\b|outer\\b|order\\b|group\\b|having\\b|limit\\b|on\\b)([a-zA-Z_][a-zA-Z0-9_]*))?");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\blimit\\s+(\\d+)\\b");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b");
    private static final Pattern AS_ALIAS_PATTERN = Pattern.compile("(?i)\\bas\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b");
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE", "CALL", "OUTFILE",
            "LOAD", "GRANT", "REVOKE", "CREATE", "REPLACE"
    );
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "SELECT", "FROM", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "WHERE",
            "AND", "OR", "NOT", "NULL", "IS", "LIKE", "IN", "BETWEEN", "ORDER", "BY",
            "GROUP", "HAVING", "LIMIT", "ASC", "DESC", "DISTINCT", "AS", "CASE", "WHEN",
            "THEN", "ELSE", "END", "TRUE", "FALSE", "COUNT", "SUM", "AVG", "MIN", "MAX"
    );
    private static final Set<String> PHYSICAL_TABLES = Set.of(
            "activity", "campus", "venue", "category", "user", "registration",
            "activityfeedback", "creditrecord", "notification"
    );
    private static final Map<String, Set<String>> VIEW_FIELDS = Map.of(
            "activity_view", Set.of("activityId", "activityTitle", "status", "currentEnrollment",
                    "capacityLimit", "startTime", "endTime", "campusName", "venueName", "roomNumber",
                    "categoryName", "organizerName", "description"),
            "campus_view", Set.of("campusId", "campusName", "location"),
            "venue_view", Set.of("venueId", "venueName", "roomNumber", "capacity", "campusName"),
            "my_registration_view", Set.of("registrationId", "activityId", "activityTitle", "registrationStatus",
                    "queueNo", "registrationTime", "checkInTime", "activityStatus", "currentEnrollment",
                    "capacityLimit", "startTime", "campusName", "venueName", "categoryName"),
            "my_feedback_view", Set.of("feedbackId", "activityId", "activityTitle", "rating", "content",
                    "updatedAt", "startTime", "campusName", "venueName"),
            "my_notification_view", Set.of("notificationId", "type", "title", "content", "read",
                    "createdAt", "relatedActivityId"),
            "my_credit_view", Set.of("recordId", "activityTitle", "changeValue", "reasonType", "reason",
                    "createdAt"),
            "organizer_activity_view", Set.of("activityId", "activityTitle", "status", "currentEnrollment",
                    "capacityLimit", "startTime", "endTime", "campusName", "venueName", "roomNumber",
                    "categoryName", "description"),
            "organizer_participant_view", Set.of("studentId", "studentName", "studentNo",
                    "participationCount", "recentParticipationTime")
    );
    private static final Map<Role, Set<String>> ROLE_VIEWS = Map.of(
            Role.STUDENT, Set.of("activity_view", "campus_view", "venue_view", "my_registration_view",
                    "my_feedback_view", "my_notification_view", "my_credit_view"),
            Role.ORGANIZER, Set.of("activity_view", "campus_view", "venue_view", "organizer_activity_view",
                    "organizer_participant_view", "my_notification_view"),
            Role.ADMIN, VIEW_FIELDS.keySet()
    );

    private final QueryMetadataRegistry metadataRegistry;

    public ControlledSqlCompiler(QueryMetadataRegistry metadataRegistry) {
        this.metadataRegistry = metadataRegistry;
    }

    public ControlledSqlExecution compile(String sql, CurrentUser user) {
        String normalized = validate(sql, user);
        String limitedSql = enforceLimit(normalized);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("currentUserId", user.id());
        String compiled = replaceViews(limitedSql, user.role());
        return new ControlledSqlExecution(compiled, limitedSql.replaceAll("\\s+", " ").trim(), params);
    }

    private String validate(String sql, CurrentUser user) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException(40002, "受控 SQL 草稿不能为空");
        }
        String normalized = sql.strip();
        rejectEscapeSyntax(normalized);
        rejectForbiddenKeyword(normalized);
        rejectSensitiveFields(normalized);
        parseSelect(normalized);
        validateViews(normalized, user.role());
        validateIdentifiers(normalized, user.role());
        return normalized;
    }

    private void rejectEscapeSyntax(String sql) {
        if (sql.contains(";") || sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new BusinessException(40002, "受控 SQL 不允许多语句、分号或注释");
        }
    }

    private void rejectForbiddenKeyword(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        if (!upper.stripLeading().startsWith("SELECT")) {
            throw new BusinessException(40002, "受控 SQL 只允许 SELECT");
        }
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upper.matches("(?s).*\\b" + keyword + "\\b.*")) {
                throw new BusinessException(40002, "受控 SQL 包含禁止操作：" + keyword);
            }
        }
    }

    private void rejectSensitiveFields(String sql) {
        if (metadataRegistry.containsSensitiveToken(sql)) {
            throw new BusinessException(40002, "受控 SQL 包含敏感字段");
        }
    }

    private void parseSelect(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select)) {
                throw new BusinessException(40002, "受控 SQL 只允许单条 SELECT");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(40002, "受控 SQL 解析失败：" + ex.getMessage());
        }
    }

    private void validateViews(String sql, Role role) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String view = normalize(matcher.group(1));
            if (PHYSICAL_TABLES.contains(view)) {
                throw new BusinessException(40002, "普通用户 SQL 不能直接引用物理表：" + matcher.group(1));
            }
            if (!VIEW_FIELDS.containsKey(view)) {
                throw new BusinessException(40002, "受控 SQL 包含未知逻辑视图：" + matcher.group(1));
            }
            if (!ROLE_VIEWS.getOrDefault(role, Set.of()).contains(view)) {
                throw new BusinessException(40301, "当前角色无权查询逻辑视图：" + matcher.group(1));
            }
        }
        if (!found) {
            throw new BusinessException(40002, "受控 SQL 缺少逻辑视图");
        }
    }

    private void validateIdentifiers(String sql, Role role) {
        Set<String> allowedViews = ROLE_VIEWS.getOrDefault(role, Set.of());
        Set<String> allowedFields = new java.util.HashSet<>();
        for (String view : allowedViews) {
            allowedFields.addAll(VIEW_FIELDS.getOrDefault(view, Set.of()));
        }
        Set<String> aliases = collectAliases(sql);
        Matcher matcher = TOKEN_PATTERN.matcher(sql);
        while (matcher.find()) {
            String token = matcher.group();
            String upper = token.toUpperCase(Locale.ROOT);
            String lower = normalize(token);
            if (SQL_KEYWORDS.contains(upper)
                    || VIEW_FIELDS.containsKey(lower)
                    || allowedFields.contains(token)
                    || aliases.contains(token)
                    || token.matches("\\d+")) {
                continue;
            }
            if (PHYSICAL_TABLES.contains(lower)) {
                throw new BusinessException(40002, "普通用户 SQL 不能直接引用物理表：" + token);
            }
            if (isStringLiteralToken(sql, matcher.start())) {
                continue;
            }
            throw new BusinessException(40002, "受控 SQL 包含未知字段或标识符：" + token);
        }
    }

    private Set<String> collectAliases(String sql) {
        Set<String> aliases = new java.util.HashSet<>();
        Matcher tableMatcher = TABLE_PATTERN.matcher(sql);
        while (tableMatcher.find()) {
            String alias = tableMatcher.group(2);
            if (alias != null && !SQL_KEYWORDS.contains(alias.toUpperCase(Locale.ROOT))) {
                aliases.add(alias);
            }
        }
        Matcher asMatcher = AS_ALIAS_PATTERN.matcher(sql);
        while (asMatcher.find()) {
            aliases.add(asMatcher.group(1));
        }
        return aliases;
    }

    private boolean isStringLiteralToken(String sql, int position) {
        int quotesBefore = 0;
        for (int i = 0; i < position; i++) {
            if (sql.charAt(i) == '\'') {
                quotesBefore++;
            }
        }
        return quotesBefore % 2 == 1;
    }

    private String enforceLimit(String sql) {
        Matcher matcher = LIMIT_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return sql + " LIMIT " + MAX_LIMIT;
        }
        int limit = Integer.parseInt(matcher.group(1));
        if (limit <= MAX_LIMIT) {
            return sql;
        }
        return matcher.replaceFirst("LIMIT " + MAX_LIMIT);
    }

    private String replaceViews(String sql, Role role) {
        Matcher matcher = VIEW_REFERENCE_PATTERN.matcher(sql);
        StringBuffer compiled = new StringBuffer();
        while (matcher.find()) {
            String clause = matcher.group(1);
            String view = normalize(matcher.group(2));
            if (!VIEW_FIELDS.containsKey(view)) {
                continue;
            }
            String alias = matcher.group(3);
            if (alias == null || alias.isBlank()) {
                alias = view;
            }
            String replacement = clause + " (" + viewSql(view, role) + ") " + alias;
            matcher.appendReplacement(compiled, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(compiled);
        return compiled.toString();
    }

    private String viewSql(String view, Role role) {
        return switch (view) {
            case "activity_view" -> activityViewSql(role);
            case "campus_view" -> """
                    SELECT c.campus_id AS campusId, c.campus_name AS campusName, c.location AS location
                    FROM Campus c
                    """;
            case "venue_view" -> """
                    SELECT v.venue_id AS venueId, v.venue_name AS venueName, v.room_number AS roomNumber,
                           v.capacity AS capacity, c.campus_name AS campusName
                    FROM Venue v
                    JOIN Campus c ON v.campus_id = c.campus_id
                    """;
            case "my_registration_view" -> """
                    SELECT r.registration_id AS registrationId, a.activity_id AS activityId, a.title AS activityTitle,
                           r.status AS registrationStatus, r.queue_no AS queueNo, r.registration_time AS registrationTime,
                           r.check_in_time AS checkInTime, a.status AS activityStatus,
                           a.current_enrollment AS currentEnrollment, a.capacity_limit AS capacityLimit,
                           a.start_time AS startTime, c.campus_name AS campusName, v.venue_name AS venueName,
                           cat.category_name AS categoryName
                    FROM Registration r
                    JOIN Activity a ON r.activity_id = a.activity_id
                    JOIN Venue v ON a.venue_id = v.venue_id
                    JOIN Campus c ON v.campus_id = c.campus_id
                    JOIN Category cat ON a.category_id = cat.category_id
                    WHERE r.student_id = :currentUserId
                    """;
            case "my_feedback_view" -> """
                    SELECT f.feedback_id AS feedbackId, a.activity_id AS activityId, a.title AS activityTitle,
                           f.rating AS rating, f.content AS content, f.updated_at AS updatedAt,
                           a.start_time AS startTime, c.campus_name AS campusName, v.venue_name AS venueName
                    FROM ActivityFeedback f
                    JOIN Activity a ON f.activity_id = a.activity_id
                    JOIN Venue v ON a.venue_id = v.venue_id
                    JOIN Campus c ON v.campus_id = c.campus_id
                    WHERE f.student_id = :currentUserId
                    """;
            case "my_notification_view" -> """
                    SELECT n.notification_id AS notificationId, n.type AS type, n.title AS title,
                           n.content AS content, n.is_read AS `read`, n.created_at AS createdAt,
                           n.related_activity_id AS relatedActivityId
                    FROM Notification n
                    WHERE n.recipient_id = :currentUserId
                    """;
            case "my_credit_view" -> """
                    SELECT cr.record_id AS recordId, a.title AS activityTitle, cr.change_value AS changeValue,
                           cr.reason_type AS reasonType, cr.reason AS reason, cr.created_at AS createdAt
                    FROM CreditRecord cr
                    LEFT JOIN Activity a ON cr.activity_id = a.activity_id
                    WHERE cr.student_id = :currentUserId
                    """;
            case "organizer_activity_view" -> organizerActivityViewSql();
            case "organizer_participant_view" -> """
                    SELECT u.user_id AS studentId, u.username AS studentName, u.student_no AS studentNo,
                           COUNT(DISTINCT r.registration_id) AS participationCount,
                           MAX(a.start_time) AS recentParticipationTime
                    FROM Registration r
                    JOIN Activity a ON r.activity_id = a.activity_id
                    JOIN User u ON r.student_id = u.user_id
                    WHERE a.organizer_id = :currentUserId
                      AND r.status IN ('ENROLLED', 'CHECKED_IN', 'ABSENT')
                    GROUP BY u.user_id, u.username, u.student_no
                    """;
            default -> throw new BusinessException(40002, "未知逻辑视图：" + view);
        };
    }

    private String activityViewSql(Role role) {
        String statusScope = role == Role.ADMIN
                ? "1 = 1"
                : "a.status IN ('PUBLISHED', 'ONGOING', 'FINISHED', 'CANCELLED')";
        return """
                SELECT a.activity_id AS activityId, a.title AS activityTitle, a.status AS status,
                       a.current_enrollment AS currentEnrollment, a.capacity_limit AS capacityLimit,
                       a.start_time AS startTime, a.end_time AS endTime,
                       c.campus_name AS campusName, v.venue_name AS venueName, v.room_number AS roomNumber,
                       cat.category_name AS categoryName, ou.username AS organizerName,
                       a.description AS description
                FROM Activity a
                JOIN Venue v ON a.venue_id = v.venue_id
                JOIN Campus c ON v.campus_id = c.campus_id
                JOIN Category cat ON a.category_id = cat.category_id
                JOIN User ou ON a.organizer_id = ou.user_id
                WHERE %s
                """.formatted(statusScope);
    }

    private String organizerActivityViewSql() {
        return """
                SELECT a.activity_id AS activityId, a.title AS activityTitle, a.status AS status,
                       a.current_enrollment AS currentEnrollment, a.capacity_limit AS capacityLimit,
                       a.start_time AS startTime, a.end_time AS endTime,
                       c.campus_name AS campusName, v.venue_name AS venueName, v.room_number AS roomNumber,
                       cat.category_name AS categoryName, a.description AS description
                FROM Activity a
                JOIN Venue v ON a.venue_id = v.venue_id
                JOIN Campus c ON v.campus_id = c.campus_id
                JOIN Category cat ON a.category_id = cat.category_id
                WHERE a.organizer_id = :currentUserId
                """;
    }

    private String normalize(String value) {
        return value.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }
}
