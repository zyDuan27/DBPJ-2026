package com.campus.activity.service;

import com.campus.activity.common.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class NaturalQueryPromptBuilder {
    public String systemPrompt() {
        return """
                你是校园活动系统的自然语言查询规划器。你只能输出 JSON，不能输出 SQL。
                目标是把用户问题转成受控 QueryPlan DSL，后端会校验并编译为只读 SELECT。
                不要编造字段、表名或权限条件；不能支持 INSERT、UPDATE、DELETE、DROP 等写操作。
                如果问题过于模糊，返回 ambiguity=true 并给出 2-4 个 clarificationOptions。

                JSON schema:
                {
                  "intent": "ACTIVITY_LIST | ACTIVITY_REGISTRATION_LIST | WAITLIST_TOP | MY_REGISTRATION_LIST | CHECK_IN_STATUS | ABSENCE_LIST | LOW_RATING_FEEDBACK | CREDIT_RISK | CREDIT_RECORDS | NOTIFICATION_LIST",
                  "domain": "activity | registration | checkIn | feedback | credit | notification",
                  "selectFields": ["activity.title"],
                  "filters": [{"field":"startFrom","operator":"gte","value":"2026-05-01T00:00:00"}],
                  "metrics": ["registration.count"],
                  "groupBy": [],
                  "orderBy": ["startTime desc"],
                  "page": 1,
                  "size": 20,
                  "ambiguity": false,
                  "clarificationOptions": []
                }

                allowed filter fields:
                startFrom, startTo, activityKeyword, activityStatus, categoryKeyword, campusKeyword, venueKeyword,
                organizerKeyword, studentKeyword, registrationStatus, maxRating, maxCreditScore, unreadOnly,
                notificationType, evaluatedOnly.
                semantic aliases you may use:
                activity.title, category.name, campus.name, venue.name, venue.room, organizer.name,
                student.name, student.no, registration.status, registration.evaluated, feedback.exists,
                feedback.rating, credit.score, notification.type.
                allowed statuses:
                activityStatus = DRAFT, PENDING_REVIEW, REJECTED, PUBLISHED, ONGOING, FINISHED, CANCELLED;
                registrationStatus = ENROLLED, WAITLISTED, CANCELLED, CHECKED_IN, ABSENT;
                notificationType = ACTIVITY_APPROVED, ACTIVITY_REJECTED, ACTIVITY_CANCELLED,
                REGISTRATION_ENROLLED, REGISTRATION_WAITLISTED, REGISTRATION_CANCELLED.
                用 ISO-8601 日期时间表达时间范围。分页 size 最大 50。
                """;
    }

    public String userPrompt(String question, Integer page, Integer size, CurrentUser user) {
        return """
                当前日期：%s
                当前用户角色：%s
                请求分页：page=%s, size=%s
                用户问题：%s

                角色能力摘要：
                STUDENT 只能查公开活动、自己的报名、自己的信用、自己的通知。
                ORGANIZER 只能查自己创建活动的报名、签到、缺勤、反馈和通知。
                ADMIN 可以查全站业务数据。
                权限条件不要写入 filters，由后端强制注入。

                输出 JSON。
                """.formatted(LocalDate.now(), user.role(), page, size, question);
    }
}
