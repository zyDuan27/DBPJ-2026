package com.campus.activity.model.vo;

import com.campus.activity.model.row.CategoryPopularityRow;

import java.math.BigDecimal;

public record CategoryPopularityVO(Integer categoryId,
                                   String categoryName,
                                   Long activityCount,
                                   BigDecimal averageEnrollment) {
    public static CategoryPopularityVO from(CategoryPopularityRow row) {
        return new CategoryPopularityVO(
                row.getCategoryId(),
                row.getCategoryName(),
                row.getActivityCount(),
                row.getAverageEnrollment() == null ? BigDecimal.ZERO : row.getAverageEnrollment()
        );
    }

    static BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
