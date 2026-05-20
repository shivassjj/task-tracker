package ru.panchenko.task_tracker.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Size(min = 3, max = 50)
        String username,

        @Email
        String email
) {}
