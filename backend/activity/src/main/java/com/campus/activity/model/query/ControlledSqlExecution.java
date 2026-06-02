package com.campus.activity.model.query;

import java.util.Map;

public record ControlledSqlExecution(String sql,
                                     String sqlPreview,
                                     Map<String, Object> params) {
}
