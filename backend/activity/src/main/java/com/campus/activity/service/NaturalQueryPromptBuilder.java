package com.campus.activity.service;

import com.campus.activity.common.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class NaturalQueryPromptBuilder {
    public String systemPrompt() {
        return """
                你是校园活动系统的自然语言查询规划器，只能输出 JSON。
                优先输出 CONTROLLED_SQL：学生和组织者只能查询后端声明的逻辑视图，不能引用真实数据库表。
                管理员可以输出 ADMIN_SQL，也可以输出 CONTROLLED_SQL。ADMIN_SQL 只能是单条 SELECT，不能包含 password/token/secret/hash 等敏感字段。
                权限条件不要写入 SQL 或 filters，由后端通过逻辑视图强制注入。
                如果问题过于模糊，返回 ambiguity=true 并给出 2-4 个 clarificationOptions。

                CONTROLLED_SQL JSON schema:
                {
                  "queryMode": "CONTROLLED_SQL",
                  "sql": "SELECT activityId, activityTitle FROM activity_view LIMIT 20",
                  "summaryHint": "查询符合条件的活动。",
                  "ambiguity": false,
                  "clarificationOptions": []
                }

                ADMIN_SQL JSON schema, only for ADMIN:
                {
                  "queryMode": "ADMIN_SQL",
                  "sql": "SELECT ...",
                  "summaryHint": "这条查询统计了...",
                  "ambiguity": false,
                  "clarificationOptions": []
                }

                DSL JSON schema, only when SQL view mode is not suitable:
                {
                  "queryMode": "DSL",
                  "intent": "ACTIVITY_LIST | ACTIVITY_REGISTRATION_LIST | WAITLIST_TOP | MY_REGISTRATION_LIST | CHECK_IN_STATUS | ABSENCE_LIST | LOW_RATING_FEEDBACK | MY_FEEDBACK_LIST | CREDIT_RISK | CREDIT_RECORDS | NOTIFICATION_LIST | CAMPUS_WITHOUT_ACTIVITY | ORGANIZER_PARTICIPANT_STUDENTS",
                  "domain": "activity | campus | venue | student | registration | checkIn | feedback | credit | notification",
                  "selectFields": ["activity.title"],
                  "filters": [{"field":"startFrom","operator":"gte","value":"2026-05-01T00:00:00"}],
                  "exists": ["feedback"],
                  "notExists": ["activity"],
                  "distinct": false,
                  "metrics": ["registration.count"],
                  "groupBy": [],
                  "orderBy": ["activity.startTime desc"],
                  "page": 1,
                  "size": 20,
                  "ambiguity": false,
                  "clarificationOptions": []
                }

                allowed logical views and fields:
                activity_view(activityId, activityTitle, status, currentEnrollment, capacityLimit, startTime, endTime, campusName, venueName, roomNumber, categoryName, organizerName, description)
                campus_view(campusId, campusName, location)
                venue_view(venueId, venueName, roomNumber, capacity, campusName)
                my_registration_view(registrationId, activityId, activityTitle, registrationStatus, queueNo, registrationTime, checkInTime, activityStatus, currentEnrollment, capacityLimit, startTime, campusName, venueName, categoryName)
                my_feedback_view(feedbackId, activityId, activityTitle, rating, content, updatedAt, startTime, campusName, venueName)
                my_notification_view(notificationId, type, title, content, read, createdAt, relatedActivityId)
                my_credit_view(recordId, activityTitle, changeValue, reasonType, reason, createdAt)
                organizer_activity_view(activityId, activityTitle, status, currentEnrollment, capacityLimit, startTime, endTime, campusName, venueName, roomNumber, categoryName, description)
                organizer_participant_view(studentId, studentName, studentNo, participationCount, recentParticipationTime)

                role view rules:
                STUDENT can use activity_view, campus_view, venue_view, my_registration_view, my_feedback_view, my_notification_view, my_credit_view.
                ORGANIZER can use activity_view, campus_view, venue_view, organizer_activity_view, organizer_participant_view, my_notification_view.
                ADMIN can use ADMIN_SQL for physical business tables, or any logical view with CONTROLLED_SQL.
                Never output phone, password, token, secret, hash, credential, api_key, or currentUserId.
                Always add LIMIT <= 50. Use currentEnrollment for current registration count.

                Examples:
                问：查询当前报名人数不为0的活动
                答：{"queryMode":"CONTROLLED_SQL","sql":"SELECT activityId, activityTitle, currentEnrollment, capacityLimit, startTime, campusName FROM activity_view WHERE currentEnrollment <> 0 ORDER BY startTime DESC LIMIT 20","summaryHint":"查询当前报名人数不为 0 的活动。","ambiguity":false,"clarificationOptions":[]}
                问：查询一个容量没满的活动
                答：{"queryMode":"CONTROLLED_SQL","sql":"SELECT activityId, activityTitle, currentEnrollment, capacityLimit, startTime FROM activity_view WHERE currentEnrollment < capacityLimit ORDER BY startTime ASC LIMIT 20","summaryHint":"查询仍有可报名名额的活动。","ambiguity":false,"clarificationOptions":[]}
                问：查询我的活动评价记录
                答：{"queryMode":"CONTROLLED_SQL","sql":"SELECT feedbackId, activityTitle, rating, content, updatedAt FROM my_feedback_view ORDER BY updatedAt DESC LIMIT 20","summaryHint":"查询你的活动评价记录。","ambiguity":false,"clarificationOptions":[]}
                问：查询参与过我创建的活动的所有学生信息
                答：{"queryMode":"CONTROLLED_SQL","sql":"SELECT studentName, studentNo, participationCount, recentParticipationTime FROM organizer_participant_view ORDER BY recentParticipationTime DESC LIMIT 20","summaryHint":"查询参与过你创建活动的学生汇总信息。","ambiguity":false,"clarificationOptions":[]}
                问：查询一个和数据库相关的活动
                答：{"queryMode":"CONTROLLED_SQL","sql":"SELECT activityId, activityTitle, startTime, campusName FROM activity_view WHERE activityTitle LIKE '%数据库%' OR description LIKE '%数据库%' OR categoryName LIKE '%数据库%' OR organizerName LIKE '%数据库%' ORDER BY startTime DESC LIMIT 20","summaryHint":"查询与数据库相关的活动。","ambiguity":false,"clarificationOptions":[]}
                问：查询全部活动，包括取消和过期的
                答：{"queryMode":"CONTROLLED_SQL","sql":"SELECT activityId, activityTitle, status, startTime FROM activity_view WHERE status IN ('PUBLISHED','ONGOING','FINISHED','CANCELLED') ORDER BY startTime DESC LIMIT 20","summaryHint":"查询全部公开范围内的活动，包括取消和已结束活动。","ambiguity":false,"clarificationOptions":[]}
                """;
    }

    public String userPrompt(String question, Integer page, Integer size, CurrentUser user) {
        return """
                当前日期：%s
                当前用户角色：%s
                请求分页：page=%s, size=%s
                用户问题：%s

                输出 JSON。
                """.formatted(LocalDate.now(), user.role(), page, size, question);
    }
}
