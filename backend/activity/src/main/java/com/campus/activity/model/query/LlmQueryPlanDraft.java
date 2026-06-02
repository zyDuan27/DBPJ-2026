package com.campus.activity.model.query;

import java.util.ArrayList;
import java.util.List;

public class LlmQueryPlanDraft {
    private String intent;
    private String domain;
    private String queryMode;
    private String sql;
    private String summaryHint;
    private List<String> selectFields = new ArrayList<>();
    private List<LlmQueryPlanFilter> filters = new ArrayList<>();
    private List<String> exists = new ArrayList<>();
    private List<String> notExists = new ArrayList<>();
    private List<String> metrics = new ArrayList<>();
    private List<String> groupBy = new ArrayList<>();
    private List<String> orderBy = new ArrayList<>();
    private Boolean distinct;
    private Integer page;
    private Integer size;
    private Boolean ambiguity;
    private List<String> clarificationOptions = new ArrayList<>();

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getQueryMode() {
        return queryMode;
    }

    public void setQueryMode(String queryMode) {
        this.queryMode = queryMode;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getSummaryHint() {
        return summaryHint;
    }

    public void setSummaryHint(String summaryHint) {
        this.summaryHint = summaryHint;
    }

    public List<String> getSelectFields() {
        return selectFields;
    }

    public void setSelectFields(List<String> selectFields) {
        this.selectFields = selectFields == null ? new ArrayList<>() : selectFields;
    }

    public List<LlmQueryPlanFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<LlmQueryPlanFilter> filters) {
        this.filters = filters == null ? new ArrayList<>() : filters;
    }

    public List<String> getExists() {
        return exists;
    }

    public void setExists(List<String> exists) {
        this.exists = exists == null ? new ArrayList<>() : exists;
    }

    public List<String> getNotExists() {
        return notExists;
    }

    public void setNotExists(List<String> notExists) {
        this.notExists = notExists == null ? new ArrayList<>() : notExists;
    }

    public List<String> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<String> metrics) {
        this.metrics = metrics == null ? new ArrayList<>() : metrics;
    }

    public List<String> getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(List<String> groupBy) {
        this.groupBy = groupBy == null ? new ArrayList<>() : groupBy;
    }

    public List<String> getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(List<String> orderBy) {
        this.orderBy = orderBy == null ? new ArrayList<>() : orderBy;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Boolean getAmbiguity() {
        return ambiguity;
    }

    public void setAmbiguity(Boolean ambiguity) {
        this.ambiguity = ambiguity;
    }

    public List<String> getClarificationOptions() {
        return clarificationOptions;
    }

    public void setClarificationOptions(List<String> clarificationOptions) {
        this.clarificationOptions = clarificationOptions == null ? new ArrayList<>() : clarificationOptions;
    }
}
