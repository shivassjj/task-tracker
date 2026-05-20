package ru.panchenko.task_tracker.project.dto;

import ru.panchenko.task_tracker.project.Project;
import ru.panchenko.task_tracker.user.dto.UserResponse;

import java.time.LocalDateTime;

public record ProjectResponse (
        Long id,
        String name,
        String description,
        UserResponse owner,
        LocalDateTime createdAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                UserResponse.from(project.getOwner()),
                project.getCreatedAt()
        );
    }
}
