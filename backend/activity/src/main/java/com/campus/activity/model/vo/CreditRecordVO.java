package com.campus.activity.model.vo;

import com.campus.activity.model.row.CreditRecordRow;

import java.time.LocalDateTime;

public record CreditRecordVO(Integer recordId,
                             Integer changeValue,
                             String reasonType,
                             String reason,
                             LocalDateTime createdAt,
                             Integer activityId,
                             String activityTitle) {
    public static CreditRecordVO from(CreditRecordRow row) {
        return new CreditRecordVO(
                row.getRecordId(),
                row.getChangeValue(),
                row.getReasonType(),
                row.getReason(),
                row.getCreatedAt(),
                row.getActivityId(),
                row.getActivityTitle()
        );
    }
}
