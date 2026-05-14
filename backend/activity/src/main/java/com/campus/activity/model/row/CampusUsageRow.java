package com.campus.activity.model.row;

public class CampusUsageRow {
    private Integer campusId;
    private String campusName;
    private Long activityCount;
    private Long venueCount;

    public Integer getCampusId() {
        return campusId;
    }

    public void setCampusId(Integer campusId) {
        this.campusId = campusId;
    }

    public String getCampusName() {
        return campusName;
    }

    public void setCampusName(String campusName) {
        this.campusName = campusName;
    }

    public Long getActivityCount() {
        return activityCount;
    }

    public void setActivityCount(Long activityCount) {
        this.activityCount = activityCount;
    }

    public Long getVenueCount() {
        return venueCount;
    }

    public void setVenueCount(Long venueCount) {
        this.venueCount = venueCount;
    }
}
