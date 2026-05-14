package com.campus.activity.model.row;

import java.math.BigDecimal;

public class CategoryPopularityRow {
    private Integer categoryId;
    private String categoryName;
    private Long activityCount;
    private BigDecimal averageEnrollment;

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getActivityCount() {
        return activityCount;
    }

    public void setActivityCount(Long activityCount) {
        this.activityCount = activityCount;
    }

    public BigDecimal getAverageEnrollment() {
        return averageEnrollment;
    }

    public void setAverageEnrollment(BigDecimal averageEnrollment) {
        this.averageEnrollment = averageEnrollment;
    }
}
