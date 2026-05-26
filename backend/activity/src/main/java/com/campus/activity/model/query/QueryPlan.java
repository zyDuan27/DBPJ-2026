package com.campus.activity.model.query;

import java.util.ArrayList;
import java.util.List;

public class QueryPlan {
    private final QueryIntent intent;
    private final List<QueryFilter> filters = new ArrayList<>();
    private final List<String> selectFields = new ArrayList<>();
    private final List<String> metrics = new ArrayList<>();
    private final List<String> groupBy = new ArrayList<>();
    private final List<String> orderBy = new ArrayList<>();
    private int page;
    private int size;
    private String planner = "rules";

    public QueryPlan(QueryIntent intent, int page, int size) {
        this.intent = intent;
        this.page = page;
        this.size = size;
    }

    public QueryIntent getIntent() {
        return intent;
    }

    public List<QueryFilter> getFilters() {
        return filters;
    }

    public List<String> getSelectFields() {
        return selectFields;
    }

    public List<String> getMetrics() {
        return metrics;
    }

    public List<String> getGroupBy() {
        return groupBy;
    }

    public List<String> getOrderBy() {
        return orderBy;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public String getPlanner() {
        return planner;
    }

    public void setPlanner(String planner) {
        if (planner != null && !planner.isBlank()) {
            this.planner = planner;
        }
    }

    public void addFilter(String key, Object value) {
        if (value != null) {
            filters.add(new QueryFilter(key, value));
        }
    }

    public void addSelectField(String field) {
        if (field != null && !field.isBlank()) {
            selectFields.add(field);
        }
    }

    public void addMetric(String metric) {
        if (metric != null && !metric.isBlank()) {
            metrics.add(metric);
        }
    }

    public void addGroupBy(String field) {
        if (field != null && !field.isBlank()) {
            groupBy.add(field);
        }
    }

    public void addOrderBy(String field) {
        if (field != null && !field.isBlank()) {
            orderBy.add(field);
        }
    }
}
