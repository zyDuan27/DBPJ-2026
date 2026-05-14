package com.campus.activity.model.vo;

import com.campus.activity.model.row.CreditRiskStudentRow;

public record CreditRiskStudentVO(Integer studentId,
                                  String studentName,
                                  String studentNo,
                                  Long creditScore,
                                  Long absentCount) {
    public static CreditRiskStudentVO from(CreditRiskStudentRow row) {
        return new CreditRiskStudentVO(
                row.getStudentId(),
                row.getStudentName(),
                row.getStudentNo(),
                row.getCreditScore(),
                row.getAbsentCount()
        );
    }
}
