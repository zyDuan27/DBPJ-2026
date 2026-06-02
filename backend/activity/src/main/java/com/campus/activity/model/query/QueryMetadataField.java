package com.campus.activity.model.query;

public record QueryMetadataField(String key,
                                 String label,
                                 String sqlExpression,
                                 QueryDomain domain,
                                 String type,
                                 boolean filterable,
                                 boolean sortable,
                                 boolean aggregateable,
                                 boolean adminOnly) {
}
