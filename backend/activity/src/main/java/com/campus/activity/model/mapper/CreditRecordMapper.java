package com.campus.activity.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.model.entity.CreditRecord;
import com.campus.activity.model.row.CreditRecordRow;
import com.campus.activity.model.row.CreditRiskStudentRow;
import com.campus.activity.model.row.CreditSummaryRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CreditRecordMapper extends BaseMapper<CreditRecord> {
    @Select("""
            SELECT COUNT(*) AS recordCount,
                   COALESCE(SUM(change_value), 0) AS totalChange,
                   SUM(CASE WHEN reason_type = 'ABSENT' THEN 1 ELSE 0 END) AS absentCount,
                   SUM(CASE WHEN reason_type = 'CHECK_IN' THEN 1 ELSE 0 END) AS checkInCreditCount
            FROM CreditRecord
            """)
    CreditSummaryRow overviewSummary();

    @Select("""
            SELECT u.user_id AS studentId, u.username AS studentName, u.student_no AS studentNo,
                   100 + COALESCE(SUM(c.change_value), 0) AS creditScore,
                   SUM(CASE WHEN c.reason_type = 'ABSENT' THEN 1 ELSE 0 END) AS absentCount
            FROM User u
            LEFT JOIN CreditRecord c ON u.user_id = c.student_id
            WHERE u.role = 'STUDENT'
            GROUP BY u.user_id, u.username, u.student_no
            HAVING creditScore < 100 OR absentCount > 0
            ORDER BY creditScore ASC, absentCount DESC
            LIMIT 10
            """)
    List<CreditRiskStudentRow> riskStudents();

    @Select("""
            SELECT COALESCE(SUM(change_value), 0)
            FROM CreditRecord
            WHERE student_id = #{studentId}
            """)
    Long totalChange(@Param("studentId") int studentId);

    @Select("SELECT COUNT(*) FROM CreditRecord WHERE student_id = #{studentId}")
    Long countStudentRecords(@Param("studentId") int studentId);

    @Select("""
            SELECT c.record_id AS recordId, c.change_value AS changeValue, c.reason_type AS reasonType,
                   c.reason, c.created_at AS createdAt,
                   a.activity_id AS activityId, a.title AS activityTitle
            FROM CreditRecord c
            LEFT JOIN Activity a ON c.activity_id = a.activity_id
            WHERE c.student_id = #{studentId}
            ORDER BY c.created_at DESC, c.record_id DESC
            LIMIT #{offset}, #{size}
            """)
    List<CreditRecordRow> studentRecords(@Param("studentId") int studentId,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    @Insert("""
            INSERT IGNORE INTO CreditRecord(student_id, activity_id, registration_id, change_value, reason_type, reason, operator_id)
            SELECT r.student_id, r.activity_id, r.registration_id, 1, 'CHECK_IN', '按时完成活动签到', #{operatorId}
            FROM Registration r
            WHERE r.registration_id = #{registrationId}
            """)
    int insertCheckInCredit(@Param("operatorId") int operatorId, @Param("registrationId") int registrationId);

    @Insert("""
            INSERT IGNORE INTO CreditRecord(student_id, activity_id, registration_id, change_value, reason_type, reason, operator_id)
            SELECT student_id, activity_id, registration_id, -10, 'ABSENT', '活动结束后未完成签到', #{operatorId}
            FROM Registration
            WHERE activity_id = #{activityId} AND status = 'ABSENT'
            """)
    int insertAbsenceCredits(@Param("activityId") int activityId, @Param("operatorId") int operatorId);
}
