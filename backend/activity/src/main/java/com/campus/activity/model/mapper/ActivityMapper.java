package com.campus.activity.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.model.entity.Activity;
import com.campus.activity.model.row.ActivityDetailRow;
import com.campus.activity.model.row.ActivityListItemRow;
import com.campus.activity.model.row.ActivityLockRow;
import com.campus.activity.model.row.ActivityNotifyRow;
import com.campus.activity.model.row.StudentRegistrationRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM Activity a
            JOIN Venue v ON a.venue_id = v.venue_id
            JOIN Campus c ON v.campus_id = c.campus_id
            JOIN Category cat ON a.category_id = cat.category_id
            JOIN User u ON a.organizer_id = u.user_id
            <where>
                <if test="studentVisible">AND a.status IN ('PUBLISHED', 'ONGOING', 'FINISHED')</if>
                <if test="organizerId != null">AND a.organizer_id = #{organizerId}</if>
                <if test="keyword != null and keyword != ''">AND a.title LIKE CONCAT('%', #{keyword}, '%')</if>
                <if test="campusId != null">AND c.campus_id = #{campusId}</if>
                <if test="categoryId != null">AND a.category_id = #{categoryId}</if>
                <if test="status != null and status != ''">AND a.status = #{status}</if>
            </where>
            </script>
            """)
    Long countActivities(@Param("studentVisible") boolean studentVisible,
                         @Param("organizerId") Integer organizerId,
                         @Param("keyword") String keyword,
                         @Param("campusId") Integer campusId,
                         @Param("categoryId") Integer categoryId,
                         @Param("status") String status);

    @Select("""
            <script>
            SELECT a.activity_id AS id, a.title, a.poster_url AS posterUrl,
                   a.start_time AS startTime, a.end_time AS endTime, a.enroll_deadline AS enrollDeadline,
                   c.campus_name AS campusName, v.venue_name AS venueName, v.room_number AS roomNumber,
                   cat.category_name AS categoryName, a.venue_id AS venueId, a.category_id AS categoryId,
                   a.capacity_limit AS capacityLimit,
                   a.current_enrollment AS currentEnrollment, a.status, a.reject_reason AS rejectReason,
                   u.username AS organizerName
            FROM Activity a
            JOIN Venue v ON a.venue_id = v.venue_id
            JOIN Campus c ON v.campus_id = c.campus_id
            JOIN Category cat ON a.category_id = cat.category_id
            JOIN User u ON a.organizer_id = u.user_id
            <where>
                <if test="studentVisible">AND a.status IN ('PUBLISHED', 'ONGOING', 'FINISHED')</if>
                <if test="organizerId != null">AND a.organizer_id = #{organizerId}</if>
                <if test="keyword != null and keyword != ''">AND a.title LIKE CONCAT('%', #{keyword}, '%')</if>
                <if test="campusId != null">AND c.campus_id = #{campusId}</if>
                <if test="categoryId != null">AND a.category_id = #{categoryId}</if>
                <if test="status != null and status != ''">AND a.status = #{status}</if>
            </where>
            ORDER BY a.start_time DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<ActivityListItemRow> listActivities(@Param("studentVisible") boolean studentVisible,
                                             @Param("organizerId") Integer organizerId,
                                             @Param("keyword") String keyword,
                                             @Param("campusId") Integer campusId,
                                             @Param("categoryId") Integer categoryId,
                                             @Param("status") String status,
                                             @Param("offset") int offset,
                                             @Param("size") int size);

    @Select("""
            SELECT a.activity_id AS id, a.title, a.poster_url AS posterUrl, a.description,
                   a.start_time AS startTime, a.end_time AS endTime, a.enroll_deadline AS enrollDeadline,
                   c.campus_name AS campusName, v.venue_name AS venueName, v.room_number AS roomNumber,
                   cat.category_name AS categoryName, a.venue_id AS venueId, a.category_id AS categoryId,
                   a.capacity_limit AS capacityLimit, a.current_enrollment AS currentEnrollment,
                   a.status, a.reject_reason AS rejectReason,
                   u.username AS organizerName, a.organizer_id AS organizerId
            FROM Activity a
            JOIN Venue v ON a.venue_id = v.venue_id
            JOIN Campus c ON v.campus_id = c.campus_id
            JOIN Category cat ON a.category_id = cat.category_id
            JOIN User u ON a.organizer_id = u.user_id
            WHERE a.activity_id = #{activityId}
            """)
    ActivityDetailRow findDetail(@Param("activityId") int activityId);

    @Insert("""
            INSERT INTO Activity(title, venue_id, category_id, start_time, end_time, enroll_deadline,
                                 capacity_limit, poster_url, description, status, organizer_id)
            VALUES (#{title}, #{venueId}, #{categoryId}, #{startTime}, #{endTime}, #{enrollDeadline},
                    #{capacityLimit}, #{posterUrl}, #{description}, #{status}, #{organizerId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "activityId", keyColumn = "activity_id")
    int insertActivity(Activity activity);

    @Update("""
            UPDATE Activity
            SET title = #{title}, venue_id = #{venueId}, category_id = #{categoryId},
                start_time = #{startTime}, end_time = #{endTime}, enroll_deadline = #{enrollDeadline},
                capacity_limit = #{capacityLimit}, poster_url = #{posterUrl}, description = #{description}
            WHERE activity_id = #{activityId}
            """)
    int updateActivity(Activity activity);

    @Update("""
            UPDATE Activity
            SET status = 'PENDING_REVIEW', reject_reason = NULL
            WHERE activity_id = #{activityId}
            """)
    int submitForReview(@Param("activityId") int activityId);

    @Update("""
            UPDATE Activity
            SET status = 'CANCELLED'
            WHERE activity_id = #{activityId}
            """)
    int cancelActivity(@Param("activityId") int activityId);

    @Update("""
            UPDATE Activity
            SET status = 'PUBLISHED', admin_id = #{adminId}, reject_reason = NULL
            WHERE activity_id = #{activityId}
            """)
    int approveActivity(@Param("activityId") int activityId, @Param("adminId") int adminId);

    @Update("""
            UPDATE Activity
            SET status = 'REJECTED', admin_id = #{adminId}, reject_reason = #{reason}
            WHERE activity_id = #{activityId}
            """)
    int rejectActivity(@Param("activityId") int activityId,
                       @Param("adminId") int adminId,
                       @Param("reason") String reason);

    @Select("SELECT status FROM Activity WHERE activity_id = #{activityId}")
    String findStatus(@Param("activityId") int activityId);

    @Select("SELECT organizer_id FROM Activity WHERE activity_id = #{activityId}")
    Integer findOrganizerId(@Param("activityId") int activityId);

    @Select("""
            SELECT activity_id AS activityId, title, organizer_id AS organizerId
            FROM Activity
            WHERE activity_id = #{activityId}
            """)
    ActivityNotifyRow findNotificationTarget(@Param("activityId") int activityId);

    @Select("""
            SELECT COUNT(*)
            FROM Activity target
            JOIN Activity other ON target.venue_id = other.venue_id
            WHERE target.activity_id = #{activityId}
              AND other.activity_id <> target.activity_id
              AND other.status IN ('PENDING_REVIEW', 'PUBLISHED', 'ONGOING')
              AND target.start_time < other.end_time
              AND target.end_time > other.start_time
            """)
    Integer countVenueConflicts(@Param("activityId") int activityId);

    @Select("""
            SELECT registration_id AS registrationId, status AS registrationStatus
            FROM Registration
            WHERE student_id = #{studentId} AND activity_id = #{activityId}
            """)
    StudentRegistrationRow findStudentRegistration(@Param("studentId") int studentId,
                                                   @Param("activityId") int activityId);

    @Select("""
            SELECT activity_id AS activityId, status, current_enrollment AS currentEnrollment,
                   capacity_limit AS capacityLimit, enroll_deadline AS enrollDeadline,
                   end_time AS endTime
            FROM Activity
            WHERE activity_id = #{activityId}
            FOR UPDATE
            """)
    ActivityLockRow lockActivity(@Param("activityId") int activityId);

    @Update("""
            UPDATE Activity
            SET current_enrollment = current_enrollment + 1
            WHERE activity_id = #{activityId}
            """)
    int incrementEnrollment(@Param("activityId") int activityId);

    @Update("""
            UPDATE Activity
            SET current_enrollment = current_enrollment - 1
            WHERE activity_id = #{activityId}
            """)
    int decrementEnrollment(@Param("activityId") int activityId);

    @Update("""
            UPDATE Activity
            SET current_enrollment = GREATEST(current_enrollment - #{count}, 0)
            WHERE activity_id = #{activityId}
            """)
    int decreaseEnrollmentBy(@Param("activityId") int activityId, @Param("count") int count);

}
