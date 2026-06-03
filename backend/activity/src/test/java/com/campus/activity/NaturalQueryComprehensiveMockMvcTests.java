package com.campus.activity;

import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.Role;
import com.campus.activity.service.AuthService;
import com.campus.activity.service.LlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "llm.enabled=true",
        "llm.api-key=test-key",
        "llm.sql-mode=CONTROLLED_ALL",
        "llm.controlled-sql-enabled=true",
        "llm.admin-sql-enabled=true",
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
class NaturalQueryComprehensiveMockMvcTests {
    private static final String TEST_PASSWORD_HASH =
            "pbkdf2$120000$ZGJwai0yMDI2LXRlc3Qtc2VlZA==$Z7kG5pEEo62BsGNBwn7JqwppwrnIYLeR4lTvLrVzz6M=";

    private final List<Integer> activityIds = new ArrayList<>();
    private final List<Integer> registrationIds = new ArrayList<>();
    private final List<Integer> feedbackIds = new ArrayList<>();
    private final List<Integer> venueIds = new ArrayList<>();
    private final List<Integer> categoryIds = new ArrayList<>();
    private final List<Integer> campusIds = new ArrayList<>();
    private final List<Integer> userIds = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthService authService;

    @Test
    void studentCanQueryActivitiesByDescriptionDateAndCategoryTogether() throws Exception {
        int organizerId = insertUser("ORGANIZER", "semantic-org-" + uniqueSuffix(), null, uniquePhone("181"));
        int studentId = insertUser("STUDENT", "semantic-student-" + uniqueSuffix(), "SQ" + uniqueSuffix(), uniquePhone("182"));
        int campusId = insertCampus("语义校区", "semantic-location");
        int venueId = insertVenue("普通楼", "S-101", 80, campusId);
        int volunteerCategoryId = insertCategory("志愿服务");
        int lectureCategoryId = insertCategory("讲座");
        LocalDateTime tomorrow = LocalDate.now().plusDays(1).atTime(10, 0);

        String expectedTitle = "数据库志愿实践-" + uniqueSuffix();
        String wrongCategoryTitle = "数据库讲座-" + uniqueSuffix();
        String wrongDateTitle = "数据库后天志愿-" + uniqueSuffix();
        insertActivity(expectedTitle, venueId, volunteerCategoryId, tomorrow, tomorrow.plusHours(2),
                80, 0, "数据库系统志愿服务实践", "PUBLISHED", organizerId);
        insertActivity(wrongCategoryTitle, venueId, lectureCategoryId, tomorrow, tomorrow.plusHours(2),
                80, 0, "数据库系统专题讲座", "PUBLISHED", organizerId);
        insertActivity(wrongDateTitle, venueId, volunteerCategoryId, tomorrow.plusDays(1), tomorrow.plusDays(1).plusHours(2),
                80, 0, "数据库系统志愿服务实践", "PUBLISHED", organizerId);

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(studentId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询明天和数据库相关的志愿活动","page":1,"size":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("CONTROLLED_SQL"))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", hasItem(expectedTitle)))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", not(hasItem(wrongCategoryTitle))))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", not(hasItem(wrongDateTitle))));
    }

    @Test
    void studentCanQueryFeedbackByRatingAndMultiDimEvaluatedActivities() throws Exception {
        int organizerId = insertUser("ORGANIZER", "feedback-org-" + uniqueSuffix(), null, uniquePhone("183"));
        int studentId = insertUser("STUDENT", "feedback-student-" + uniqueSuffix(), "FS" + uniqueSuffix(), uniquePhone("184"));
        int campusId = insertCampus("邯郸校区", "feedback-location");
        int guanghuaVenueId = insertVenue("光华楼", "G-201", 120, campusId);
        int otherVenueId = insertVenue("逸夫楼", "Y-101", 120, campusId);
        int categoryId = insertCategory("技术分享");
        LocalDateTime start = LocalDate.now().plusDays(2).atTime(14, 0);

        String highEnrollmentTitle = "高参与评价活动-" + uniqueSuffix();
        String lowEnrollmentTitle = "低参与评价活动-" + uniqueSuffix();
        String otherVenueTitle = "其他场地评价活动-" + uniqueSuffix();
        int highActivityId = insertActivity(highEnrollmentTitle, guanghuaVenueId, categoryId, start, start.plusHours(2),
                120, 30, "高参与人数活动", "PUBLISHED", organizerId);
        int lowActivityId = insertActivity(lowEnrollmentTitle, guanghuaVenueId, categoryId, start.plusHours(3), start.plusHours(5),
                120, 5, "低参与人数活动", "PUBLISHED", organizerId);
        int otherVenueActivityId = insertActivity(otherVenueTitle, otherVenueId, categoryId, start.plusHours(6), start.plusHours(8),
                120, 80, "其他场地活动", "PUBLISHED", organizerId);
        insertCheckedInFeedback(studentId, highActivityId, 5, "体验很好");
        insertCheckedInFeedback(studentId, lowActivityId, 3, "一般");
        insertCheckedInFeedback(studentId, otherVenueActivityId, 5, "其他场地很好");

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(studentId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询我的高评分活动反馈","page":1,"size":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", hasItem(highEnrollmentTitle)))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", hasItem(otherVenueTitle)))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", not(hasItem(lowEnrollmentTitle))));

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(studentId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询我评价过的活动中，在光华楼举办的，参与人数较多的活动","page":1,"size":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.rows[0].activityTitle").value(highEnrollmentTitle))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", hasItem(lowEnrollmentTitle)))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", not(hasItem(otherVenueTitle))));
    }

    @Test
    void organizerCanQueryAvailableVenueCandidatesAndParticipantSummary() throws Exception {
        int organizerId = insertUser("ORGANIZER", "organizer-query-" + uniqueSuffix(), null, uniquePhone("185"));
        int studentOneId = insertUser("STUDENT", "participant-one-" + uniqueSuffix(), "P1" + uniqueSuffix(), uniquePhone("186"));
        int studentTwoId = insertUser("STUDENT", "participant-two-" + uniqueSuffix(), "P2" + uniqueSuffix(), uniquePhone("187"));
        int campusId = insertCampus("组织者校区", "organizer-location");
        int bigVenueId = insertVenue("大报告厅", "B-501", 100, campusId);
        int smallVenueId = insertVenue("小教室", "S-101", 30, campusId);
        int categoryId = insertCategory("社团活动");
        LocalDateTime start = LocalDate.now().plusDays(3).atTime(9, 0);
        int firstActivityId = insertActivity("组织者活动一-" + uniqueSuffix(), bigVenueId, categoryId,
                start, start.plusHours(2), 100, 2, "组织者测试活动", "PUBLISHED", organizerId);
        int secondActivityId = insertActivity("组织者活动二-" + uniqueSuffix(), bigVenueId, categoryId,
                start.plusDays(1), start.plusDays(1).plusHours(2), 100, 1, "组织者测试活动", "PUBLISHED", organizerId);
        insertRegistration(studentOneId, firstActivityId, "CHECKED_IN");
        insertRegistration(studentOneId, secondActivityId, "ENROLLED");
        insertRegistration(studentTwoId, firstActivityId, "ENROLLED");

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(organizer(organizerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询容量至少50人的可申请活动场地","page":1,"size":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.rows[*].venueName", hasItem("大报告厅")))
                .andExpect(jsonPath("$.data.rows[*].venueName", not(hasItem("小教室"))));

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(organizer(organizerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询参与过我创建活动的学生，按参与次数排序","page":1,"size":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.rows[0].studentName").value(jdbcTemplate.queryForObject(
                        "SELECT username FROM User WHERE user_id = ?", String.class, studentOneId)))
                .andExpect(jsonPath("$.data.rows[0].participationCount").value(2));
    }

    @Test
    void adminCanQueryVenueOccupancyWithAdminSqlDraft() throws Exception {
        int adminId = insertUser("ADMIN", "admin-occupancy-" + uniqueSuffix(), null, uniquePhone("188"));
        int organizerId = insertUser("ORGANIZER", "occupancy-org-" + uniqueSuffix(), null, uniquePhone("189"));
        int campusId = insertCampus("占用校区", "occupancy-location");
        int venueId = insertVenue("占用测试场地", "O-301", 80, campusId);
        int categoryId = insertCategory("测试类型");
        LocalDateTime tomorrow = LocalDate.now().plusDays(1).atTime(15, 0);
        insertActivity("明天场地占用活动-" + uniqueSuffix(), venueId, categoryId,
                tomorrow, tomorrow.plusHours(2), 80, 10, "管理员场地占用测试", "PUBLISHED", organizerId);

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(admin(adminId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询明天各场地活动占用情况","page":1,"size":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("ADMIN_SQL"))
                .andExpect(jsonPath("$.data.sqlPreview").exists())
                .andExpect(jsonPath("$.data.rows[*].venueName", hasItem("占用测试场地")))
                .andExpect(jsonPath("$.data.rows[0].activityCount").value(1));
    }

    @AfterEach
    void cleanUp() {
        for (Integer feedbackId : feedbackIds) {
            jdbcTemplate.update("DELETE FROM ActivityFeedback WHERE feedback_id = ?", feedbackId);
        }
        for (Integer registrationId : registrationIds) {
            jdbcTemplate.update("DELETE FROM Registration WHERE registration_id = ?", registrationId);
        }
        for (Integer activityId : activityIds) {
            jdbcTemplate.update("DELETE FROM Activity WHERE activity_id = ?", activityId);
        }
        for (Integer venueId : venueIds) {
            jdbcTemplate.update("DELETE FROM Venue WHERE venue_id = ?", venueId);
        }
        for (Integer categoryId : categoryIds) {
            jdbcTemplate.update("DELETE FROM Category WHERE category_id = ?", categoryId);
        }
        for (Integer campusId : campusIds) {
            jdbcTemplate.update("DELETE FROM Campus WHERE campus_id = ?", campusId);
        }
        for (Integer userId : userIds) {
            jdbcTemplate.update("DELETE FROM Notification WHERE recipient_id = ?", userId);
            jdbcTemplate.update("DELETE FROM User WHERE user_id = ?", userId);
        }
        feedbackIds.clear();
        registrationIds.clear();
        activityIds.clear();
        venueIds.clear();
        categoryIds.clear();
        campusIds.clear();
        userIds.clear();
    }

    private int insertCampus(String name, String location) {
        return insertAndTrack(campusIds, """
                INSERT INTO Campus(campus_name, location)
                VALUES (?, ?)
                """, name + "-" + uniqueSuffix(), location);
    }

    private int insertVenue(String name, String roomNumber, int capacity, int campusId) {
        return insertAndTrack(venueIds, """
                INSERT INTO Venue(venue_name, room_number, capacity, campus_id)
                VALUES (?, ?, ?, ?)
                """, name, roomNumber + "-" + uniqueSuffix(), capacity, campusId);
    }

    private int insertCategory(String name) {
        return insertAndTrack(categoryIds, """
                INSERT INTO Category(category_name)
                VALUES (?)
                """, name + "-" + uniqueSuffix());
    }

    private int insertActivity(String title, int venueId, int categoryId, LocalDateTime startTime, LocalDateTime endTime,
                               int capacity, int currentEnrollment, String description, String status, int organizerId) {
        return insertAndTrack(activityIds, """
                INSERT INTO Activity(title, venue_id, category_id, start_time, end_time, enroll_deadline,
                                     capacity_limit, current_enrollment, description, status, organizer_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, title, venueId, categoryId, startTime, endTime, startTime.minusHours(1),
                capacity, currentEnrollment, description, status, organizerId);
    }

    private int insertRegistration(int studentId, int activityId, String status) {
        Object checkInTime = "CHECKED_IN".equals(status) ? LocalDateTime.now() : null;
        return insertAndTrack(registrationIds, """
                INSERT INTO Registration(student_id, activity_id, status, check_in_time)
                VALUES (?, ?, ?, ?)
                """, studentId, activityId, status, checkInTime);
    }

    private void insertCheckedInFeedback(int studentId, int activityId, int rating, String content) {
        int registrationId = insertRegistration(studentId, activityId, "CHECKED_IN");
        insertAndTrack(feedbackIds, """
                INSERT INTO ActivityFeedback(registration_id, activity_id, student_id, rating, content)
                VALUES (?, ?, ?, ?, ?)
                """, registrationId, activityId, studentId, rating, content);
    }

    private int insertUser(String role, String username, String studentNo, String phone) {
        return insertAndTrack(userIds, """
                INSERT INTO User(role, username, student_no, password, phone)
                VALUES (?, ?, ?, ?, ?)
                """, role, username, studentNo, TEST_PASSWORD_HASH, phone);
    }

    private int insertAndTrack(List<Integer> target, String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        int id = keyHolder.getKey().intValue();
        target.add(id);
        return id;
    }

    private String uniqueSuffix() {
        return Long.toUnsignedString(System.nanoTime(), 36);
    }

    private String uniquePhone(String prefix) {
        return prefix + Long.toUnsignedString(System.nanoTime(), 36);
    }

    private CurrentUser student(int id) {
        return new CurrentUser(id, "student-" + id, Role.STUDENT, "S" + id, "10000000000");
    }

    private CurrentUser organizer(int id) {
        return new CurrentUser(id, "organizer-" + id, Role.ORGANIZER, null, "10000000000");
    }

    private CurrentUser admin(int id) {
        return new CurrentUser(id, "admin-" + id, Role.ADMIN, null, "10000000000");
    }

    private String bearer(CurrentUser user) {
        return "Bearer " + authService.issueToken(user);
    }

    @TestConfiguration
    static class StubLlmConfig {
        private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Bean("openAiCompatibleLlmClient")
        LlmClient llmClient() {
            return (systemPrompt, userPrompt) -> {
                if (userPrompt.contains("数据库相关的志愿活动")) {
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    return controlledSql("""
                              SELECT activityId, activityTitle, startTime, categoryName
                              FROM activity_view
                              WHERE description LIKE '%%数据库%%'
                                AND categoryName LIKE '%%志愿%%'
                                AND startTime >= '%s'
                                AND startTime < '%s'
                            ORDER BY startTime ASC
                            LIMIT 20
                            """.formatted(tomorrow.atStartOfDay().format(SQL_TIME), tomorrow.plusDays(1).atStartOfDay().format(SQL_TIME)),
                            "查询明天和数据库相关的志愿活动。");
                }
                if (userPrompt.contains("高评分活动反馈")) {
                    return controlledSql("""
                            SELECT feedbackId, activityTitle, rating, content, updatedAt
                            FROM my_feedback_view
                            WHERE rating >= 4
                            ORDER BY rating DESC, updatedAt DESC
                            LIMIT 20
                            """, "查询你的高评分活动反馈。");
                }
                if (userPrompt.contains("评价过的活动中") && userPrompt.contains("光华楼")) {
                    return controlledSql("""
                            SELECT fv.activityTitle AS activityTitle, fv.rating AS rating, av.currentEnrollment AS currentEnrollment, fv.venueName AS venueName
                            FROM my_feedback_view fv
                            JOIN activity_view av ON fv.activityId = av.activityId
                            WHERE fv.venueName LIKE '%光华楼%'
                            ORDER BY av.currentEnrollment DESC
                            LIMIT 20
                            """, "查询你评价过且在光华楼举办的活动，并按参与人数排序。");
                }
                if (userPrompt.contains("容量至少50")) {
                    return controlledSql("""
                            SELECT venueId, venueName, roomNumber, capacity, campusName
                            FROM venue_view
                            WHERE capacity >= 50
                            ORDER BY capacity DESC
                            LIMIT 20
                            """, "查询容量至少 50 人的场地候选。");
                }
                if (userPrompt.contains("参与过我创建活动的学生")) {
                    return controlledSql("""
                            SELECT studentName, studentNo, participationCount, recentParticipationTime
                            FROM organizer_participant_view
                            ORDER BY participationCount DESC, recentParticipationTime DESC
                            LIMIT 20
                            """, "查询参与过你创建活动的学生汇总。");
                }
                if (userPrompt.contains("场地活动占用情况")) {
                    LocalDate tomorrow = LocalDate.now().plusDays(1);
                    return """
                            {
                              "queryMode": "ADMIN_SQL",
                              "sql": "SELECT v.venue_name AS venueName, COUNT(a.activity_id) AS activityCount FROM Venue v JOIN Activity a ON a.venue_id = v.venue_id WHERE a.start_time >= '%s' AND a.start_time < '%s' GROUP BY v.venue_id, v.venue_name ORDER BY activityCount DESC LIMIT 20",
                              "summaryHint": "查询明天各场地活动占用情况。",
                              "ambiguity": false,
                              "clarificationOptions": []
                            }
                            """.formatted(tomorrow.atStartOfDay().format(SQL_TIME), tomorrow.plusDays(1).atStartOfDay().format(SQL_TIME));
                }
                return controlledSql("SELECT activityId, activityTitle FROM activity_view LIMIT 20", "默认活动查询。");
            };
        }

        private String controlledSql(String sql, String summaryHint) {
            return """
                    {
                      "queryMode": "CONTROLLED_SQL",
                      "sql": "%s",
                      "summaryHint": "%s",
                      "ambiguity": false,
                      "clarificationOptions": []
                    }
                    """.formatted(sql.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("\\s+", " ").trim(),
                    summaryHint.replace("\"", "\\\""));
        }
    }
}
