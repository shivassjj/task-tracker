package ru.panchenko.task_tracker.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(

    @NotBlank(message = "Username обязателен")
    @Size(min = 3, max = 50, message = "Username от 3 до 50 символов")
    String username,

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    String email,

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, message = "Пароль минимум 8 символов")
    String password
) {}
