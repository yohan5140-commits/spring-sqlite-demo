package com.example.demo.ledger;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 통장 입출내역 등을 한 번에 등록하기 위한 요청. */
public record ImportRequest(
        @NotEmpty(message = "가져올 내역이 없습니다.")
        @Size(max = LedgerService.MAX_IMPORT_ROWS, message = "한 번에 가져올 수 있는 내역은 최대 1,000건입니다.")
        List<@NotNull(message = "내역이 비어 있습니다.") @Valid EntryRequest> entries) {
}
