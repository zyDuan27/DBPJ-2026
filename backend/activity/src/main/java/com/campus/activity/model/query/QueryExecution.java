package com.campus.activity.model.query;

import com.campus.activity.model.vo.QueryColumnVO;

import java.util.List;
import java.util.Map;

public record QueryExecution(String sql,
                             String countSql,
                             String sqlPreview,
                             Map<String, Object> params,
                             List<QueryColumnVO> columns) {
}
