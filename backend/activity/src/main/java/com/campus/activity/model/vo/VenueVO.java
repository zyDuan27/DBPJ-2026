package com.campus.activity.model.vo;

import com.campus.activity.model.row.VenueRow;

public record VenueVO(Integer id,
                      String venueName,
                      String roomNumber,
                      Integer capacity,
                      Integer campusId,
                      String campusName) {
    public static VenueVO from(VenueRow row) {
        return new VenueVO(
                row.getId(),
                row.getVenueName(),
                row.getRoomNumber(),
                row.getCapacity(),
                row.getCampusId(),
                row.getCampusName()
        );
    }
}
