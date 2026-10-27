package com.example.demo.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

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

@SpringBootTest
class LedgerApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM budgets");
    }

    private static String entryJson(String type, String category, long amount, String date) {
        return "{\"type\":\"" + type + "\",\"category\":\"" + category + "\",\"amount\":" + amount
                + ",\"date\":\"" + date + "\"}";
    }

    private long addEntry(String type, String category, long amount, String date) throws Exception {
        String body = mockMvc.perform(post("/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson(type, category, amount, date)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void setBudget(String category, long amount) throws Exception {
        mockMvc.perform(put("/ledger/budgets/{category}", category)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyAmount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    // ---------- 내역 CRUD ----------

    @Test
    void 모든_입력_항목을_저장하고_그대로_돌려준다() throws Exception {
        mockMvc.perform(post("/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"category\":\"식비\",\"amount\":12000,\"date\":\"2026-10-27\","
                                + "\"asset\":\"신한카드\",\"title\":\"점심\",\"memo\":\"팀 회식\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.category").value("식비"))
                .andExpect(jsonPath("$.amount").value(12000))
                .andExpect(jsonPath("$.date").value("2026-10-27"))
                .andExpect(jsonPath("$.asset").value("신한카드"))
                .andExpect(jsonPath("$.title").value("점심"))
                .andExpect(jsonPath("$.memo").value("팀 회식"));
    }

    @Test
    void 선택_항목은_비워도_되고_공백은_null로_저장된다() throws Exception {
        mockMvc.perform(post("/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INCOME\",\"category\":\"급여\",\"amount\":3000000,\"date\":\"2026-10-25\","
                                + "\"asset\":\"  \",\"memo\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.asset").doesNotExist())
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.memo").doesNotExist());
    }

    @Test
    void 월별_목록은_해당_월의_첫날과_마지막날을_포함하고_이웃_달은_제외한다() throws Exception {
        addEntry("EXPENSE", "식비", 1, "2026-09-30");
        addEntry("EXPENSE", "식비", 2, "2026-10-01");
        addEntry("EXPENSE", "식비", 3, "2026-10-31");
        addEntry("EXPENSE", "식비", 4, "2026-11-01");

        mockMvc.perform(get("/ledger/entries?month=2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-10-31"))
                .andExpect(jsonPath("$[1].date").value("2026-10-01"));
    }

    @Test
    void 같은_날짜의_내역은_나중에_등록한_것이_먼저_나온다() throws Exception {
        addEntry("EXPENSE", "식비", 100, "2026-10-10");
        addEntry("EXPENSE", "교통", 200, "2026-10-10");

        mockMvc.perform(get("/ledger/entries?month=2026-10"))
                .andExpect(jsonPath("$[0].category").value("교통"))
                .andExpect(jsonPath("$[1].category").value("식비"));
    }

    @Test
    void 내역을_수정할_수_있다() throws Exception {
        long id = addEntry("EXPENSE", "식비", 12000, "2026-10-27");

        mockMvc.perform(put("/ledger/entries/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INCOME\",\"category\":\"용돈\",\"amount\":50000,\"date\":\"2026-10-28\","
                                + "\"title\":\"생일\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.category").value("용돈"))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.date").value("2026-10-28"))
                .andExpect(jsonPath("$.title").value("생일"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isEqualTo(1);
    }

    @Test
    void 내역을_삭제하면_다시_삭제할_수_없다() throws Exception {
        long id = addEntry("EXPENSE", "식비", 12000, "2026-10-27");

        mockMvc.perform(delete("/ledger/entries/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/ledger/entries/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/ledger/entries?month=2026-10")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 없는_내역_수정은_404() throws Exception {
        mockMvc.perform(put("/ledger/entries/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson("EXPENSE", "식비", 1000, "2026-10-27")))
                .andExpect(status().isNotFound());
    }

    // ---------- 검증 ----------

    @Test
    void 필수_항목이_없으면_필드별_메시지와_함께_400() throws Exception {
        mockMvc.perform(post("/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("구분(수입/지출)은 필수입니다."))
                .andExpect(jsonPath("$.category").value("분류는 필수입니다."))
                .andExpect(jsonPath("$.amount").value("금액은 필수입니다."))
                .andExpect(jsonPath("$.date").value("날짜는 필수입니다."));
    }

    @Test
    void 금액은_1원_이상이어야_한다() throws Exception {
        for (long amount : new long[] {0, -500}) {
            mockMvc.perform(post("/ledger/entries")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(entryJson("EXPENSE", "식비", amount, "2026-10-27")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.amount").value("금액은 1원 이상이어야 합니다."));
        }
    }

    @Test
    void 금액_상한을_넘으면_400() throws Exception {
        mockMvc.perform(post("/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson("EXPENSE", "식비", LedgerService.MAX_AMOUNT + 1, "2026-10-27")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount").value("금액이 너무 큽니다."));
    }

    @Test
    void 구분_날짜_형식이_잘못되면_400() throws Exception {
        mockMvc.perform(post("/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson("TRANSFER", "식비", 1000, "2026-10-27")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson("EXPENSE", "식비", 1000, "2026-13-45")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 분류는_20자_이내이고_슬래시를_쓸_수_없다() throws Exception {
        mockMvc.perform(post("/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson("EXPENSE", "가".repeat(21), 1000, "2026-10-27")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.category").value("분류는 20자 이내로 입력하세요."));
        mockMvc.perform(post("/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson("EXPENSE", "식비/외식", 1000, "2026-10-27")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.category").value("분류에는 '/'를 사용할 수 없습니다."));
    }

    @Test
    void 검증에_실패한_요청은_저장되지_않는다() throws Exception {
        mockMvc.perform(post("/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entryJson("EXPENSE", "식비", -1, "2026-10-27")))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isZero();
    }

    @Test
    void 월_형식이_잘못되면_400() throws Exception {
        mockMvc.perform(get("/ledger/entries?month=2026-13")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/ledger/summary?month=abc")).andExpect(status().isBadRequest());
    }

    // ---------- 월별 요약 ----------

    @Test
    void 내역이_없어도_빈_요약을_반환하고_일별은_그_달_일수만큼_채운다() throws Exception {
        mockMvc.perform(get("/ledger/summary?month=2026-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-02"))
                .andExpect(jsonPath("$.income").value(0))
                .andExpect(jsonPath("$.expense").value(0))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.expenseByCategory.length()").value(0))
                .andExpect(jsonPath("$.daily.length()").value(28))
                .andExpect(jsonPath("$.daily[0].date").value("2026-02-01"))
                .andExpect(jsonPath("$.daily[27].date").value("2026-02-28"));
    }

    @Test
    void 수입_지출_잔액과_지난달_지출을_집계한다() throws Exception {
        addEntry("INCOME", "급여", 3_000_000, "2026-10-25");
        addEntry("INCOME", "부수입", 200_000, "2026-10-10");
        addEntry("EXPENSE", "식비", 12_000, "2026-10-01");
        addEntry("EXPENSE", "식비", 8_000, "2026-10-02");
        addEntry("EXPENSE", "교통", 50_000, "2026-10-03");
        addEntry("EXPENSE", "식비", 70_000, "2026-09-20");
        addEntry("INCOME", "급여", 999, "2026-11-01");

        mockMvc.perform(get("/ledger/summary?month=2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(3_200_000))
                .andExpect(jsonPath("$.expense").value(70_000))
                .andExpect(jsonPath("$.balance").value(3_130_000))
                .andExpect(jsonPath("$.previousExpense").value(70_000))
                .andExpect(jsonPath("$.incomeByCategory[0].category").value("급여"))
                .andExpect(jsonPath("$.incomeByCategory[0].amount").value(3_000_000));
    }

    @Test
    void 지출_분류는_금액_많은_순이고_분류별_예산이_붙는다() throws Exception {
        addEntry("EXPENSE", "식비", 20_000, "2026-10-01");
        addEntry("EXPENSE", "식비", 10_000, "2026-10-02");
        addEntry("EXPENSE", "교통", 50_000, "2026-10-03");
        setBudget("식비", 100_000);

        mockMvc.perform(get("/ledger/summary?month=2026-10"))
                .andExpect(jsonPath("$.expenseByCategory.length()").value(2))
                .andExpect(jsonPath("$.expenseByCategory[0].category").value("교통"))
                .andExpect(jsonPath("$.expenseByCategory[0].amount").value(50_000))
                .andExpect(jsonPath("$.expenseByCategory[0].budget").doesNotExist())
                .andExpect(jsonPath("$.expenseByCategory[1].category").value("식비"))
                .andExpect(jsonPath("$.expenseByCategory[1].amount").value(30_000))
                .andExpect(jsonPath("$.expenseByCategory[1].budget").value(100_000));
    }

    @Test
    void 예산만_있고_지출이_없는_분류도_0원으로_포함한다() throws Exception {
        setBudget("문화", 80_000);
        setBudget("식비", 300_000);
        addEntry("EXPENSE", "식비", 20_000, "2026-10-01");

        mockMvc.perform(get("/ledger/summary?month=2026-10"))
                .andExpect(jsonPath("$.totalBudget").value(380_000))
                .andExpect(jsonPath("$.expenseByCategory.length()").value(2))
                .andExpect(jsonPath("$.expenseByCategory[1].category").value("문화"))
                .andExpect(jsonPath("$.expenseByCategory[1].amount").value(0))
                .andExpect(jsonPath("$.expenseByCategory[1].budget").value(80_000));
    }

    @Test
    void 예산은_매달_같은_금액이_적용된다() throws Exception {
        setBudget("식비", 100_000);

        mockMvc.perform(get("/ledger/summary?month=2026-03"))
                .andExpect(jsonPath("$.expenseByCategory[0].category").value("식비"))
                .andExpect(jsonPath("$.expenseByCategory[0].budget").value(100_000));
    }

    @Test
    void 일별_요약은_내역이_없는_날을_0으로_채운다() throws Exception {
        addEntry("EXPENSE", "식비", 12_000, "2026-10-01");
        addEntry("EXPENSE", "교통", 3_000, "2026-10-01");
        addEntry("INCOME", "급여", 500_000, "2026-10-15");

        mockMvc.perform(get("/ledger/summary?month=2026-10"))
                .andExpect(jsonPath("$.daily.length()").value(31))
                .andExpect(jsonPath("$.daily[0].expense").value(15_000))
                .andExpect(jsonPath("$.daily[0].income").value(0))
                .andExpect(jsonPath("$.daily[1].expense").value(0))
                .andExpect(jsonPath("$.daily[14].income").value(500_000))
                .andExpect(jsonPath("$.daily[14].expense").value(0));
    }

    // ---------- 예산 ----------

    @Test
    void 같은_분류에_예산을_다시_설정하면_금액이_바뀐다() throws Exception {
        setBudget("식비", 100_000);
        setBudget("식비", 250_000);

        mockMvc.perform(get("/ledger/budgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("식비"))
                .andExpect(jsonPath("$[0].monthlyAmount").value(250_000));
    }

    @Test
    void 예산을_삭제할_수_있다() throws Exception {
        setBudget("식비", 100_000);

        mockMvc.perform(delete("/ledger/budgets/{category}", "식비")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/ledger/budgets/{category}", "식비")).andExpect(status().isNotFound());
        mockMvc.perform(get("/ledger/budgets")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 예산_금액은_1원_이상이어야_한다() throws Exception {
        mockMvc.perform(put("/ledger/budgets/{category}", "식비")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyAmount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.monthlyAmount").value("예산은 1원 이상이어야 합니다."));
        mockMvc.perform(put("/ledger/budgets/{category}", "식비")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.monthlyAmount").value("예산 금액은 필수입니다."));
    }

    @Test
    void 분류_이름이_너무_길면_예산_설정은_400() throws Exception {
        mockMvc.perform(put("/ledger/budgets/{category}", "가".repeat(21))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyAmount\":1000}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 화면 ----------

    @Test
    void ledger_경로는_정적_페이지로_연결된다() throws Exception {
        mockMvc.perform(get("/ledger"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/ledger.html"));
    }
}
