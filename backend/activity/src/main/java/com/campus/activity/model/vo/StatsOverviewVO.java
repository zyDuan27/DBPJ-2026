package com.campus.activity.model.vo;

import com.campus.activity.model.row.StatsOverviewRow;

public record StatsOverviewVO(Long activityCount,
                              Long pendingReviewCount,
                              Long publishedCount,
                              Long registrationCount,
                              Long checkedInCount) {
    public static StatsOverviewVO from(StatsOverviewRow row) {
        return new StatsOverviewVO(
                row.getActivityCount(),
                row.getPendingReviewCount(),
                row.getPublishedCount(),
                row.getRegistrationCount(),
                row.getCheckedInCount()
        );
    }

    static Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
