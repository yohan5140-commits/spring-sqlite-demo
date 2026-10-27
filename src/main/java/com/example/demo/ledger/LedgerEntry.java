package com.example.demo.ledger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("ledger_entries")
public class LedgerEntry {

    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    private Long id;
    private EntryType type;
    private String category;
    private Long amount;
    @Column("entry_date")
    private String entryDate;
    private String asset;
    private String title;
    private String memo;
    @Column("created_at")
    private String createdAt;

    public static LedgerEntry create(EntryRequest request) {
        LedgerEntry entry = new LedgerEntry();
        entry.apply(request);
        // INSERT 시 모든 컬럼이 바인딩되어 DB의 DEFAULT가 적용되지 않으므로 직접 채운다
        entry.createdAt = LocalDateTime.now(ZoneOffset.UTC).format(UTC_FORMAT);
        return entry;
    }

    public void apply(EntryRequest request) {
        this.type = request.type();
        this.category = request.category().trim();
        this.amount = request.amount();
        this.entryDate = request.date().toString();
        this.asset = blankToNull(request.asset());
        this.title = blankToNull(request.title());
        this.memo = blankToNull(request.memo());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public EntryType getType() { return type; }
    public String getCategory() { return category; }
    public Long getAmount() { return amount; }
    public String getEntryDate() { return entryDate; }
    public String getAsset() { return asset; }
    public String getTitle() { return title; }
    public String getMemo() { return memo; }
}
