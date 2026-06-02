package com.campus.activity;

import com.campus.activity.common.AuthContext;
import com.campus.activity.common.BusinessException;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.Role;
import com.campus.activity.model.dto.ActivityRequest;
import com.campus.activity.model.dto.CheckInRequest;
import com.campus.activity.model.dto.FeedbackRequest;
import com.campus.activity.model.dto.ReviewRequest;
import com.campus.activity.model.query.LlmQueryPlanDraft;
import com.campus.activity.model.query.LlmQueryPlanFilter;
import com.campus.activity.model.query.QueryPlan;
import com.campus.activity.model.query.QueryIntent;
import com.campus.activity.model.query.QueryPlanDecision;
import com.campus.activity.model.vo.CheckInCodeVO;
import com.campus.activity.model.vo.CheckInResultVO;
import com.campus.activity.model.vo.RegistrationActionVO;
import com.campus.activity.service.ActivityService;
import com.campus.activity.service.AdminSqlValidator;
import com.campus.activity.service.AuthService;
import com.campus.activity.service.CheckInService;
import com.campus.activity.service.FeedbackService;
import com.campus.activity.service.QueryIntentParser;
import com.campus.activity.service.QueryPlanValidator;
import com.campus.activity.service.RegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "llm.enabled=false")
@AutoConfigureMockMvc
class ActivityApplicationTests {
    private static final String TEST_PASSWORD_HASH =
            "pbkdf2$120000$ZGJwai0yMDI2LXRlc3Qtc2VlZA==$Z7kG5pEEo62BsGNBwn7JqwppwrnIYLeR4lTvLrVzz6M=";

    private final List<Integer> activityIds = new ArrayList<>();
    private final List<Integer> venueIds = new ArrayList<>();
    private final List<Integer> categoryIds = new ArrayList<>();
    private final List<Integer> campusIds = new ArrayList<>();
    private final List<Integer> userIds = new ArrayList<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private CheckInService checkInService;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private QueryPlanValidator queryPlanValidator;

    @Autowired
    private AdminSqlValidator adminSqlValidator;

    @Autowired
    private QueryIntentParser queryIntentParser;

