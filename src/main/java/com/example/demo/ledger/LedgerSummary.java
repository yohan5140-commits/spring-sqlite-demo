package com.example.demo.ledger;

import java.util.List;

/**
 * 월별 요약.
 *
 * @param previousExpense 지난달 총지출 (증감 비교용)
 * @param totalBudget     설정된 예산의 합계 (예산이 없으면 0)
 * @param expenseByCategory 지출 분류별 합계 (예산만 있고 지출이 없는 분류도 0으로 포함, 금액 많은 순)
 * @param daily           해당 월의 모든 날짜별 수입·지출 (내역이 없는 날은 0)
 */
public record LedgerSummary(
        String month,
        long income,
        long expense,
        long balance,
        long previousExpense,
        long totalBudget,
        List<CategoryAmount> expenseByCategory,
        List<CategoryAmount> incomeByCategory,
        List<DailyAmount> daily) {

    /** @param budget 해당 분류의 월 예산 (없으면 null) */
    public record CategoryAmount(String category, long amount, Long budget) {
    }

    public record DailyAmount(String date, long income, long expense) {
    }
}
