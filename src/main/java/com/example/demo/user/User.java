package com.example.demo.user;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public class User {

    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    private Long id;
    private String name;
    private String email;
    @Column("created_at")
    private String createdAt;

    public static User create(String name, String email) {
        User user = new User();
        user.name = name;
        user.email = email;
        // INSERT 시 모든 컬럼이 바인딩되어 DB의 DEFAULT가 적용되지 않으므로 직접 채운다
        user.createdAt = LocalDateTime.now(ZoneOffset.UTC).format(UTC_FORMAT);
        return user;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getCreatedAt() { return createdAt; }

    public void update(String name, String email) {
        this.name = name;
        this.email = email;
    }
}
