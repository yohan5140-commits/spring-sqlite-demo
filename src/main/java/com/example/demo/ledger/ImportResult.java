package com.example.demo.ledger;

import java.util.List;

/**
 * 가져오기 결과.
 *
 * @param created    새로 저장한 건수 (미리보기에서는 0)
 * @param skipped    이미 등록된 내역과 겹쳐서 건너뛴(건너뛸) 건수
 * @param duplicates 요청한 내역과 같은 순서로, 각 내역이 중복이면 true
 */
public record ImportResult(int created, int skipped, List<Boolean> duplicates) {
}
