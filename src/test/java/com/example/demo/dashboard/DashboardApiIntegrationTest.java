package com.example.demo.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class DashboardApiIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DB_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbcTemplate.update("DELETE FROM users");
        today = LocalDate.now(KST);
    }

    /** KST 기준 날짜·시각을 DB에 저장되는 UTC 문자열로 바꿔 사용자를 넣는다. */
    private void insertUser(String name, String email, LocalDate kstDate, int kstHour) {
        String utc = LocalDateTime.of(kstDate, LocalTime.of(kstHour, 0)).minusHours(9).format(DB_FORMAT);
        jdbcTemplate.update("INSERT INTO users (name, email, created_at) VALUES (?, ?, ?)", name, email, utc);
    }

    @Test
    void 사용자가_없어도_빈_통계를_반환한다() throws Exception {
        mockMvc.perform(get("/dashboard/stats?days=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.today").value(0))
                .andExpect(jsonPath("$.period").value(0))
                .andExpect(jsonPath("$.latestAt").doesNotExist())
                .andExpect(jsonPath("$.daily.length()").value(7))
                .andExpect(jsonPath("$.domains.length()").value(0))
                .andExpect(jsonPath("$.recent.length()").value(0));
    }

    @Test
    void 요약_수치는_기간에_따라_달라진다() throws Exception {
        insertUser("오늘", "a@example.com", today, 12);
        insertUser("사흘전", "b@gmail.com", today.minusDays(3), 10);
        insertUser("오래전", "c@example.com", today.minusDays(40), 10);

        mockMvc.perform(get("/dashboard/stats?days=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.today").value(1))
                .andExpect(jsonPath("$.period").value(2))
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.latestAt").value(today + " 12:00:00"));

        mockMvc.perform(get("/dashboard/stats?days=90"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.period").value(3));
    }

    @Test
    void 일별_가입자는_빈_날짜를_0으로_채워_오래된_순으로_반환한다() throws Exception {
        insertUser("오늘", "a@example.com", today, 12);
        insertUser("사흘전", "b@example.com", today.minusDays(3), 10);

        mockMvc.perform(get("/dashboard/stats?days=7"))
                .andExpect(jsonPath("$.daily.length()").value(7))
                .andExpect(jsonPath("$.daily[0].date").value(today.minusDays(6).toString()))
                .andExpect(jsonPath("$.daily[0].count").value(0))
                .andExpect(jsonPath("$.daily[3].date").value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.daily[3].count").value(1))
                .andExpect(jsonPath("$.daily[6].date").value(today.toString()))
                .andExpect(jsonPath("$.daily[6].count").value(1));
    }

    @Test
    void KST_자정_근처_가입자는_KST_날짜로_집계된다() throws Exception {
        // KST 00:30 = 전날 UTC 15:30. UTC 날짜로 묶으면 어제로 잘못 집계된다
        insertUser("자정직후", "a@example.com", today, 0);
        // KST 23:30 = 같은 날 UTC 14:30
        insertUser("자정직전", "b@example.com", today.minusDays(1), 23);

        mockMvc.perform(get("/dashboard/stats?days=7"))
                .andExpect(jsonPath("$.today").value(1))
                .andExpect(jsonPath("$.daily[6].count").value(1))
                .andExpect(jsonPath("$.daily[5].count").value(1));
    }

    @Test
    void 도메인은_많은_순으로_기간_내_가입자만_집계한다() throws Exception {
        insertUser("1", "a@example.com", today, 9);
        insertUser("2", "b@example.com", today, 10);
        insertUser("3", "c@gmail.com", today, 11);
        insertUser("4", "d@old.com", today.minusDays(40), 11);

        mockMvc.perform(get("/dashboard/stats?days=7"))
                .andExpect(jsonPath("$.domains.length()").value(2))
                .andExpect(jsonPath("$.domains[0].domain").value("example.com"))
                .andExpect(jsonPath("$.domains[0].count").value(2))
                .andExpect(jsonPath("$.domains[1].domain").value("gmail.com"))
                .andExpect(jsonPath("$.domains[1].count").value(1));

        mockMvc.perform(get("/dashboard/stats?days=90"))
                .andExpect(jsonPath("$.domains.length()").value(3));
    }

    @Test
    void 최근_가입자는_최신_순이며_최대_5명이고_KST로_변환된다() throws Exception {
        // 사용자1~6이 KST 10시~15시에 가입
        for (int i = 1; i <= 6; i++) {
            insertUser("사용자" + i, "u" + i + "@example.com", today, i + 9);
        }

        mockMvc.perform(get("/dashboard/stats?days=7"))
                .andExpect(jsonPath("$.recent.length()").value(5))
                .andExpect(jsonPath("$.recent[0].name").value("사용자6"))
                .andExpect(jsonPath("$.recent[0].createdAt").value(today + " 15:00:00"))
                .andExpect(jsonPath("$.recent[4].name").value("사용자2"));
    }

    @Test
    void 허용되지_않은_기간은_400() throws Exception {
        mockMvc.perform(get("/dashboard/stats?days=5")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/dashboard/stats?days=0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/dashboard/stats?days=abc")).andExpect(status().isBadRequest());
    }

    @Test
    void 기간을_생략하면_14일() throws Exception {
        mockMvc.perform(get("/dashboard/stats"))
                .andExpect(jsonPath("$.days").value(14))
                .andExpect(jsonPath("$.daily.length()").value(14));
    }

    @Test
    void dashboard_경로는_정적_페이지로_연결된다() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/dashboard.html"));
    }
}
