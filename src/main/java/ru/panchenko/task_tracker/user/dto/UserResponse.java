package ru.panchenko.task_tracker.user.dto;

import ru.panchenko.task_tracker.user.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
