package com.example.demo.ledger;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends ListCrudRepository<LedgerEntry, Long> {

    /** [from, to) 범위의 내역을 최신 날짜 순으로 조회한다. 날짜는 'yyyy-MM-dd' 문자열이라 사전순 비교가 곧 날짜순이다. */
    @Query("SELECT * FROM ledger_entries WHERE entry_date >= :from AND entry_date < :to "
            + "ORDER BY entry_date DESC, id DESC")
    List<LedgerEntry> findInRange(@Param("from") String from, @Param("to") String to);
}
