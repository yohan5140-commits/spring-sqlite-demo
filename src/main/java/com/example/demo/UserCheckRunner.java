package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserCheckRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public UserCheckRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.query(
                "SELECT id, name, email, created_at_kst FROM users_kst",
                (rs, i) -> rs.getInt("id") + " | " + rs.getString("name") + " | "
                        + rs.getString("email") + " | " + rs.getString("created_at_kst"))
                .forEach(System.out::println);
    }
}
