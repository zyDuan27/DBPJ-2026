package com.campus.activity;

import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.Role;
import com.campus.activity.service.AuthService;
import com.campus.activity.service.ControlledSqlCompiler;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
class NaturalQueryControlledSqlMockMvcTests {
    private static final String TEST_PASSWORD_HASH =
            "pbkdf2$120000$ZGJwai0yMDI2LXRlc3Qtc2VlZA==$Z7kG5pEEo62BsGNBwn7JqwppwrnIYLeR4lTvLrVzz6M=";

    private final List<Integer> activityIds = new ArrayList<>();
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

    @Autowired
    private ControlledSqlCompiler controlledSqlCompiler;

    @Test
    void mockMvcStudentControlledSqlReturnsActivitiesWithNonZeroCurrentEnrollment() throws Exception {
        TestFixture positive = createFixture(5);
        TestFixture zero = createFixture(5);
        String positiveTitle = "non-zero-enrollment-" + System.nanoTime();
        String zeroTitle = "zero-enrollment-" + System.nanoTime();
        jdbcTemplate.update("""
                UPDATE Activity
                SET title = ?, current_enrollment = 1
                WHERE activity_id = ?
                """, positiveTitle, positive.activityId());
        jdbcTemplate.update("""
                UPDATE Activity
                SET title = ?, current_enrollment = 0
                WHERE activity_id = ?
                """, zeroTitle, zero.activityId());

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(positive.studentId(), "student")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询当前报名人数不为0的活动","page":1,"size":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("CONTROLLED_SQL"))
                .andExpect(jsonPath("$.data.sqlPreview").doesNotExist())
                .andExpect(jsonPath("$.data.rows[*].activityTitle", hasItem(positiveTitle)))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", not(hasItem(zeroTitle))));
    }

    @Test
    void mockMvcStudentControlledSqlSupportsCapacityComparison() throws Exception {
        TestFixture fixture = createFixture(5);
        String title = "capacity-open-" + System.nanoTime();
        jdbcTemplate.update("""
                UPDATE Activity
                SET title = ?, current_enrollment = 1, capacity_limit = 5
                WHERE activity_id = ?
                """, title, fixture.activityId());

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(fixture.studentId(), "student")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询一个容量没满的活动","page":1,"size":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("CONTROLLED_SQL"))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", hasItem(title)));
    }

    @Test
    void mockMvcStudentControlledSqlRejectsSensitiveField() throws Exception {
        TestFixture fixture = createFixture(5);

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(fixture.studentId(), "student")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询学生手机号","page":1,"size":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void controlledSqlCompilerRejectsPhysicalTableAndUnknownField() {
        CurrentUser student = student(1, "student");

        assertThatThrownBy(() -> controlledSqlCompiler.compile("SELECT title FROM Activity LIMIT 20", student))
                .hasMessageContaining("物理表");
        assertThatThrownBy(() -> controlledSqlCompiler.compile("SELECT phone FROM activity_view LIMIT 20", student))
                .hasMessageContaining("敏感字段");
        assertThatThrownBy(() -> controlledSqlCompiler.compile("SELECT unknownColumn FROM activity_view LIMIT 20", student))
                .hasMessageContaining("未知字段");
    }

    @AfterEach
    void cleanUp() {
        for (Integer activityId : activityIds) {
            jdbcTemplate.update("DELETE FROM ActivityFeedback WHERE activity_id = ?", activityId);
            jdbcTemplate.update("DELETE FROM CreditRecord WHERE activity_id = ?", activityId);
            jdbcTemplate.update("DELETE FROM Registration WHERE activity_id = ?", activityId);
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
        activityIds.clear();
        venueIds.clear();
        categoryIds.clear();
        campusIds.clear();
        userIds.clear();
    }

    private TestFixture createFixture(int capacity) {
        String suffix = Long.toString(System.nanoTime());
        int campusId = insertAndTrack(campusIds, """
                INSERT INTO Campus(campus_name, location)
                VALUES (?, ?)
                """, "controlled-campus-" + suffix, "controlled-location");
        int venueId = insertAndTrack(venueIds, """
                INSERT INTO Venue(venue_name, room_number, capacity, campus_id)
                VALUES (?, ?, ?, ?)
                """, "controlled-venue", "C-" + suffix, capacity, campusId);
        int categoryId = insertAndTrack(categoryIds, """
                INSERT INTO Category(category_name)
                VALUES (?)
                """, "controlled-category-" + suffix);
        int organizerId = insertUser("ORGANIZER", "controlled-organizer-" + suffix, null, uniquePhone("181"));
        int studentId = insertUser("STUDENT", "controlled-student-" + suffix, "CS" + suffix, uniquePhone("182"));
        int activityId = insertAndTrack(activityIds, """
                INSERT INTO Activity(title, venue_id, category_id, start_time, end_time, enroll_deadline,
                                     capacity_limit, current_enrollment, description, status, organizer_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 'PUBLISHED', ?)
                """, "controlled-activity-" + suffix, venueId, categoryId, LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2), LocalDateTime.now().plusHours(12),
                capacity, "controlled sql test activity", organizerId);
        return new TestFixture(activityId, studentId);
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

    private String uniquePhone(String prefix) {
        return prefix + Long.toUnsignedString(System.nanoTime(), 36);
    }

    private CurrentUser student(int id, String name) {
        return new CurrentUser(id, name, Role.STUDENT, "S" + id, "10000000000");
    }

    private String bearer(CurrentUser user) {
        return "Bearer " + authService.issueToken(user);
    }

    private record TestFixture(int activityId, int studentId) {
    }

    @TestConfiguration
    static class StubLlmConfig {
        @Bean("openAiCompatibleLlmClient")
        LlmClient llmClient() {
            return (systemPrompt, userPrompt) -> {
                if (userPrompt.contains("报名人数不为0")) {
                    return """
                            {
                              "queryMode": "CONTROLLED_SQL",
                              "sql": "SELECT activityId, activityTitle, currentEnrollment, capacityLimit FROM activity_view WHERE currentEnrollment <> 0 ORDER BY startTime DESC LIMIT 20",
                              "summaryHint": "查询当前报名人数不为 0 的活动。",
                              "ambiguity": false,
                              "clarificationOptions": []
                            }
                            """;
                }
                if (userPrompt.contains("容量没满")) {
                    return """
                            {
                              "queryMode": "CONTROLLED_SQL",
                              "sql": "SELECT activityId, activityTitle, currentEnrollment, capacityLimit FROM activity_view WHERE currentEnrollment < capacityLimit ORDER BY startTime ASC LIMIT 20",
                              "summaryHint": "查询容量未满的活动。",
                              "ambiguity": false,
                              "clarificationOptions": []
                            }
                            """;
                }
                return """
                        {
                          "queryMode": "CONTROLLED_SQL",
                          "sql": "SELECT phone FROM my_registration_view LIMIT 20",
                          "summaryHint": "查询手机号。",
                          "ambiguity": false,
                          "clarificationOptions": []
                        }
                        """;
            };
        }
    }
}
