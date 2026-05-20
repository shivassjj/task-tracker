package ru.panchenko.task_tracker.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectCreateRequest(
        @NotBlank(message = "Имя проекта обязательно")
        @Size(min = 3, max = 50, message = "Имя проекта от 3 до 50 символов")
        String name,

        @Size(max = 300, message = "Описание не более 500 символов")
        String description
) {}
