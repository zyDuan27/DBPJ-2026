package com.campus.activity.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.model.entity.Registration;
import com.campus.activity.model.row.CheckInTargetRow;
import com.campus.activity.model.row.RegistrationActionRow;
import com.campus.activity.model.row.RegistrationListItemRow;
import com.campus.activity.model.row.RegistrationLockRow;
import com.campus.activity.model.row.RegistrationNotifyRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {
    @Select("""
            SELECT r.registration_id AS registrationId, r.student_id AS studentId,
                   r.status, a.end_time AS endTime
            FROM Registration r
            JOIN Activity a ON r.activity_id = a.activity_id
            WHERE r.registration_id = #{registrationId}
            """)
    CheckInTargetRow findCheckInCodeTarget(@Param("registrationId") int registrationId);

    @Select("""
            SELECT r.registration_id AS registrationId, r.status, a.organizer_id AS organizerId
            FROM Registration r
            JOIN Activity a ON r.activity_id = a.activity_id
            WHERE r.registration_id = #{registrationId}
            FOR UPDATE
            """)
    CheckInTargetRow findCheckInTargetForUpdate(@Param("registrationId") int registrationId);

    @Select("""
            SELECT r.registration_id AS registrationId, r.student_id AS studentId,
                   r.activity_id AS activityId, a.title
            FROM Registration r
            JOIN Activity a ON r.activity_id = a.activity_id
            WHERE r.registration_id = #{registrationId}
            """)
    RegistrationNotifyRow findNotificationTarget(@Param("registrationId") int registrationId);

    @Select("""
            SELECT r.registration_id AS registrationId, r.student_id AS studentId,
                   r.activity_id AS activityId, a.title
            FROM Registration r
            JOIN Activity a ON r.activity_id = a.activity_id
            WHERE r.activity_id = #{activityId} AND r.status = 'ENROLLED'
            """)
    List<RegistrationNotifyRow> findEnrolledNotificationTargets(@Param("activityId") int activityId);

    @Select("""
            SELECT student_id
            FROM Registration
            WHERE activity_id = #{activityId}
              AND status IN ('ENROLLED', 'WAITLISTED', 'CHECKED_IN')
            """)
    List<Integer> findActiveStudentIds(@Param("activityId") int activityId);

    @Update("""
            UPDATE Registration
            SET status = 'CHECKED_IN', check_in_time = CURRENT_TIMESTAMP
            WHERE registration_id = #{registrationId}
            """)
    int markCheckedIn(@Param("registrationId") int registrationId);

    @Select("""
            SELECT registration_id AS registrationId, status AS registrationStatus, queue_no AS queueNo
            FROM Registration
            WHERE student_id = #{studentId} AND activity_id = #{activityId}
            """)
    RegistrationActionRow findReusableRegistration(@Param("studentId") int studentId,
                                                   @Param("activityId") int activityId);

    @Select("""
            SELECT registration_id AS registrationId
            FROM Registration
            WHERE student_id = #{studentId} AND activity_id = #{activityId}
            """)
    Integer findRegistrationId(@Param("studentId") int studentId, @Param("activityId") int activityId);

    @Insert("""
            INSERT INTO Registration(student_id, activity_id, status, queue_no)
            VALUES (#{studentId}, #{activityId}, #{status}, #{queueNo})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "registrationId", keyColumn = "registration_id")
    int insertRegistration(Registration registration);

    @Update("""
            UPDATE Registration
            SET status = #{status}, queue_no = #{queueNo},
                registration_time = CURRENT_TIMESTAMP, check_in_time = NULL
            WHERE registration_id = #{registrationId}
            """)
    int updateEnrollment(Registration registration);

    @Update("""
            UPDATE Registration
            SET status = 'CANCELLED', queue_no = NULL
            WHERE registration_id = #{registrationId}
            """)
    int cancelRegistration(@Param("registrationId") int registrationId);

    @Select("""
            SELECT COALESCE(MAX(queue_no), 0) + 1
            FROM Registration
            WHERE activity_id = #{activityId} AND status = 'WAITLISTED'
            """)
    Integer nextWaitlistQueueNo(@Param("activityId") int activityId);

    @Select("""
            SELECT registration_id
            FROM Registration
            WHERE activity_id = #{activityId} AND status = 'WAITLISTED'
            ORDER BY queue_no ASC, registration_time ASC
            LIMIT 1
            """)
    Integer findNextWaitlisted(@Param("activityId") int activityId);

    @Update("""
            UPDATE Registration
            SET status = 'ENROLLED', queue_no = NULL
            WHERE registration_id = #{registrationId}
            """)
    int promoteRegistration(@Param("registrationId") int registrationId);

    @Select("""
            SELECT registration_id AS registrationId, student_id AS studentId,
                   activity_id AS activityId, status
            FROM Registration
            WHERE registration_id = #{registrationId}
            FOR UPDATE
            """)
    RegistrationLockRow lockRegistration(@Param("registrationId") int registrationId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM Registration r
            JOIN Activity a ON r.activity_id = a.activity_id
            JOIN Venue v ON a.venue_id = v.venue_id
            JOIN Campus c ON v.campus_id = c.campus_id
            WHERE r.student_id = #{studentId}
            <if test="status != null and status != ''">AND r.status = #{status}</if>
            </script>
            """)
    Long countMine(@Param("studentId") int studentId, @Param("status") String status);

    @Select("""
            <script>
            SELECT r.registration_id AS registrationId, r.status AS registrationStatus, r.queue_no AS queueNo,
                   r.registration_time AS registrationTime, r.check_in_time AS checkInTime,
                   a.activity_id AS activityId, a.title, a.start_time AS startTime, a.end_time AS endTime,
                   c.campus_name AS campusName, v.venue_name AS venueName, v.room_number AS roomNumber
            FROM Registration r
            JOIN Activity a ON r.activity_id = a.activity_id
            JOIN Venue v ON a.venue_id = v.venue_id
            JOIN Campus c ON v.campus_id = c.campus_id
            WHERE r.student_id = #{studentId}
            <if test="status != null and status != ''">AND r.status = #{status}</if>
            ORDER BY a.start_time DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<RegistrationListItemRow> listMine(@Param("studentId") int studentId,
                                           @Param("status") String status,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM Registration r
            JOIN User u ON r.student_id = u.user_id
            WHERE r.activity_id = #{activityId}
            <if test="status != null and status != ''">AND r.status = #{status}</if>
            </script>
            """)
    Long countActivityRegistrations(@Param("activityId") int activityId, @Param("status") String status);

    @Select("""
            <script>
            SELECT r.registration_id AS registrationId, u.username AS studentName, u.student_no AS studentNo,
                   u.phone, r.status AS registrationStatus, r.queue_no AS queueNo,
                   r.registration_time AS registrationTime, r.check_in_time AS checkInTime
            FROM Registration r
            JOIN User u ON r.student_id = u.user_id
            WHERE r.activity_id = #{activityId}
            <if test="status != null and status != ''">AND r.status = #{status}</if>
            ORDER BY r.status, r.queue_no, r.registration_time
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<RegistrationListItemRow> listActivityRegistrations(@Param("activityId") int activityId,
                                                            @Param("status") String status,
                                                            @Param("offset") int offset,
                                                            @Param("size") int size);

    @Update("""
            UPDATE Registration
            SET status = 'ABSENT'
            WHERE activity_id = #{activityId} AND status = 'ENROLLED'
            """)
    int markAbsences(@Param("activityId") int activityId);
}
