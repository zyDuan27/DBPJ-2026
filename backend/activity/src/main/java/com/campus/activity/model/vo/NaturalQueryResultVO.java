package com.campus.activity.model.vo;

import java.util.List;
import java.util.Map;

public record NaturalQueryResultVO(String summary,
                                   List<QueryColumnVO> columns,
                                   List<Map<String, Object>> rows,
                                   String sqlPreview,
                                   String intent,
                                   int page,
                                   int size,
                                   long total) {
}
