package com.example.demo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * 컨트롤러 → Spring Data JDBC → SQLite(인메모리)까지 실제 구성요소를 모두 거치는 테스트.
 * DB 설정은 src/test/resources/application.properties 에서 분리되어 있다.
 */
@SpringBootTest
class UserApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbcTemplate.update("DELETE FROM users");
    }

    private long createUser(String name, String email) throws Exception {
        String body = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private int countUsers() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }

    @Test
    void 생성한_사용자를_조회할_수_있다() throws Exception {
        long id = createUser("홍길동", "gildong@example.com");

        mockMvc.perform(get("/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("gildong@example.com"));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void 페이지_단위로_조회하고_전체_개수를_알려준다() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createUser("사용자" + i, "user" + i + "@example.com");
        }

        mockMvc.perform(get("/users?page=1&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("사용자3"))
                .andExpect(jsonPath("$.content[1].name").value("사용자4"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));

        // 마지막 페이지는 남은 1건만 반환한다
        mockMvc.perform(get("/users?page=2&size=2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("사용자5"));

        // 범위를 벗어난 페이지는 빈 목록
        mockMvc.perform(get("/users?page=9&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void 정렬_기준과_방향을_지정할_수_있다() throws Exception {
        createUser("나", "b@example.com");
        createUser("다", "c@example.com");
        createUser("가", "a@example.com");

        mockMvc.perform(get("/users?sort=name,asc"))
                .andExpect(jsonPath("$.content[0].name").value("가"))
                .andExpect(jsonPath("$.content[2].name").value("다"));

        mockMvc.perform(get("/users?sort=name,desc"))
                .andExpect(jsonPath("$.content[0].name").value("다"))
                .andExpect(jsonPath("$.content[2].name").value("가"));

        // camelCase 프로퍼티(createdAt)가 created_at 컬럼으로 매핑되는지 확인
        mockMvc.perform(get("/users?sort=email,asc"))
                .andExpect(jsonPath("$.content[0].email").value("a@example.com"));
        mockMvc.perform(get("/users?sort=createdAt,desc&size=3"))
                .andExpect(status().isOk());
    }

    @Test
    void 정렬과_페이징을_함께_사용한다() throws Exception {
        createUser("가", "a@example.com");
        createUser("나", "b@example.com");
        createUser("다", "c@example.com");

        mockMvc.perform(get("/users?sort=name,desc&size=2&page=1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("가"));
    }

    @Test
    void 정렬_값이_모두_같아도_페이지_사이에_중복_누락이_없다() throws Exception {
        long first = createUser("동명", "a@example.com");
        createUser("동명", "b@example.com");
        createUser("동명", "c@example.com");
        createUser("동명", "d@example.com");
        createUser("동명", "e@example.com");
        // 생성 시각과 이름이 전부 같은 상황을 만든다
        jdbcTemplate.update("UPDATE users SET created_at = '2026-01-01 00:00:00'");

        for (String sort : new String[] {"createdAt,desc", "name,asc"}) {
            StringBuilder ids = new StringBuilder();
            for (int page = 0; page < 3; page++) {
                String body = mockMvc.perform(get("/users?size=2&sort=" + sort + "&page=" + page))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
                List<Integer> pageIds = JsonPath.read(body, "$.content[*].id");
                pageIds.forEach(id -> ids.append(id).append(','));
            }

            // 5건이 id 오름차순으로 한 번씩만 나와야 한다
            String expected = LongStream.range(first, first + 5)
                    .mapToObj(String::valueOf).collect(Collectors.joining(",", "", ","));
            assertThat(ids.toString()).as("sort=" + sort).isEqualTo(expected);
        }
    }

    @Test
    void 허용되지_않은_정렬_항목은_400이고_SQL로_전달되지_않는다() throws Exception {
        mockMvc.perform(get("/users?sort=nope,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 페이지_크기는_최대_100으로_제한된다() throws Exception {
        mockMvc.perform(get("/users?size=1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void DB에는_UTC로_저장되고_응답은_KST로_나간다() throws Exception {
        long id = createUser("홍길동", "gildong@example.com");

        String utc = jdbcTemplate.queryForObject("SELECT created_at FROM users WHERE id = ?", String.class, id);
        String kst = jdbcTemplate.queryForObject("SELECT created_at_kst FROM users_kst WHERE id = ?", String.class, id);
        assertThat(utc).isNotEqualTo(kst);

        // 응답 값이 DB의 users_kst 뷰(+9시간)와 같아야 한다
        mockMvc.perform(get("/users/" + id))
                .andExpect(jsonPath("$.createdAt").value(kst));
    }

    @Test
    void 이메일이_중복되면_실제_SQLite_제약조건_위반이_409로_변환된다() throws Exception {
        createUser("홍길동", "gildong@example.com");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"중복\",\"email\":\"gildong@example.com\"}"))
                .andExpect(status().isConflict());

        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void 다른_사용자의_이메일로_수정하면_409() throws Exception {
        createUser("김철수", "chulsoo@example.com");
        long id = createUser("이영희", "younghee@example.com");

        mockMvc.perform(put("/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"이영희\",\"email\":\"chulsoo@example.com\"}"))
                .andExpect(status().isConflict());

        String email = jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, id);
        assertThat(email).isEqualTo("younghee@example.com");
    }

    @Test
    void 수정한_내용이_DB에_반영되고_생성_시각은_유지된다() throws Exception {
        long id = createUser("홍길동", "gildong@example.com");
        String createdBefore = jdbcTemplate.queryForObject("SELECT created_at FROM users WHERE id = ?", String.class, id);

        mockMvc.perform(put("/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"홍길순\",\"email\":\"gilsoon@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("홍길순"));

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM users WHERE id = ?", String.class, id))
                .isEqualTo("홍길순");
        assertThat(jdbcTemplate.queryForObject("SELECT created_at FROM users WHERE id = ?", String.class, id))
                .isEqualTo(createdBefore);
    }

    @Test
    void 삭제하면_조회되지_않는다() throws Exception {
        long id = createUser("홍길동", "gildong@example.com");

        mockMvc.perform(delete("/users/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/users/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/users/" + id)).andExpect(status().isNotFound());

        assertThat(countUsers()).isZero();
    }

    @Test
    void 검증에_실패한_요청은_DB에_저장되지_않는다() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"홍길동\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        assertThat(countUsers()).isZero();
    }
}