    @BeforeEach
    void ensureNotificationTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS Notification (
                    notification_id INT AUTO_INCREMENT PRIMARY KEY,
                    recipient_id INT NOT NULL,
                    type VARCHAR(40) NOT NULL,
                    title VARCHAR(120) NOT NULL,
                    content VARCHAR(500) NOT NULL,
                    related_type VARCHAR(40),
                    related_id INT,
                    is_read TINYINT(1) NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id)
                        REFERENCES User(user_id)
                        ON UPDATE CASCADE
                        ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void deletingCampusCascadesVenuesActivitiesRegistrationsAndFeedback() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);
        Integer campusId = jdbcTemplate.queryForObject(
                "SELECT campus_id FROM Venue WHERE venue_id = ?",
                Integer.class,
                fixture.venueId()
        );
        int registrationId = insertAndTrack(new ArrayList<>(), """
                INSERT INTO Registration(student_id, activity_id, status, check_in_time)
                VALUES (?, ?, 'CHECKED_IN', ?)
                """, fixture.studentOneId(), fixture.activityId(), LocalDateTime.now());
        int feedbackId = insertAndTrack(new ArrayList<>(), """
                INSERT INTO ActivityFeedback(registration_id, activity_id, student_id, rating, content)
                VALUES (?, ?, ?, 5, 'cascade check')
                """, registrationId, fixture.activityId(), fixture.studentOneId());

        jdbcTemplate.update("DELETE FROM Campus WHERE campus_id = ?", campusId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM Venue WHERE venue_id = ?",
                Integer.class,
                fixture.venueId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM Activity WHERE activity_id = ?",
                Integer.class,
                fixture.activityId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM Registration WHERE registration_id = ?",
                Integer.class,
                registrationId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ActivityFeedback WHERE feedback_id = ?",
                Integer.class,
                feedbackId
        )).isZero();
    }

    @Test
    void loginAcceptsHashedPasswordAndRejectsWrongPassword() {
        int userId = insertUser("STUDENT", "login-student", "L" + Long.toString(System.nanoTime()).substring(0, 12), uniquePhone("199"));

        CurrentUser currentUser = authService.authenticate("login-student", "123456");
        assertThat(currentUser.id()).isEqualTo(userId);

        assertBusinessCode(() -> authService.authenticate("login-student", "wrong-password"), 40101);
    }

    @Test
    void mockMvcLoginReturnsStableContract() throws Exception {
        insertUser("STUDENT", "contract-login", "C" + Long.toString(System.nanoTime()).substring(0, 12), uniquePhone("198"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"contract-login","password":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.name").value("contract-login"));
    }

    @Test
    void mockMvcProtectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void mockMvcRejectsCrossRoleAccess() throws Exception {
        int studentId = insertUser("STUDENT", "contract-student", "M" + Long.toString(System.nanoTime()).substring(0, 12), uniquePhone("197"));

        mockMvc.perform(get("/api/v1/stats/overview")
                        .header("Authorization", bearer(student(studentId, "contract-student"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void mockMvcActivityListAndEnrollmentContractsStayStable() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        mockMvc.perform(get("/api/v1/activities")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one")))
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5));

        mockMvc.perform(post("/api/v1/activities/{activityId}/registrations", fixture.activityId())
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.activityId").value(fixture.activityId()))
                .andExpect(jsonPath("$.data.registrationStatus").value("ENROLLED"))
                .andExpect(jsonPath("$.data.registrationId").exists());
    }

    @Test
    void mockMvcNotificationContractsStayStable() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        mockMvc.perform(post("/api/v1/activities/{activityId}/registrations", fixture.activityId())
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        Integer notificationId = jdbcTemplate.queryForObject("""
                SELECT notification_id
                FROM Notification
                WHERE recipient_id = ? AND type = 'REGISTRATION_ENROLLED'
                ORDER BY notification_id DESC
                LIMIT 1
                """, Integer.class, fixture.studentOneId());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one")))
                        .param("page", "1")
                        .param("size", "10")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].notificationId").value(notificationId))
                .andExpect(jsonPath("$.data.list[0].type").value("REGISTRATION_ENROLLED"))
                .andExpect(jsonPath("$.data.list[0].read").value(false));

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void mockMvcNaturalQueryContractsStayStable() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);
        int adminId = insertUser("ADMIN", "natural-query-admin", null, uniquePhone("194"));

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询明天的活动","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("ACTIVITY_LIST"))
                .andExpect(jsonPath("$.data.summary").exists())
                .andExpect(jsonPath("$.data.columns").isArray())
                .andExpect(jsonPath("$.data.rows").isArray())
                .andExpect(jsonPath("$.data.sqlPreview").doesNotExist());

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(admin(adminId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询待审核活动","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("ACTIVITY_LIST"))
                .andExpect(jsonPath("$.data.sqlPreview").exists());
    }

    @Test
    void mockMvcNaturalQueryRejectsUnknownAndUnauthorizedQuestions() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);
        int adminId = insertUser("ADMIN", "natural-query-admin-reject", null, uniquePhone("193"));

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询报名名单","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(admin(adminId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"帮我删除所有活动","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void mockMvcNaturalQueryReturnsClarificationForBroadQuestion() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);
        String activityTitle = jdbcTemplate.queryForObject(
                "SELECT title FROM Activity WHERE activity_id = ?",
                String.class,
                fixture.activityId()
        );

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查一下报名情况","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.clarificationRequired").value(true))
                .andExpect(jsonPath("$.data.clarificationOptions").isArray())
                .andExpect(jsonPath("$.data.clarificationOptions", hasItem("查询我的报名记录")))
                .andExpect(jsonPath("$.data.rows").isArray());

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(organizer(fixture.organizerId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询活动报名情况","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.clarificationRequired").value(true))
                .andExpect(jsonPath("$.data.clarificationOptions", hasItem("查询" + activityTitle + "的报名名单")));
    }

    @Test
    void mockMvcNaturalQuerySupportsNaturalSlotsForCreditAndActivityTitle() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);
        int adminId = insertUser("ADMIN", "natural-query-admin-slots", null, uniquePhone("192"));
        int lowCreditStudentId = insertUser("STUDENT", "low-credit-student", "R" + Long.toString(System.nanoTime()).substring(0, 12), uniquePhone("191"));
        jdbcTemplate.update("""
                INSERT INTO CreditRecord(student_id, activity_id, change_value, reason_type, reason, operator_id)
                VALUES (?, ?, -25, 'MANUAL_ADJUST', 'contract test penalty', ?)
                """, lowCreditStudentId, fixture.activityId(), adminId);

        String activityTitle = jdbcTemplate.queryForObject(
                "SELECT title FROM Activity WHERE activity_id = ?",
                String.class,
                fixture.activityId()
        );

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(admin(adminId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询信用分低于80的学生","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("CREDIT_RISK"))
                .andExpect(jsonPath("$.data.rows[0].studentName").value("low-credit-student"));

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(organizer(fixture.organizerId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询%s的报名名单","page":1,"size":10}
                                """.formatted(activityTitle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("ACTIVITY_REGISTRATION_LIST"))
                .andExpect(jsonPath("$.data.rows").isArray());

        Integer campusId = jdbcTemplate.queryForObject(
                "SELECT campus_id FROM Venue WHERE venue_id = ?",
                Integer.class,
                fixture.venueId()
        );
        jdbcTemplate.update("UPDATE Campus SET campus_name = ? WHERE campus_id = ?",
                "邯郸校区-" + System.nanoTime(), campusId);
        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        registrationService.enroll(fixture.activityId());
        AuthContext.clear();

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询我报名的在邯郸校区开展的活动","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("MY_REGISTRATION_LIST"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.rows[0].activityId").value(fixture.activityId()))
                .andExpect(jsonPath("$.data.sqlPreview").doesNotExist());

        int feedbackRegistrationId = jdbcTemplate.queryForObject("""
                SELECT registration_id
                FROM Registration
                WHERE student_id = ? AND activity_id = ?
                """, Integer.class, fixture.studentOneId(), fixture.activityId());
        jdbcTemplate.update("UPDATE Registration SET status = 'CHECKED_IN', check_in_time = CURRENT_TIMESTAMP WHERE registration_id = ?",
                feedbackRegistrationId);
        insertAndTrack(new ArrayList<>(), """
                INSERT INTO ActivityFeedback(registration_id, activity_id, student_id, rating, content)
                VALUES (?, ?, ?, 4, 'natural query evaluated filter')
                """, feedbackRegistrationId, fixture.activityId(), fixture.studentOneId());

        TestFixture notEvaluatedFixture = createFixture(LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2), 5);
        Integer notEvaluatedCampusId = jdbcTemplate.queryForObject(
                "SELECT campus_id FROM Venue WHERE venue_id = ?",
                Integer.class,
                notEvaluatedFixture.venueId()
        );
        jdbcTemplate.update("UPDATE Campus SET campus_name = ? WHERE campus_id = ?",
                "邯郸校区-" + System.nanoTime(), notEvaluatedCampusId);
        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        registrationService.enroll(notEvaluatedFixture.activityId());
        AuthContext.clear();

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询我报名的在邯郸校区开展的活动，要求活动是我评价过的","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("MY_REGISTRATION_LIST"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.rows[0].activityId").value(fixture.activityId()));
    }

    @Test
    void mockMvcNaturalQuerySupportsSemanticActivityKeyword() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);
        String title = "数据库系统项目分享会-" + System.nanoTime();
        jdbcTemplate.update("""
                UPDATE Activity
                SET title = ?, description = ?
                WHERE activity_id = ?
                """, title, "面向数据库课程项目的小型分享会，介绍活动报名系统的设计与实现。", fixture.activityId());

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询一个和数据库相关的活动","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("ACTIVITY_LIST"))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", hasItem(title)));

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询关于数据库的活动","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("ACTIVITY_LIST"))
                .andExpect(jsonPath("$.data.rows[*].activityTitle", hasItem(title)));
    }

    @Test
    void queryIntentParserExtractsSemanticActivityKeywordWithoutPollutingBroadActivityQuery() {
        QueryPlan semanticPlan = queryIntentParser.parse("查询一个和数据库相关的活动", 1, 20);
        assertThat(semanticPlan.getIntent()).isEqualTo(QueryIntent.ACTIVITY_LIST);
        assertThat(semanticPlan.getFilters())
                .anyMatch(filter -> "activityKeyword".equals(filter.key()) && "数据库".equals(filter.value()));

        QueryPlan broadPlan = queryIntentParser.parse("查询明天的活动", 1, 20);
        assertThat(broadPlan.getIntent()).isEqualTo(QueryIntent.ACTIVITY_LIST);
        assertThat(broadPlan.getFilters())
                .noneMatch(filter -> "activityKeyword".equals(filter.key()));
    }

    @Test
    void mockMvcNaturalQueryReturnsCampusDomainForCampusWithoutActivities() throws Exception {
        int studentId = insertUser("STUDENT", "campus-query-student", "CAMPUS" + Long.toString(System.nanoTime()).substring(0, 8), uniquePhone("190"));
        String campusName = "no-activity-campus-" + System.nanoTime();
        insertAndTrack(campusIds, """
                INSERT INTO Campus(campus_name, location)
                VALUES (?, ?)
                """, campusName, "empty-location");

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(studentId, "campus-query-student")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"有活动未举办的校区","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("CAMPUS_WITHOUT_ACTIVITY"))
                .andExpect(jsonPath("$.data.columns[0].key").value("campusId"))
                .andExpect(jsonPath("$.data.rows[*].campusName", hasItem(campusName)))
                .andExpect(jsonPath("$.data.sqlPreview").doesNotExist());
    }

    @Test
    void mockMvcNaturalQueryReturnsOrganizerParticipantStudentsWithoutPhone() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);
        String studentOneName = jdbcTemplate.queryForObject("SELECT username FROM User WHERE user_id = ?", String.class, fixture.studentOneId());
        insertAndTrack(new ArrayList<>(), """
                INSERT INTO Registration(student_id, activity_id, status, registration_time)
                VALUES (?, ?, 'CHECKED_IN', ?)
                """, fixture.studentOneId(), fixture.activityId(), LocalDateTime.now().minusDays(1));
        insertAndTrack(new ArrayList<>(), """
                INSERT INTO Registration(student_id, activity_id, status, registration_time)
                VALUES (?, ?, 'ENROLLED', ?)
                """, fixture.studentTwoId(), fixture.activityId(), LocalDateTime.now());

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(organizer(fixture.organizerId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询参与过我创建的活动的所有学生信息","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("ORGANIZER_PARTICIPANT_STUDENTS"))
                .andExpect(jsonPath("$.data.columns[*].key", hasItem("studentName")))
                .andExpect(jsonPath("$.data.columns[*].key", hasItem("participationCount")))
                .andExpect(jsonPath("$.data.rows[*].studentName", hasItem(studentOneName)))
                .andExpect(jsonPath("$.data.rows[0].phone").doesNotExist());
    }

    @Test
    void mockMvcNaturalQueryReturnsMyFeedbackRecordsInsteadOfRegistrations() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);
        int registrationId = insertAndTrack(new ArrayList<>(), """
                INSERT INTO Registration(student_id, activity_id, status, check_in_time)
                VALUES (?, ?, 'CHECKED_IN', ?)
                """, fixture.studentOneId(), fixture.activityId(), LocalDateTime.now());
        int feedbackId = insertAndTrack(new ArrayList<>(), """
                INSERT INTO ActivityFeedback(registration_id, activity_id, student_id, rating, content)
                VALUES (?, ?, ?, 4, 'natural feedback query')
                """, registrationId, fixture.activityId(), fixture.studentOneId());

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(student(fixture.studentOneId(), "student-one")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询我的活动评价记录","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("MY_FEEDBACK_LIST"))
                .andExpect(jsonPath("$.data.summary").value("共查询到 1 条你的活动评价记录。"))
                .andExpect(jsonPath("$.data.columns[*].key", hasItem("feedbackId")))
                .andExpect(jsonPath("$.data.columns[*].key", hasItem("rating")))
                .andExpect(jsonPath("$.data.rows[0].feedbackId").value(feedbackId))
                .andExpect(jsonPath("$.data.rows[0].rating").value(4))
                .andExpect(jsonPath("$.data.rows[0].registrationId").doesNotExist());
    }

    @Test
    void queryPlanValidatorAcceptsSafeLlmPlan() {
        LlmQueryPlanDraft draft = new LlmQueryPlanDraft();
        draft.setIntent("ACTIVITY_LIST");
        draft.setDomain("activity");
        draft.getFilters().add(filter("activity.status", "eq", "PUBLISHED"));
        draft.getFilters().add(filter("campus.name", "contains", "邯郸"));
        draft.getFilters().add(filter("venue.name", "contains", "光华楼"));
        draft.getFilters().add(filter("startFrom", "gte", LocalDateTime.now().toString()));
        draft.setSize(100);

        QueryPlanDecision decision = queryPlanValidator.validate(draft, 1, 20);

        assertThat(decision.clarificationRequired()).isFalse();
        assertThat(decision.plan().getIntent()).isEqualTo(QueryIntent.ACTIVITY_LIST);
        assertThat(decision.plan().getSize()).isEqualTo(50);
        assertThat(decision.plan().getFilters())
                .extracting("key")
                .contains("activityStatus", "campusKeyword", "venueKeyword", "startFrom");
    }

    @Test
    void queryPlanValidatorAcceptsNaturalSlotFilters() {
        LlmQueryPlanDraft draft = new LlmQueryPlanDraft();
        draft.setIntent("CREDIT_RISK");
        draft.setDomain("credit");
        draft.getFilters().add(filter("credit.score", "lte", 80));
        draft.getFilters().add(filter("student.name", "contains", "张三"));

        QueryPlanDecision decision = queryPlanValidator.validate(draft, 1, 20);

        assertThat(decision.clarificationRequired()).isFalse();
        assertThat(decision.plan().getFilters())
                .extracting("key")
                .contains("maxCreditScore", "studentKeyword");
    }

    @Test
    void queryPlanValidatorNormalizesImperfectLlmRegistrationPlan() {
        LlmQueryPlanDraft draft = new LlmQueryPlanDraft();
        draft.setIntent("ACTIVITY_LIST");
        draft.setDomain("activity");
        draft.getFilters().add(filter("campus.name", "contains", "邯郸"));
        draft.getFilters().add(filter("registration.status", "eq", "ENROLLED"));
        draft.getFilters().add(filter("feedback.exists", "true", true));

        QueryPlanDecision decision = queryPlanValidator.validate(draft, 1, 20);

        assertThat(decision.clarificationRequired()).isFalse();
        assertThat(decision.plan().getIntent()).isEqualTo(QueryIntent.MY_REGISTRATION_LIST);
        assertThat(decision.plan().getFilters()).anyMatch(filter -> "registrationStatus".equals(filter.key()) && "ENROLLED".equals(filter.value()));
        assertThat(decision.plan().getFilters()).anyMatch(filter -> "evaluatedOnly".equals(filter.key()) && Boolean.TRUE.equals(filter.value()));
    }

    @Test
    void queryPlanValidatorNormalizesCampusNotExistsPlan() {
        LlmQueryPlanDraft draft = new LlmQueryPlanDraft();
        draft.setIntent("ACTIVITY_LIST");
        draft.setDomain("campus");
        draft.setDistinct(true);
        draft.getNotExists().add("activity");

        QueryPlanDecision decision = queryPlanValidator.validate(draft, 1, 20);

        assertThat(decision.clarificationRequired()).isFalse();
        assertThat(decision.plan().getIntent()).isEqualTo(QueryIntent.CAMPUS_WITHOUT_ACTIVITY);
        assertThat(decision.planPreview()).containsEntry("queryMode", "DSL");
    }

    @Test
    void queryPlanValidatorRejectsSensitiveSelectField() {
        LlmQueryPlanDraft draft = new LlmQueryPlanDraft();
        draft.setIntent("ACTIVITY_LIST");
        draft.setDomain("activity");
        draft.getSelectFields().add("user.password");

        assertBusinessCode(() -> queryPlanValidator.validate(draft, 1, 20), 40002);
    }

    @Test
    void queryPlanValidatorRejectsUnknownLlmField() {
        LlmQueryPlanDraft draft = new LlmQueryPlanDraft();
        draft.setIntent("ACTIVITY_LIST");
        draft.getFilters().add(filter("user.password_hash", "eq", "secret"));

        assertBusinessCode(() -> queryPlanValidator.validate(draft, 1, 20), 40002);
    }

    @Test
    void queryPlanValidatorReturnsClarificationForAmbiguity() {
        LlmQueryPlanDraft draft = new LlmQueryPlanDraft();
        draft.setAmbiguity(true);
        draft.setClarificationOptions(List.of("查询某个活动的报名名单", "查询我的报名记录"));

        QueryPlanDecision decision = queryPlanValidator.validate(draft, 1, 20);

        assertThat(decision.clarificationRequired()).isTrue();
        assertThat(decision.clarificationOptions()).contains("查询我的报名记录");
    }

    @Test
    void adminSqlValidatorAllowsOnlySafeSelects() {
        assertThat(adminSqlValidator.validateAndLimit("SELECT campus_id AS campusId, campus_name AS campusName FROM Campus"))
                .endsWith("LIMIT 50");
        assertThat(adminSqlValidator.validateAndLimit("SELECT campus_id FROM Campus LIMIT 500"))
                .endsWith("LIMIT 50");
        assertBusinessCode(() -> adminSqlValidator.validateAndLimit("SELECT password FROM User"), 40002);
        assertBusinessCode(() -> adminSqlValidator.validateAndLimit("SELECT * FROM UnknownTable"), 40002);
        assertBusinessCode(() -> adminSqlValidator.validateAndLimit("UPDATE User SET username = 'x'"), 40002);
        assertBusinessCode(() -> adminSqlValidator.validateAndLimit("SELECT campus_id FROM Campus; SELECT user_id FROM User"), 40002);
    }

    @Test
    void mockMvcOrganizerCannotReadOthersRegistrationList() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);
        int otherOrganizerId = insertUser("ORGANIZER", "contract-other-organizer", null, uniquePhone("196"));

        mockMvc.perform(get("/api/v1/activities/{activityId}/registrations", fixture.activityId())
                        .header("Authorization", bearer(organizer(otherOrganizerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void mockMvcCheckInRejectsMalformedCodeWithStableErrorContract() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        mockMvc.perform(patch("/api/v1/registrations/check-in")
                        .header("Authorization", bearer(organizer(fixture.organizerId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checkInCode":"malformed-code"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("签到码格式错误"));
    }

    @Test
    void enrollmentWaitlistAndPromotionFlow() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 1);

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        RegistrationActionVO enrolled = registrationService.enroll(fixture.activityId());
        assertThat(enrolled.registrationStatus()).isEqualTo("ENROLLED");

        AuthContext.set(student(fixture.studentTwoId(), "student-two"));
        RegistrationActionVO waitlisted = registrationService.enroll(fixture.activityId());
        assertThat(waitlisted.registrationStatus()).isEqualTo("WAITLISTED");
        assertThat(waitlisted.queueNo()).isEqualTo(1);

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        RegistrationActionVO cancelled = registrationService.cancel(enrolled.registrationId());
        assertThat(cancelled.registrationStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.promotedRegistrationId()).isEqualTo(waitlisted.registrationId());

        Map<String, Object> promoted = jdbcTemplate.queryForMap("""
                SELECT status, queue_no, activity_id
                FROM Registration
                WHERE registration_id = ?
                """, waitlisted.registrationId());
        assertThat(promoted.get("status")).isEqualTo("ENROLLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_enrollment FROM Activity WHERE activity_id = ?",
                Integer.class,
                fixture.activityId()
        )).isEqualTo(1);
    }

    @Test
    void concurrentEnrollmentDoesNotOversellAndKeepsWaitlistOrdered() throws Exception {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 1);
        List<Integer> studentIds = new ArrayList<>();
        studentIds.add(fixture.studentOneId());
        studentIds.add(fixture.studentTwoId());
        for (int i = 0; i < 4; i++) {
            studentIds.add(insertUser(
                    "STUDENT",
                    "concurrent-student-" + i + "-" + System.nanoTime(),
                    "Q" + Long.toString(System.nanoTime()).substring(0, 12),
                    uniquePhone("195")
            ));
        }

        CountDownLatch ready = new CountDownLatch(studentIds.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(studentIds.size());
        try {
            List<Callable<RegistrationActionVO>> tasks = studentIds.stream()
                    .<Callable<RegistrationActionVO>>map(studentId -> () -> {
                        ready.countDown();
                        start.await(5, TimeUnit.SECONDS);
                        AuthContext.set(student(studentId, "concurrent-" + studentId));
                        try {
                            return registrationService.enroll(fixture.activityId());
                        } finally {
                            AuthContext.clear();
                        }
                    })
                    .toList();
            List<Future<RegistrationActionVO>> futures = tasks.stream().map(executor::submit).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<RegistrationActionVO> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS).registrationStatus())
                        .isIn("ENROLLED", "WAITLISTED");
            }
        } finally {
            executor.shutdownNow();
        }

        Map<String, Object> statusCounts = jdbcTemplate.queryForMap("""
                SELECT
                  SUM(CASE WHEN status = 'ENROLLED' THEN 1 ELSE 0 END) AS enrolledCount,
                  SUM(CASE WHEN status = 'WAITLISTED' THEN 1 ELSE 0 END) AS waitlistedCount,
                  COUNT(DISTINCT queue_no) AS distinctQueueCount,
                  MIN(queue_no) AS minQueueNo,
                  MAX(queue_no) AS maxQueueNo
                FROM Registration
                WHERE activity_id = ?
                """, fixture.activityId());
        int waitlistedCount = ((Number) statusCounts.get("waitlistedCount")).intValue();
        assertThat(((Number) statusCounts.get("enrolledCount")).intValue()).isEqualTo(1);
        assertThat(waitlistedCount).isEqualTo(studentIds.size() - 1);
        assertThat(((Number) statusCounts.get("distinctQueueCount")).intValue()).isEqualTo(waitlistedCount);
        assertThat(((Number) statusCounts.get("minQueueNo")).intValue()).isEqualTo(1);
        assertThat(((Number) statusCounts.get("maxQueueNo")).intValue()).isEqualTo(waitlistedCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_enrollment FROM Activity WHERE activity_id = ?",
                Integer.class,
                fixture.activityId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT recipient_id)
                FROM Notification
                WHERE recipient_id IN (%s)
                  AND type IN ('REGISTRATION_ENROLLED', 'REGISTRATION_WAITLISTED')
                """.formatted(String.join(",", studentIds.stream().map(String::valueOf).toList())),
                Integer.class
        )).isEqualTo(studentIds.size());
    }

    @Test
    void checkInIsIdempotentAndFeedbackCanBeUpdated() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        RegistrationActionVO enrolled = registrationService.enroll(fixture.activityId());
        int registrationId = enrolled.registrationId();
        CheckInCodeVO code = checkInService.code(registrationId);

        AuthContext.set(organizer(fixture.organizerId()));
        CheckInResultVO firstCheckIn = checkInService.checkIn(new CheckInRequest(code.checkInCode()));
        CheckInResultVO secondCheckIn = checkInService.checkIn(new CheckInRequest(code.checkInCode()));
        assertThat(firstCheckIn.registrationStatus()).isEqualTo("CHECKED_IN");
        assertThat(secondCheckIn.registrationStatus()).isEqualTo("CHECKED_IN");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM CreditRecord
                WHERE registration_id = ? AND reason_type = 'CHECK_IN'
                """, Integer.class, registrationId)).isEqualTo(1);

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        feedbackService.submit(fixture.activityId(), new FeedbackRequest(5, "smooth activity"));
        feedbackService.submit(fixture.activityId(), new FeedbackRequest(3, "smooth check-in but venue guide can improve"));

        Map<String, Object> feedback = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS feedbackCount, MAX(rating) AS rating
                FROM ActivityFeedback
                WHERE registration_id = ?
                """, registrationId);
        assertThat(((Number) feedback.get("feedbackCount")).intValue()).isEqualTo(1);
        assertThat(((Number) feedback.get("rating")).intValue()).isEqualTo(3);
    }

    @Test
    void markAbsencesOnlyWritesCreditOnce() {
        TestFixture fixture = createFixture(LocalDateTime.now().minusDays(1), LocalDateTime.now().minusHours(2), 5);

        int registrationId = insertAndTrack(new ArrayList<>(), """
                INSERT INTO Registration(student_id, activity_id, status)
                VALUES (?, ?, 'ENROLLED')
                """, fixture.studentOneId(), fixture.activityId());
        jdbcTemplate.update("""
                UPDATE Activity
                SET current_enrollment = 1
                WHERE activity_id = ?
                """, fixture.activityId());

        AuthContext.set(organizer(fixture.organizerId()));
        RegistrationActionVO firstMark = registrationService.markAbsences(fixture.activityId());
        RegistrationActionVO secondMark = registrationService.markAbsences(fixture.activityId());

        assertThat(firstMark.absentCount()).isEqualTo(1);
        assertThat(secondMark.absentCount()).isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM Registration WHERE registration_id = ?",
                String.class,
                registrationId
        )).isEqualTo("ABSENT");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM CreditRecord
                WHERE registration_id = ? AND reason_type = 'ABSENT'
                """, Integer.class, registrationId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(change_value), 0)
                FROM CreditRecord
                WHERE registration_id = ?
                """, Integer.class, registrationId)).isEqualTo(-10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_enrollment FROM Activity WHERE activity_id = ?",
                Integer.class,
                fixture.activityId()
        )).isEqualTo(0);
    }

    @Test
    void enrollmentRejectsClosedActivity() {
        TestFixture fixture = createFixture(LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(3), 5);

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        assertBusinessCode(() -> registrationService.enroll(fixture.activityId()), 40904);
    }

    @Test
    void studentCannotMarkAbsences() {
        TestFixture fixture = createFixture(LocalDateTime.now().minusDays(1), LocalDateTime.now().minusHours(2), 5);

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        assertBusinessCode(() -> registrationService.markAbsences(fixture.activityId()), 40301);
    }

    @Test
    void organizerCannotManageOthersActivityRegistrations() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);
        int otherOrganizerId = insertUser("ORGANIZER", "other-organizer", null, uniquePhone("155"));

        AuthContext.set(organizer(otherOrganizerId));
        assertBusinessCode(() -> registrationService.activityRegistrations(fixture.activityId(), 1, 10, null), 40301);
    }

    @Test
    void studentCannotCancelOthersRegistration() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        RegistrationActionVO enrolled = registrationService.enroll(fixture.activityId());

        AuthContext.set(student(fixture.studentTwoId(), "student-two"));
        assertBusinessCode(() -> registrationService.cancel(enrolled.registrationId()), 40301);
    }

    @Test
    void checkedInRegistrationCannotBeCancelled() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        RegistrationActionVO enrolled = registrationService.enroll(fixture.activityId());
        CheckInCodeVO code = checkInService.code(enrolled.registrationId());

        AuthContext.set(organizer(fixture.organizerId()));
        checkInService.checkIn(new CheckInRequest(code.checkInCode()));

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        assertBusinessCode(() -> registrationService.cancel(enrolled.registrationId()), 40903);
    }

    @Test
    void feedbackRejectsActivityWithoutCheckedInRegistration() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        registrationService.enroll(fixture.activityId());

        assertBusinessCode(() -> feedbackService.submit(fixture.activityId(), new FeedbackRequest(4, "not checked in")), 40903);
    }

    @Test
    void studentCannotGenerateOthersCheckInCode() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        AuthContext.set(student(fixture.studentOneId(), "student-one"));
        RegistrationActionVO enrolled = registrationService.enroll(fixture.activityId());

        AuthContext.set(student(fixture.studentTwoId(), "student-two"));
        assertBusinessCode(() -> checkInService.code(enrolled.registrationId()), 40301);
    }

    @Test
    void checkInRejectsMalformedCode() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        AuthContext.set(organizer(fixture.organizerId()));
        assertBusinessCode(() -> checkInService.checkIn(new CheckInRequest("malformed-code")), 40001);
    }

    @Test
    void organizerCannotUpdatePublishedActivity() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        AuthContext.set(organizer(fixture.organizerId()));
        assertBusinessCode(() -> activityService.update(fixture.activityId(), validActivityRequest(fixture)), 40903);
    }

    @Test
    void reviewRejectsInvalidStateAndInvalidResult() {
        TestFixture fixture = createFixture(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2), 5);

        AuthContext.set(admin());
        assertBusinessCode(() -> activityService.review(fixture.activityId(), new ReviewRequest("APPROVED", null)), 40903);

        jdbcTemplate.update("UPDATE Activity SET status = 'PENDING_REVIEW' WHERE activity_id = ?", fixture.activityId());
        assertBusinessCode(() -> activityService.review(fixture.activityId(), new ReviewRequest("UNKNOWN", null)), 40001);
    }

    @AfterEach
    void cleanUp() {
        AuthContext.clear();
        for (Integer userId : userIds) {
            jdbcTemplate.update("DELETE FROM Notification WHERE recipient_id = ?", userId);
        }
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
            jdbcTemplate.update("DELETE FROM User WHERE user_id = ?", userId);
        }
        activityIds.clear();
        venueIds.clear();
        categoryIds.clear();
        campusIds.clear();
        userIds.clear();
    }

    private TestFixture createFixture(LocalDateTime startTime, LocalDateTime endTime, int capacity) {
        String suffix = Long.toString(System.nanoTime());
        int campusId = insertAndTrack(campusIds, """
                INSERT INTO Campus(campus_name, location)
                VALUES (?, ?)
                """, "test-campus-" + suffix, "test-location");
        int venueId = insertAndTrack(venueIds, """
                INSERT INTO Venue(venue_name, room_number, capacity, campus_id)
                VALUES (?, ?, ?, ?)
                """, "test-venue", "T-" + suffix, capacity, campusId);
        int categoryId = insertAndTrack(categoryIds, """
                INSERT INTO Category(category_name)
                VALUES (?)
                """, "test-category-" + suffix);
        int organizerId = insertUser("ORGANIZER", "test-organizer-" + suffix, null, uniquePhone("188"));
        int studentOneId = insertUser("STUDENT", "test-student-one-" + suffix, "S" + suffix + "1", uniquePhone("177"));
        int studentTwoId = insertUser("STUDENT", "test-student-two-" + suffix, "S" + suffix + "2", uniquePhone("166"));
        int activityId = insertAndTrack(activityIds, """
                INSERT INTO Activity(title, venue_id, category_id, start_time, end_time, enroll_deadline,
                                     capacity_limit, current_enrollment, description, status, organizer_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 'PUBLISHED', ?)
                """, "test-activity-" + suffix, venueId, categoryId, startTime, endTime,
                startTime.minusHours(1), capacity, "integration test activity", organizerId);
        return new TestFixture(activityId, organizerId, studentOneId, studentTwoId, venueId, categoryId);
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

    private CurrentUser student(int userId, String name) {
        return new CurrentUser(userId, name, Role.STUDENT, "TEST-" + userId, "1" + userId);
    }

    private CurrentUser organizer(int userId) {
        return new CurrentUser(userId, "test-organizer", Role.ORGANIZER, null, "1" + userId);
    }

    private CurrentUser admin() {
        return new CurrentUser(1, "test-admin", Role.ADMIN, null, "10000000000");
    }

    private CurrentUser admin(int userId) {
        return new CurrentUser(userId, "test-admin", Role.ADMIN, null, "1" + userId);
    }

    private String bearer(CurrentUser user) {
        return "Bearer " + authService.issueToken(user);
    }

    private ActivityRequest validActivityRequest(TestFixture fixture) {
        LocalDateTime startTime = LocalDateTime.now().plusDays(2);
        return new ActivityRequest(
                "updated activity",
                fixture.venueId(),
                fixture.categoryId(),
                startTime,
                startTime.plusHours(2),
                startTime.minusHours(1),
                5,
                null,
                "updated description"
        );
    }

    private void assertBusinessCode(ThrowingOperation operation, int expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(expectedCode));
    }

    private LlmQueryPlanFilter filter(String field, String operator, Object value) {
        LlmQueryPlanFilter filter = new LlmQueryPlanFilter();
        filter.setField(field);
        filter.setOperator(operator);
        filter.setValue(value);
        return filter;
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }

    private record TestFixture(int activityId, int organizerId, int studentOneId, int studentTwoId,
                               int venueId, int categoryId) {
    }
}
