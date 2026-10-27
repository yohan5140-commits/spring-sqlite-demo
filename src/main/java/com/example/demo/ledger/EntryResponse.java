package com.example.demo.ledger;

public record EntryResponse(
        Long id, EntryType type, String category, Long amount, String date, String asset, String title, String memo) {

    public static EntryResponse from(LedgerEntry entry) {
        return new EntryResponse(entry.getId(), entry.getType(), entry.getCategory(), entry.getAmount(),
                entry.getEntryDate(), entry.getAsset(), entry.getTitle(), entry.getMemo());
    }
}
