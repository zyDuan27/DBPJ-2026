package com.campus.activity.service;

import com.campus.activity.common.BusinessException;
import com.campus.activity.model.query.QueryIntent;
import com.campus.activity.model.query.QueryPlan;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QueryIntentParser {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final List<Pattern> SEMANTIC_ACTIVITY_KEYWORD_PATTERNS = List.of(
            Pattern.compile(".*(?:和|与)(.+?)(?:相关|有关)的?活动.*"),
            Pattern.compile(".*关于(.+?)的?活动.*"),
            Pattern.compile(".*查询(?:一个|一些|有关)?(.+?)(?:相关|有关)的?活动.*"),
            Pattern.compile(".*查询(?:一个|一些)?(.+?)活动.*")
    );
    private static final Pattern CREDIT_SCORE_LIMIT = Pattern.compile("信用分(?:低于|小于|不高于|<=?)(\\d{1,3})");

    public QueryPlan parse(String question, Integer page, Integer size) {
        String text = normalize(question);
        QueryPlan plan = new QueryPlan(resolveIntent(text), sanitizePage(page), sanitizeSize(size));
        applyCommonSlots(plan, text);
        return plan;
    }

    private QueryIntent resolveIntent(String text) {
        if (containsAny(text, "删除", "新增", "插入", "更新", "修改", "清空")) {
            throw new BusinessException(40002, "自然语言查询只支持只读查询");
        }
        if (containsAny(text, "校区") && containsAny(text, "未举办", "没有活动", "无活动", "没活动")) {
            return QueryIntent.CAMPUS_WITHOUT_ACTIVITY;
        }
        if (containsAny(text, "学生") && containsAny(text, "参与过", "参加过") && containsAny(text, "我创建", "我发布", "我的活动")) {
            return QueryIntent.ORGANIZER_PARTICIPANT_STUDENTS;
        }
        if (containsAny(text, "未读通知", "通知")) {
            return QueryIntent.NOTIFICATION_LIST;
        }
        if (containsAny(text, "信用流水")) {
            return QueryIntent.CREDIT_RECORDS;
        }
        if (containsAny(text, "信用分低", "信用风险")) {
            return QueryIntent.CREDIT_RISK;
        }
        if (containsAny(text, "我的评价", "我的活动评价", "评价记录", "反馈记录") && containsAny(text, "我的", "我")) {
            return QueryIntent.MY_FEEDBACK_LIST;
        }
        if (containsAny(text, "低评分", "低分反馈", "差评")) {
            return QueryIntent.LOW_RATING_FEEDBACK;
        }
        if (containsAny(text, "缺勤")) {
            return QueryIntent.ABSENCE_LIST;
        }
        if (containsAny(text, "未签到", "签到情况", "签到")) {
            return QueryIntent.CHECK_IN_STATUS;
        }
        if (containsAny(text, "候补人数最多", "候补最多")) {
            return QueryIntent.WAITLIST_TOP;
        }
        if (containsAny(text, "我的报名", "我的活动", "我报名", "我参加", "报名记录")) {
            return QueryIntent.MY_REGISTRATION_LIST;
        }
        if (containsAny(text, "报名名单", "报名情况", "已取消报名")) {
            return QueryIntent.ACTIVITY_REGISTRATION_LIST;
        }
        if (containsAny(text, "活动", "待审核", "发布")) {
            return QueryIntent.ACTIVITY_LIST;
        }
        throw new BusinessException(40002, "暂时无法识别该查询，请换一种业务问题描述");
    }

    private void applyCommonSlots(QueryPlan plan, String text) {
        if (text.contains("今天")) {
            addDayRange(plan, LocalDate.now());
        } else if (text.contains("明天")) {
            addDayRange(plan, LocalDate.now().plusDays(1));
        } else if (text.contains("本月")) {
            LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
            plan.addFilter("startFrom", firstDay.atStartOfDay());
            plan.addFilter("startTo", firstDay.plusMonths(1).atStartOfDay());
        }
        if (text.contains("待审核")) {
            plan.addFilter("activityStatus", "PENDING_REVIEW");
        } else if (text.contains("已发布") || text.contains("发布")) {
            plan.addFilter("activityStatus", "PUBLISHED");
        }
        if (text.contains("志愿")) {
            plan.addFilter("categoryKeyword", "志愿");
        }
        if (text.contains("邯郸")) {
            plan.addFilter("campusKeyword", "邯郸");
        } else if (text.contains("江湾")) {
            plan.addFilter("campusKeyword", "江湾");
        }
        if (text.contains("光华楼")) {
            plan.addFilter("venueKeyword", "光华楼");
        }
        if (text.contains("计算机协会")) {
            plan.addFilter("organizerKeyword", "计算机协会");
        }
        applyActivityKeywordSlot(plan, text);
        applySemanticActivityKeywordSlot(plan, text);
        applyStudentKeywordSlot(plan, text);
        if (text.contains("未读")) {
            plan.addFilter("unreadOnly", true);
        }
        if (text.contains("活动取消")) {
            plan.addFilter("notificationType", "ACTIVITY_CANCELLED");
        }
        if (text.contains("已取消报名")) {
            plan.addFilter("registrationStatus", "CANCELLED");
        } else if (text.contains("候补")) {
            plan.addFilter("registrationStatus", "WAITLISTED");
        } else if (text.contains("未签到")) {
            plan.addFilter("registrationStatus", "ENROLLED");
        }
        if (containsAny(text, "评价过", "已评价", "有评价", "有反馈", "反馈过")) {
            plan.addFilter("evaluatedOnly", true);
        }
        if (plan.getIntent() == QueryIntent.LOW_RATING_FEEDBACK) {
            plan.addFilter("maxRating", 2);
        }
        Matcher creditLimit = CREDIT_SCORE_LIMIT.matcher(text);
        if (creditLimit.find()) {
            plan.addFilter("maxCreditScore", Integer.parseInt(creditLimit.group(1)));
        } else if (plan.getIntent() == QueryIntent.CREDIT_RISK && text.contains("信用分低")) {
            plan.addFilter("maxCreditScore", 80);
        }
    }

    private void applyActivityKeywordSlot(QueryPlan plan, String text) {
        String marker = null;
        for (String candidate : List.of("的报名名单", "的报名情况", "签到情况", "未签到学生", "缺勤记录", "低分反馈", "低评分反馈")) {
            if (text.contains(candidate)) {
                marker = candidate;
                break;
            }
        }
        if (marker == null) {
            return;
        }
        int end = text.indexOf(marker);
        String prefix = text.substring(0, end)
                .replace("查询", "")
                .replace("查看", "")
                .replace("一下", "")
                .replace("某个活动", "")
                .trim();
        if (!prefix.isBlank() && !containsAny(prefix, "我的", "某活动")) {
            plan.addFilter("activityKeyword", prefix);
        }
    }

    private void applySemanticActivityKeywordSlot(QueryPlan plan, String text) {
        if (plan.getIntent() != QueryIntent.ACTIVITY_LIST
                || plan.getFilters().stream().anyMatch(filter -> "activityKeyword".equals(filter.key()))) {
            return;
        }
        for (Pattern pattern : SEMANTIC_ACTIVITY_KEYWORD_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (!matcher.matches()) {
                continue;
            }
            String keyword = normalizeSemanticActivityKeyword(matcher.group(1));
            if (isUsefulActivityKeyword(keyword)) {
                plan.addFilter("activityKeyword", keyword);
                return;
            }
        }
    }

    private String normalizeSemanticActivityKeyword(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("查询", "")
                .replace("查找", "")
                .replace("查看", "")
                .replace("查一下", "")
                .replace("看看", "")
                .replace("一个", "")
                .replace("一些", "")
                .replace("有关", "")
                .replace("相关", "")
                .replace("关于", "")
                .replace("活动", "")
                .replace("的", "")
                .replace("和", "")
                .replace("与", "")
                .trim();
    }

    private boolean isUsefulActivityKeyword(String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.length() < 2) {
            return false;
        }
        return !containsAny(keyword,
                "今天", "明天", "昨天", "本月", "待审核", "已发布", "发布",
                "报名", "名单", "签到", "缺勤", "通知", "校区", "场地");
    }

    private void applyStudentKeywordSlot(QueryPlan plan, String text) {
        for (String name : List.of("张三", "李四")) {
            if (text.contains(name)) {
                plan.addFilter("studentKeyword", name);
                return;
            }
        }
    }

    private void addDayRange(QueryPlan plan, LocalDate day) {
        plan.addFilter("startFrom", day.atStartOfDay());
        plan.addFilter("startTo", day.plusDays(1).atStartOfDay());
    }

    private String normalize(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(40001, "查询问题不能为空");
        }
        return question.trim().replaceAll("\\s+", "");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
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
}
