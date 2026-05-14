package com.campus.activity.model.vo;

import com.campus.activity.model.row.CampusUsageRow;

public record CampusUsageVO(Integer campusId, String campusName, Long activityCount, Long venueCount) {
    public static CampusUsageVO from(CampusUsageRow row) {
        return new CampusUsageVO(
                row.getCampusId(),
                row.getCampusName(),
                row.getActivityCount(),
                row.getVenueCount()
        );
    }
}
