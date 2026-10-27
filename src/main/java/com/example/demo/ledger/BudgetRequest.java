package com.example.demo.ledger;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BudgetRequest(
        @NotNull(message = "예산 금액은 필수입니다.")
        @Positive(message = "예산은 1원 이상이어야 합니다.")
        @Max(value = LedgerService.MAX_AMOUNT, message = "금액이 너무 큽니다.") Long monthlyAmount) {
}
