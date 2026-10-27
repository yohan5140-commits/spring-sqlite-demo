package com.example.demo.user;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record UserResponse(Long id, String name, String email, String createdAt) {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** DB에는 UTC로 저장되므로 응답할 때만 KST로 변환한다. */
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), toKst(user.getCreatedAt()));
    }

    private static String toKst(String utc) {
        if (utc == null) {
            return null;
        }
        return LocalDateTime.parse(utc, FORMAT)
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(KST)
                .format(FORMAT);
    }
}
