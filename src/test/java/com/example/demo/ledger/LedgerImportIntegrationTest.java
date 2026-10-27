package com.example.demo.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 통장 입출내역 가져오기(POST /ledger/entries/import) 테스트. */
@SpringBootTest
class LedgerImportIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbcTemplate.update("DELETE FROM ledger_entries");
    }

    private static String row(String type, String category, long amount, String date, String title) {
        return "{\"type\":\"" + type + "\",\"category\":\"" + category + "\",\"amount\":" + amount
                + ",\"date\":\"" + date + "\",\"asset\":\"국민통장\""
                + (title == null ? "" : ",\"title\":\"" + title + "\"") + "}";
    }

    private static String body(String... rows) {
        return "{\"entries\":[" + String.join(",", rows) + "]}";
    }

    private ResultActions importRows(boolean dryRun, String... rows) throws Exception {
        return mockMvc.perform(post("/ledger/entries/import?dryRun=" + dryRun)
                .contentType(MediaType.APPLICATION_JSON).content(body(rows)));
    }

    private int count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class);
    }

    @Test
    void 여러_건을_한_번에_저장한다() throws Exception {
        importRows(false,
                row("EXPENSE", "식비", 4500, "2026-10-01", "스타벅스"),
                row("INCOME", "급여", 3_000_000, "2026-10-25", "10월 급여"),
                row("EXPENSE", "교통", 1400, "2026-10-26", "지하철"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.duplicates.length()").value(3));

        assertThat(count()).isEqualTo(3);
        mockMvc.perform(get("/ledger/entries?month=2026-10"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("지하철"))
                .andExpect(jsonPath("$[0].asset").value("국민통장"));
    }

    @Test
    void 미리보기는_저장하지_않고_중복_여부만_알려준다() throws Exception {
        importRows(false, row("EXPENSE", "식비", 4500, "2026-10-01", "스타벅스")).andExpect(status().isOk());

        importRows(true,
                row("EXPENSE", "식비", 4500, "2026-10-01", "스타벅스"),
                row("EXPENSE", "식비", 5000, "2026-10-01", "스타벅스"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.duplicates[0]").value(true))
                .andExpect(jsonPath("$.duplicates[1]").value(false));

        assertThat(count()).isEqualTo(1);
    }

    @Test
    void 같은_파일을_다시_가져와도_중복_저장되지_않는다() throws Exception {
        String[] rows = {
                row("EXPENSE", "식비", 4500, "2026-10-01", "스타벅스"),
                row("INCOME", "급여", 3_000_000, "2026-10-25", "10월 급여")};

        importRows(false, rows).andExpect(jsonPath("$.created").value(2));
        importRows(false, rows)
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.duplicates[0]").value(true))
                .andExpect(jsonPath("$.duplicates[1]").value(true));

        assertThat(count()).isEqualTo(2);
    }

    @Test
    void 같은_날_같은_거래가_여러_건이어도_정상_거래는_모두_저장한다() throws Exception {
        String coffee = row("EXPENSE", "식비", 4500, "2026-10-01", "스타벅스");

        importRows(false, coffee, coffee).andExpect(jsonPath("$.created").value(2));
        assertThat(count()).isEqualTo(2);

        // 같은 두 건을 다시 가져오면 둘 다 중복
        importRows(false, coffee, coffee).andExpect(jsonPath("$.created").value(0));
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void 기존에_1건_있고_파일에_2건이면_1건만_새로_저장한다() throws Exception {
        String coffee = row("EXPENSE", "식비", 4500, "2026-10-01", "스타벅스");
        importRows(false, coffee).andExpect(jsonPath("$.created").value(1));

        importRows(false, coffee, coffee)
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.duplicates[0]").value(true))
                .andExpect(jsonPath("$.duplicates[1]").value(false));

        assertThat(count()).isEqualTo(2);
    }

    @Test
    void 날짜_구분_금액_내용_중_하나라도_다르면_다른_거래다() throws Exception {
        importRows(false, row("EXPENSE", "식비", 4500, "2026-10-01", "스타벅스")).andExpect(status().isOk());

        importRows(false,
                row("EXPENSE", "식비", 4500, "2026-10-02", "스타벅스"),   // 날짜
                row("INCOME", "기타", 4500, "2026-10-01", "스타벅스"),    // 구분
                row("EXPENSE", "식비", 4600, "2026-10-01", "스타벅스"),   // 금액
                row("EXPENSE", "식비", 4500, "2026-10-01", "이디야"))     // 내용
                .andExpect(jsonPath("$.created").value(4))
                .andExpect(jsonPath("$.skipped").value(0));
    }

    @Test
    void 분류가_달라도_나머지가_같으면_같은_거래로_본다() throws Exception {
        importRows(false, row("EXPENSE", "식비", 4500, "2026-10-01", "스타벅스")).andExpect(status().isOk());

        // 사용자가 미리보기에서 분류만 바꿨더라도 같은 통장 거래이므로 중복
        importRows(false, row("EXPENSE", "기타", 4500, "2026-10-01", "스타벅스"))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void 내용이_없는_거래도_중복을_판단한다() throws Exception {
        importRows(false, row("EXPENSE", "식비", 4500, "2026-10-01", null)).andExpect(jsonPath("$.created").value(1));
        importRows(false, row("EXPENSE", "식비", 4500, "2026-10-01", " ")).andExpect(jsonPath("$.created").value(0));
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void 직접_입력한_내역과도_중복을_판단한다() throws Exception {
        mockMvc.perform(post("/ledger/entries").contentType(MediaType.APPLICATION_JSON)
                        .content(row("EXPENSE", "식비", 4500, "2026-10-01", "스타벅스")))
                .andExpect(status().isCreated());

        importRows(false, row("EXPENSE", "식비", 4500, "2026-10-01", "스타벅스"))
                .andExpect(jsonPath("$.created").value(0));
    }

    @Test
    void 여러_달에_걸친_내역도_한_번에_가져온다() throws Exception {
        importRows(false,
                row("EXPENSE", "식비", 1000, "2026-08-31", "a"),
                row("EXPENSE", "식비", 2000, "2026-09-15", "b"),
                row("EXPENSE", "식비", 3000, "2026-10-01", "c"))
                .andExpect(jsonPath("$.created").value(3));

        mockMvc.perform(get("/ledger/entries?month=2026-09")).andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- 검증 ----------

    @Test
    void 빈_목록은_400() throws Exception {
        mockMvc.perform(post("/ledger/entries/import").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.entries").value("가져올 내역이 없습니다."));
        mockMvc.perform(post("/ledger/entries/import").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 한_번에_1000건을_넘으면_400이고_저장되지_않는다() throws Exception {
        String[] rows = IntStream.range(0, LedgerService.MAX_IMPORT_ROWS + 1)
                .mapToObj(i -> row("EXPENSE", "식비", 1000 + i, "2026-10-01", "n" + i)).toArray(String[]::new);

        importRows(false, rows)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.entries").value("한 번에 가져올 수 있는 내역은 최대 1,000건입니다."));
        assertThat(count()).isZero();
    }

    @Test
    void 정확히_1000건은_가져올_수_있다() throws Exception {
        String[] rows = IntStream.range(0, LedgerService.MAX_IMPORT_ROWS)
                .mapToObj(i -> row("EXPENSE", "식비", 1000 + i, "2026-10-01", "n" + i)).toArray(String[]::new);

        importRows(false, rows).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1000));
        assertThat(count()).isEqualTo(1000);
    }

    @Test
    void 한_건이라도_잘못되면_전체가_저장되지_않는다() throws Exception {
        importRows(false,
                row("EXPENSE", "식비", 4500, "2026-10-01", "정상"),
                row("EXPENSE", "식비", -1, "2026-10-01", "금액 오류"))
                .andExpect(status().isBadRequest());

        assertThat(count()).isZero();
    }

    @Test
    void 잘못된_행은_몇_번째_항목인지_알려준다() throws Exception {
        importRows(false,
                row("EXPENSE", "식비", 4500, "2026-10-01", "정상"),
                row("EXPENSE", " ", 100, "2026-10-01", "분류 없음"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['entries[1].category']").value("분류는 필수입니다."));
    }

    @Test
    void 미리보기_요청도_같은_검증을_거친다() throws Exception {
        importRows(true, row("EXPENSE", "식비", 0, "2026-10-01", "금액 0")).andExpect(status().isBadRequest());
    }

    @Test
    void 응답의_중복_목록은_요청_순서와_같다() throws Exception {
        importRows(false, row("EXPENSE", "식비", 2000, "2026-10-02", "b")).andExpect(status().isOk());

        String[] rows = {
                row("EXPENSE", "식비", 1000, "2026-10-01", "a"),
                row("EXPENSE", "식비", 2000, "2026-10-02", "b"),
                row("EXPENSE", "식비", 3000, "2026-10-03", "c")};
        String flags = importRows(true, rows).andReturn().getResponse().getContentAsString();

        assertThat(flags).contains("\"duplicates\":[false,true,false]");
        assertThat(IntStream.range(0, 3).mapToObj(String::valueOf).collect(Collectors.joining())).isEqualTo("012");
    }
}
