package com.example.demo.ledger;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 수입·지출 내역 입력.
 *
 * @param asset 자산/결제수단 (현금, 카드 등, 선택)
 * @param title 내용 (선택)
 */
public record EntryRequest(
        @NotNull(message = "구분(수입/지출)은 필수입니다.") EntryType type,
        @NotBlank(message = "분류는 필수입니다.")
        @Size(max = 20, message = "분류는 20자 이내로 입력하세요.")
        @Pattern(regexp = "[^/]*", message = "분류에는 '/'를 사용할 수 없습니다.") String category,
        @NotNull(message = "금액은 필수입니다.")
        @Positive(message = "금액은 1원 이상이어야 합니다.")
        @Max(value = LedgerService.MAX_AMOUNT, message = "금액이 너무 큽니다.") Long amount,
        @NotNull(message = "날짜는 필수입니다.") LocalDate date,
        @Size(max = 20, message = "자산은 20자 이내로 입력하세요.") String asset,
        @Size(max = 50, message = "내용은 50자 이내로 입력하세요.") String title,
        @Size(max = 100, message = "메모는 100자 이내로 입력하세요.") String memo) {
}
