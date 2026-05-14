package com.campus.activity;

import com.campus.activity.common.AuthContext;
import com.campus.activity.common.BusinessException;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.Role;
import com.campus.activity.model.dto.ActivityRequest;
import com.campus.activity.model.dto.CheckInRequest;
import com.campus.activity.model.dto.FeedbackRequest;
import com.campus.activity.model.dto.ReviewRequest;
import com.campus.activity.model.vo.CheckInCodeVO;
import com.campus.activity.model.vo.CheckInResultVO;
import com.campus.activity.model.vo.RegistrationActionVO;
import com.campus.activity.service.ActivityService;
import com.campus.activity.service.AuthService;
import com.campus.activity.service.CheckInService;
import com.campus.activity.service.FeedbackService;
import com.campus.activity.service.RegistrationService;
import org.junit.jupiter.api.AfterEach;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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

    @Test
    void contextLoads() {
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

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }

    private record TestFixture(int activityId, int organizerId, int studentOneId, int studentTwoId,
                               int venueId, int categoryId) {
    }
}
