package com.campus.activity.model.row;

public class StatsOverviewRow {
    private Long activityCount;
    private Long pendingReviewCount;
    private Long publishedCount;
    private Long registrationCount;
    private Long checkedInCount;

    public Long getActivityCount() {
        return activityCount;
    }

    public void setActivityCount(Long activityCount) {
        this.activityCount = activityCount;
    }

    public Long getPendingReviewCount() {
        return pendingReviewCount;
    }

    public void setPendingReviewCount(Long pendingReviewCount) {
        this.pendingReviewCount = pendingReviewCount;
    }

    public Long getPublishedCount() {
        return publishedCount;
    }

    public void setPublishedCount(Long publishedCount) {
        this.publishedCount = publishedCount;
    }

    public Long getRegistrationCount() {
        return registrationCount;
    }

    public void setRegistrationCount(Long registrationCount) {
        this.registrationCount = registrationCount;
    }

    public Long getCheckedInCount() {
        return checkedInCount;
    }

    public void setCheckedInCount(Long checkedInCount) {
        this.checkedInCount = checkedInCount;
    }
}
