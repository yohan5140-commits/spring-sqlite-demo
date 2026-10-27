package com.example.demo.dashboard;

import java.util.List;

/**
 * 대시보드에 표시할 통계. 날짜와 시각은 모두 KST 기준이다.
 *
 * @param total    전체 사용자 수 (기간과 무관)
 * @param today    오늘 가입자 수
 * @param period   조회 기간 내 가입자 수
 * @param latestAt 가장 최근 가입 시각 (사용자가 없으면 null)
 * @param days     조회 기간(일)
 * @param daily    일별 가입자 수 (가입자가 없는 날은 0으로 채움, 오래된 날짜 순)
 * @param domains  조회 기간 내 이메일 도메인별 가입자 수 (많은 순)
 * @param recent   조회 기간 내 최근 가입자
 */
public record DashboardStats(
        long total,
        long today,
        long period,
        String latestAt,
        int days,
        List<DailyCount> daily,
        List<DomainCount> domains,
        List<RecentUser> recent) {

    public record DailyCount(String date, long count) {
    }

    public record DomainCount(String domain, long count) {
    }

    public record RecentUser(Long id, String name, String email, String createdAt) {
    }
}
