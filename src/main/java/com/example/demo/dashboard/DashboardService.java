package com.example.demo.dashboard;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.demo.dashboard.DashboardStats.DailyCount;
import com.example.demo.dashboard.DashboardStats.DomainCount;
import com.example.demo.dashboard.DashboardStats.RecentUser;

@Service
public class DashboardService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DOMAIN_LIMIT = 8;
    private static final int RECENT_LIMIT = 5;

    // created_at은 UTC로 저장되므로 날짜 비교와 그룹핑은 KST로 바꾼 뒤 한다
    private static final String KST_DATE = "date(created_at, '+9 hours')";

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardStats stats(int days) {
        LocalDate today = LocalDate.now(KST);
        String from = today.minusDays(days - 1L).toString();

        long total = count("SELECT COUNT(*) FROM users");
        long todayCount = count("SELECT COUNT(*) FROM users WHERE " + KST_DATE + " = ?", today.toString());
        long period = count("SELECT COUNT(*) FROM users WHERE " + KST_DATE + " >= ?", from);
        String latestAt = jdbcTemplate.queryForObject(
                "SELECT datetime(MAX(created_at), '+9 hours') FROM users", String.class);

        return new DashboardStats(total, todayCount, period, latestAt, days,
                daily(today, days, from), domains(from), recent(from));
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /** 가입자가 없는 날도 0으로 채워서 차트의 X축이 끊기지 않게 한다. */
    private List<DailyCount> daily(LocalDate today, int days, String from) {
        Map<String, Long> counts = new HashMap<>();
        jdbcTemplate.query(
                "SELECT " + KST_DATE + " AS d, COUNT(*) AS c FROM users WHERE " + KST_DATE + " >= ? GROUP BY d",
                rs -> {
                    counts.put(rs.getString("d"), rs.getLong("c"));
                }, from);

        List<DailyCount> result = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            result.add(new DailyCount(date, counts.getOrDefault(date, 0L)));
        }
        return result;
    }

    private List<DomainCount> domains(String from) {
        return jdbcTemplate.query(
                "SELECT substr(email, instr(email, '@') + 1) AS domain, COUNT(*) AS c FROM users "
                        + "WHERE email IS NOT NULL AND " + KST_DATE + " >= ? "
                        + "GROUP BY domain ORDER BY c DESC, domain LIMIT " + DOMAIN_LIMIT,
                (rs, i) -> new DomainCount(rs.getString("domain"), rs.getLong("c")), from);
    }

    private List<RecentUser> recent(String from) {
        return jdbcTemplate.query(
                "SELECT id, name, email, datetime(created_at, '+9 hours') AS created_at_kst FROM users "
                        + "WHERE " + KST_DATE + " >= ? ORDER BY created_at DESC, id DESC LIMIT " + RECENT_LIMIT,
                (rs, i) -> new RecentUser(rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("created_at_kst")), from);
    }
}
