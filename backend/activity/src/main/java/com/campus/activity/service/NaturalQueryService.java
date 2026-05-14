package com.campus.activity.service;

import com.campus.activity.common.AuthContext;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.Role;
import com.campus.activity.model.dto.NaturalQueryRequest;
import com.campus.activity.model.query.QueryExecution;
import com.campus.activity.model.query.QueryPlan;
import com.campus.activity.model.vo.NaturalQueryResultVO;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class NaturalQueryService {
    private final QueryIntentParser queryIntentParser;
    private final QuerySqlBuilder querySqlBuilder;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public NaturalQueryService(QueryIntentParser queryIntentParser,
                               QuerySqlBuilder querySqlBuilder,
                               NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.queryIntentParser = queryIntentParser;
        this.querySqlBuilder = querySqlBuilder;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public NaturalQueryResultVO query(NaturalQueryRequest request) {
        CurrentUser user = AuthContext.get();
        QueryPlan plan = queryIntentParser.parse(request.question(), request.page(), request.size());
        QueryExecution execution = querySqlBuilder.build(plan, user);
        Long total = namedParameterJdbcTemplate.queryForObject(execution.countSql(), execution.params(), Long.class);
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(execution.sql(), execution.params());
        long safeTotal = total == null ? 0 : total;
        return new NaturalQueryResultVO(
                summarize(plan, rows, safeTotal),
                execution.columns(),
                rows,
                user.role() == Role.ADMIN ? execution.sqlPreview() : null,
                plan.getIntent().name(),
                plan.getPage(),
                plan.getSize(),
                safeTotal
        );
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
