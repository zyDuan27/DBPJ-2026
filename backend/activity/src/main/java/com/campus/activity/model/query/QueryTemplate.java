package com.campus.activity.model.query;

import com.campus.activity.model.vo.QueryColumnVO;

import java.util.List;
import java.util.Map;

public record QueryTemplate(List<QueryColumnVO> columns,
                            String fromSql,
                            List<String> defaultWhere,
                            Map<String, String> filterWhere,
                            String groupBy,
                            String orderBy) {
}
