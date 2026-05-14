package com.campus.activity.model.vo;

import com.campus.activity.model.row.CampusRow;

public record CampusVO(Integer id, String campusName, String location) {
    public static CampusVO from(CampusRow row) {
        return new CampusVO(
                row.getId(),
                row.getCampusName(),
                row.getLocation()
        );
    }
}
