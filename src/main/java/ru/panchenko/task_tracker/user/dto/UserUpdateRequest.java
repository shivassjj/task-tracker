package ru.panchenko.task_tracker.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequest {

    @Size(min = 3, max = 50)
    private String username;

    @Email
    private String email;
}
