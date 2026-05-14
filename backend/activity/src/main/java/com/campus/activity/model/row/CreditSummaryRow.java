package com.campus.activity.model.row;

public class CreditSummaryRow {
    private Long recordCount;
    private Long totalChange;
    private Long absentCount;
    private Long checkInCreditCount;

    public Long getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Long recordCount) {
        this.recordCount = recordCount;
    }

    public Long getTotalChange() {
        return totalChange;
    }

    public void setTotalChange(Long totalChange) {
        this.totalChange = totalChange;
    }

    public Long getAbsentCount() {
        return absentCount;
    }

    public void setAbsentCount(Long absentCount) {
        this.absentCount = absentCount;
    }

    public Long getCheckInCreditCount() {
        return checkInCreditCount;
    }

    public void setCheckInCreditCount(Long checkInCreditCount) {
        this.checkInCreditCount = checkInCreditCount;
    }
}
