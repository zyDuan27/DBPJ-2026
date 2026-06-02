package com.campus.activity.service;

import com.campus.activity.common.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class NaturalQueryPromptBuilder {
    public String systemPrompt() {
        return """
                你是校园活动系统的自然语言查询规划器。你只能输出 JSON。
                普通学生和组织者：输出受控 QueryPlan DSL，不能输出 SQL。
                管理员：可以在更自由的统计类问题中输出 ADMIN_SQL 草稿，但只能是单条 SELECT，不能包含 password/token/secret/hash 等敏感字段。
                权限条件不要写入 filters 或 SQL，由后端强制注入或校验。
                如果问题过于模糊，返回 ambiguity=true 并给出 2-4 个 clarificationOptions。

                DSL JSON schema:
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

                ADMIN_SQL JSON schema, only for ADMIN:
                {
                  "queryMode": "ADMIN_SQL",
                  "sql": "SELECT ...",
                  "summaryHint": "这条查询统计了...",
                  "ambiguity": false,
                  "clarificationOptions": []
                }

                allowed filter fields:
                startFrom, startTo, activityKeyword, activityStatus, activityStatusSet, categoryKeyword, campusKeyword, venueKeyword,
                organizerKeyword, studentKeyword, registrationStatus, maxRating, maxCreditScore, unreadOnly,
                notificationType, evaluatedOnly.
                semantic aliases you may use:
                activity.title, category.name, campus.name, venue.name, venue.room, organizer.name,
                student.name, student.no, registration.status, registration.evaluated, feedback.exists,
                feedback.rating, credit.score, notification.type.
                activityKeyword 表示活动语义关键词，可用于标题、简介、分类、组织者等活动相关文本，不限于精确标题。
                多个活动状态必须使用 activityStatusSet，value 为状态数组；不要把多个状态塞进 activityStatus。

                Examples:
                问：有活动未举办的校区
                答：{"queryMode":"DSL","intent":"CAMPUS_WITHOUT_ACTIVITY","domain":"campus","selectFields":["campus.id","campus.name","campus.location"],"filters":[],"exists":[],"notExists":["activity"],"distinct":true,"metrics":[],"groupBy":[],"orderBy":["campus.id asc"],"page":1,"size":20,"ambiguity":false,"clarificationOptions":[]}
                问：查询参与过我创建的活动的所有学生信息
                答：{"queryMode":"DSL","intent":"ORGANIZER_PARTICIPANT_STUDENTS","domain":"student","selectFields":["student.name","student.no","registration.count","activity.title"],"filters":[],"exists":["registration"],"notExists":[],"distinct":true,"metrics":["registration.count"],"groupBy":["student.no"],"orderBy":["lastParticipationTime desc"],"page":1,"size":20,"ambiguity":false,"clarificationOptions":[]}
                问：查询我报名的在邯郸校区开展的活动，要求活动是我评价过的
                答：{"queryMode":"DSL","intent":"MY_REGISTRATION_LIST","domain":"registration","selectFields":["activity.title","registration.status","activity.startTime"],"filters":[{"field":"campus.name","operator":"contains","value":"邯郸"},{"field":"feedback.exists","operator":"eq","value":true}],"exists":["feedback"],"notExists":[],"distinct":false,"metrics":[],"groupBy":[],"orderBy":["activity.startTime desc"],"page":1,"size":20,"ambiguity":false,"clarificationOptions":[]}
                问：查询我的活动评价记录
                答：{"queryMode":"DSL","intent":"MY_FEEDBACK_LIST","domain":"feedback","selectFields":["activity.title","feedback.rating","feedback.content"],"filters":[],"exists":[],"notExists":[],"distinct":false,"metrics":[],"groupBy":[],"orderBy":["feedback.updatedAt desc"],"page":1,"size":20,"ambiguity":false,"clarificationOptions":[]}
                问：查询一个和数据库相关的活动
                答：{"queryMode":"DSL","intent":"ACTIVITY_LIST","domain":"activity","selectFields":["activity.title","activity.startTime","campus.name"],"filters":[{"field":"activityKeyword","operator":"contains","value":"数据库"}],"exists":[],"notExists":[],"distinct":false,"metrics":[],"groupBy":[],"orderBy":["activity.startTime desc"],"page":1,"size":20,"ambiguity":false,"clarificationOptions":[]}
                问：查询全部活动，包括取消和过期的
                答：{"queryMode":"DSL","intent":"ACTIVITY_LIST","domain":"activity","selectFields":["activity.title","activity.status","activity.startTime"],"filters":[{"field":"activity.statuses","operator":"eq","value":["PUBLISHED","ONGOING","FINISHED","CANCELLED"]}],"exists":[],"notExists":[],"distinct":false,"metrics":[],"groupBy":[],"orderBy":["activity.startTime desc"],"page":1,"size":20,"ambiguity":false,"clarificationOptions":[]}

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
                STUDENT 只能查公开活动、自己的报名、自己的信用、自己的通知，也可以查询不含个人隐私的校区/场地概览。
                ORGANIZER 可以查自己创建活动的报名、签到、缺勤、反馈、参与学生聚合信息和通知。
                ADMIN 可以查全站业务数据，也可以使用 ADMIN_SQL 草稿模式。
                输出 JSON。
                """.formatted(LocalDate.now(), user.role(), page, size, question);
    }
}
