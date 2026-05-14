package com.campus.activity.model.vo;

import com.campus.activity.model.row.CreditSummaryRow;

public record CreditSummaryVO(Long recordCount, Long totalChange, Long absentCount, Long checkInCreditCount) {
    public static CreditSummaryVO from(CreditSummaryRow row) {
        return new CreditSummaryVO(
                row.getRecordCount(),
                row.getTotalChange(),
                row.getAbsentCount(),
                row.getCheckInCreditCount()
        );
    }
}
