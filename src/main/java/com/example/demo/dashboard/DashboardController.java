package com.example.demo.dashboard;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private static final Set<Integer> ALLOWED_DAYS = Set.of(7, 14, 30, 90);

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public DashboardStats stats(@RequestParam(defaultValue = "14") int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days는 7, 14, 30, 90 중 하나여야 합니다.");
        }
        return dashboardService.stats(days);
    }
}
