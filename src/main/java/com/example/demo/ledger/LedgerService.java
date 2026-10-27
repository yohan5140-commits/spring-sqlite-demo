package com.example.demo.ledger;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ledger.LedgerSummary.CategoryAmount;
import com.example.demo.ledger.LedgerSummary.DailyAmount;

@Service
public class LedgerService {

    /** 1,000억 원. 합계가 Long 범위를 넘지 않도록 한 건의 상한을 둔다. */
    public static final long MAX_AMOUNT = 100_000_000_000L;

    /** 한 번에 가져올 수 있는 최대 건수 */
    public static final int MAX_IMPORT_ROWS = 1000;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LedgerEntryRepository entryRepository;
    private final JdbcTemplate jdbcTemplate;

    public LedgerService(LedgerEntryRepository entryRepository, JdbcTemplate jdbcTemplate) {
        this.entryRepository = entryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** "2026-10" 형식의 월을 파싱한다. 생략하면 KST 기준 이번 달이다. */
    public YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(KST);
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("month는 yyyy-MM 형식이어야 합니다. (예: 2026-10)");
        }
    }

    // ---------- 내역 ----------

    public List<EntryResponse> entries(YearMonth month) {
        return entryRepository.findInRange(firstDay(month), firstDay(month.plusMonths(1))).stream()
                .map(EntryResponse::from).toList();
    }

    public EntryResponse create(EntryRequest request) {
        return EntryResponse.from(entryRepository.save(LedgerEntry.create(request)));
    }

    public Optional<EntryResponse> update(Long id, EntryRequest request) {
        return entryRepository.findById(id).map(entry -> {
            entry.apply(request);
            return EntryResponse.from(entryRepository.save(entry));
        });
    }

    public boolean delete(Long id) {
        if (!entryRepository.existsById(id)) {
            return false;
        }
        entryRepository.deleteById(id);
        return true;
    }

    // ---------- 가져오기 (통장 입출내역) ----------

    /** 같은 거래인지 판단하는 기준. 내용이 없으면 빈 문자열로 통일한다. */
    private record DuplicateKey(String date, EntryType type, long amount, String title) {
        static DuplicateKey of(EntryRequest request) {
            return new DuplicateKey(request.date().toString(), request.type(), request.amount(),
                    request.title() == null ? "" : request.title().trim());
        }
    }

    /**
     * 내역을 한 번에 등록한다. 이미 등록된 거래와 겹치면 건너뛰므로 같은 파일을 다시 가져와도 중복 저장되지 않는다.
     *
     * <p>같은 날 같은 금액의 거래가 정상적으로 여러 건일 수 있어서(예: 커피 두 잔), 중복 여부는 "있다/없다"가 아니라
     * 개수로 판단한다. DB에 이미 m건이 있으면 요청의 같은 거래 중 앞의 m건만 중복으로 본다.
     *
     * @param dryRun true면 저장하지 않고 중복 여부만 알려준다 (미리보기)
     */
    @Transactional
    public ImportResult importEntries(List<EntryRequest> requests, boolean dryRun) {
        Map<DuplicateKey, Integer> existing = existingCounts(requests);
        Map<DuplicateKey, Integer> seen = new HashMap<>();

        List<Boolean> duplicates = new ArrayList<>(requests.size());
        List<LedgerEntry> toSave = new ArrayList<>();
        for (EntryRequest request : requests) {
            DuplicateKey key = DuplicateKey.of(request);
            boolean duplicate = seen.merge(key, 1, Integer::sum) <= existing.getOrDefault(key, 0);
            duplicates.add(duplicate);
            if (!duplicate) {
                toSave.add(LedgerEntry.create(request));
            }
        }

        if (!dryRun) {
            // saveAll은 배치 INSERT를 쓰는데, SQLite 드라이버는 배치에서 행마다의 생성 키를 돌려주지 못해
            // "After saving the identifier must not be null"로 실패한다. 한 트랜잭션 안에서 하나씩 저장한다.
            toSave.forEach(entryRepository::save);
        }
        return new ImportResult(dryRun ? 0 : toSave.size(), requests.size() - toSave.size(), duplicates);
    }

    /** 요청에 포함된 날짜 범위의 기존 내역을 거래 기준별로 센다. */
    private Map<DuplicateKey, Integer> existingCounts(List<EntryRequest> requests) {
        String from = requests.stream().map(r -> r.date().toString()).min(String::compareTo).orElseThrow();
        String to = requests.stream().map(r -> r.date().toString()).max(String::compareTo).orElseThrow();

        Map<DuplicateKey, Integer> counts = new HashMap<>();
        jdbcTemplate.query("SELECT entry_date, type, amount, COALESCE(title, '') AS title, COUNT(*) AS c "
                + "FROM ledger_entries WHERE entry_date >= ? AND entry_date <= ? "
                + "GROUP BY entry_date, type, amount, COALESCE(title, '')",
                rs -> {
                    counts.put(new DuplicateKey(rs.getString("entry_date"), EntryType.valueOf(rs.getString("type")),
                            rs.getLong("amount"), rs.getString("title")), rs.getInt("c"));
                }, from, to);
        return counts;
    }

    // ---------- 예산 ----------

    public List<BudgetResponse> budgets() {
        return jdbcTemplate.query("SELECT category, monthly_amount FROM budgets ORDER BY category",
                (rs, i) -> new BudgetResponse(rs.getString("category"), rs.getLong("monthly_amount")));
    }

    /** 분류별 예산은 하나뿐이므로 있으면 금액을 바꾸고 없으면 새로 만든다. */
    public BudgetResponse saveBudget(String category, long monthlyAmount) {
        jdbcTemplate.update("INSERT INTO budgets (category, monthly_amount) VALUES (?, ?) "
                + "ON CONFLICT(category) DO UPDATE SET monthly_amount = excluded.monthly_amount",
                category, monthlyAmount);
        return new BudgetResponse(category, monthlyAmount);
    }

    public boolean deleteBudget(String category) {
        return jdbcTemplate.update("DELETE FROM budgets WHERE category = ?", category) > 0;
    }

    // ---------- 월별 요약 ----------

    @Transactional(readOnly = true)
    public LedgerSummary summary(YearMonth month) {
        String from = firstDay(month);
        String to = firstDay(month.plusMonths(1));

        Map<EntryType, Long> totals = totalsByType(from, to);
        long income = totals.getOrDefault(EntryType.INCOME, 0L);
        long expense = totals.getOrDefault(EntryType.EXPENSE, 0L);
        long previousExpense = totalsByType(firstDay(month.minusMonths(1)), from).getOrDefault(EntryType.EXPENSE, 0L);

        Map<String, Long> budgets = new HashMap<>();
        budgets().forEach(b -> budgets.put(b.category(), b.monthlyAmount()));
        long totalBudget = budgets.values().stream().mapToLong(Long::longValue).sum();

        return new LedgerSummary(month.toString(), income, expense, income - expense, previousExpense, totalBudget,
                expenseByCategory(from, to, budgets), incomeByCategory(from, to), daily(month, from, to));
    }

    private Map<EntryType, Long> totalsByType(String from, String to) {
        Map<EntryType, Long> result = new HashMap<>();
        jdbcTemplate.query("SELECT type, SUM(amount) AS total FROM ledger_entries "
                + "WHERE entry_date >= ? AND entry_date < ? GROUP BY type",
                rs -> {
                    result.put(EntryType.valueOf(rs.getString("type")), rs.getLong("total"));
                }, from, to);
        return result;
    }

    private Map<String, Long> sumByCategory(EntryType type, String from, String to) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT category, SUM(amount) AS total FROM ledger_entries "
                + "WHERE type = ? AND entry_date >= ? AND entry_date < ? GROUP BY category",
                rs -> {
                    result.put(rs.getString("category"), rs.getLong("total"));
                }, type.name(), from, to);
        return result;
    }

    /** 예산만 있고 지출이 없는 분류도 0원으로 포함해서 "남은 예산"이 보이게 한다. */
    private List<CategoryAmount> expenseByCategory(String from, String to, Map<String, Long> budgets) {
        Map<String, Long> spent = sumByCategory(EntryType.EXPENSE, from, to);
        budgets.keySet().forEach(category -> spent.putIfAbsent(category, 0L));

        List<CategoryAmount> result = new ArrayList<>();
        spent.forEach((category, amount) -> result.add(new CategoryAmount(category, amount, budgets.get(category))));
        result.sort(Comparator.comparingLong(CategoryAmount::amount).reversed().thenComparing(CategoryAmount::category));
        return result;
    }

    private List<CategoryAmount> incomeByCategory(String from, String to) {
        List<CategoryAmount> result = new ArrayList<>();
        sumByCategory(EntryType.INCOME, from, to).forEach((category, amount) ->
                result.add(new CategoryAmount(category, amount, null)));
        result.sort(Comparator.comparingLong(CategoryAmount::amount).reversed().thenComparing(CategoryAmount::category));
        return result;
    }

    /** 내역이 없는 날도 0으로 채워서 차트의 X축이 끊기지 않게 한다. */
    private List<DailyAmount> daily(YearMonth month, String from, String to) {
        Map<String, long[]> byDate = new HashMap<>();
        jdbcTemplate.query("SELECT entry_date, type, SUM(amount) AS total FROM ledger_entries "
                + "WHERE entry_date >= ? AND entry_date < ? GROUP BY entry_date, type",
                rs -> {
                    long[] sums = byDate.computeIfAbsent(rs.getString("entry_date"), d -> new long[2]);
                    sums[EntryType.valueOf(rs.getString("type")) == EntryType.INCOME ? 0 : 1] = rs.getLong("total");
                }, from, to);

        List<DailyAmount> result = new ArrayList<>(month.lengthOfMonth());
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            String date = month.atDay(day).toString();
            long[] sums = byDate.getOrDefault(date, new long[2]);
            result.add(new DailyAmount(date, sums[0], sums[1]));
        }
        return result;
    }

    private static String firstDay(YearMonth month) {
        return month.atDay(1).toString();
    }
}
