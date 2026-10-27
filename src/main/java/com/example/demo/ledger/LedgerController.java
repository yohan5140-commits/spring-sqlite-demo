package com.example.demo.ledger;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    // ---------- 내역 ----------

    @GetMapping("/entries")
    public List<EntryResponse> entries(@RequestParam(required = false) String month) {
        return ledgerService.entries(ledgerService.parseMonth(month));
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse create(@Valid @RequestBody EntryRequest request) {
        return ledgerService.create(request);
    }

    /** 통장 입출내역 등을 한 번에 등록한다. dryRun=true면 저장하지 않고 중복 여부만 돌려준다. */
    @PostMapping("/entries/import")
    public ImportResult importEntries(@Valid @RequestBody ImportRequest request,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return ledgerService.importEntries(request.entries(), dryRun);
    }

    @PutMapping("/entries/{id}")
    public ResponseEntity<EntryResponse> update(@PathVariable Long id, @Valid @RequestBody EntryRequest request) {
        return ledgerService.update(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return ledgerService.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ---------- 월별 요약 ----------

    @GetMapping("/summary")
    public LedgerSummary summary(@RequestParam(required = false) String month) {
        return ledgerService.summary(ledgerService.parseMonth(month));
    }

    // ---------- 예산 ----------

    @GetMapping("/budgets")
    public List<BudgetResponse> budgets() {
        return ledgerService.budgets();
    }

    @PutMapping("/budgets/{category}")
    public BudgetResponse saveBudget(@PathVariable String category, @Valid @RequestBody BudgetRequest request) {
        String trimmed = category.trim();
        if (trimmed.isEmpty() || trimmed.length() > 20) {
            throw new BadRequestException("분류는 1~20자로 입력하세요.");
        }
        return ledgerService.saveBudget(trimmed, request.monthlyAmount());
    }

    @DeleteMapping("/budgets/{category}")
    public ResponseEntity<Void> deleteBudget(@PathVariable String category) {
        return ledgerService.deleteBudget(category.trim())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
