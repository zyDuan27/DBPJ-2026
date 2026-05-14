package com.campus.activity.model.query;

import java.util.ArrayList;
import java.util.List;

public class QueryPlan {
    private final QueryIntent intent;
    private final List<QueryFilter> filters = new ArrayList<>();
    private int page;
    private int size;

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

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public void addFilter(String key, Object value) {
        if (value != null) {
            filters.add(new QueryFilter(key, value));
        }
    }
}
