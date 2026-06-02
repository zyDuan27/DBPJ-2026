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
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "llm.enabled=true",
        "llm.api-key=test-key",
        "llm.admin-sql-enabled=true",
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
class NaturalQueryAdminSqlMockMvcTests {
    private static final String TEST_PASSWORD_HASH =
            "pbkdf2$120000$ZGJwai0yMDI2LXRlc3Qtc2VlZA==$Z7kG5pEEo62BsGNBwn7JqwppwrnIYLeR4lTvLrVzz6M=";

    private final List<Integer> userIds = new ArrayList<>();
    private final List<Integer> campusIds = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthService authService;

    @Test
    void mockMvcAdminSqlDraftModeReturnsDynamicResultAndSqlPreview() throws Exception {
        int adminId = insertUser("ADMIN", "admin-sql-query", null, "185" + Long.toUnsignedString(System.nanoTime(), 36));
        String campusName = "admin-sql-campus-" + System.nanoTime();
        insertAndTrack(campusIds, """
                INSERT INTO Campus(campus_name, location)
                VALUES (?, ?)
                """, campusName, "sql-mode-location");

        mockMvc.perform(post("/api/v1/natural-query")
                        .header("Authorization", bearer(new CurrentUser(adminId, "admin-sql-query", Role.ADMIN, null, "10000000000")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"管理员查询所有校区","page":1,"size":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.intent").value("ADMIN_SQL"))
                .andExpect(jsonPath("$.data.summary").value("管理员校区查询"))
                .andExpect(jsonPath("$.data.sqlPreview", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.planPreview.queryMode").value("ADMIN_SQL"))
                .andExpect(jsonPath("$.data.rows[*].campusName", hasItem(campusName)));
    }

    @AfterEach
    void cleanUp() {
        for (Integer campusId : campusIds) {
            jdbcTemplate.update("DELETE FROM Campus WHERE campus_id = ?", campusId);
        }
        for (Integer userId : userIds) {
            jdbcTemplate.update("DELETE FROM User WHERE user_id = ?", userId);
        }
        campusIds.clear();
        userIds.clear();
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

    private String bearer(CurrentUser user) {
        return "Bearer " + authService.issueToken(user);
    }

    @TestConfiguration
    static class StubLlmConfig {
        @Bean("openAiCompatibleLlmClient")
        LlmClient llmClient() {
            return (systemPrompt, userPrompt) -> """
                    {
                      "queryMode": "ADMIN_SQL",
                      "sql": "SELECT campus_id AS campusId, campus_name AS campusName FROM Campus",
                      "summaryHint": "管理员校区查询",
                      "ambiguity": false,
                      "clarificationOptions": []
                    }
                    """;
        }
    }
}
