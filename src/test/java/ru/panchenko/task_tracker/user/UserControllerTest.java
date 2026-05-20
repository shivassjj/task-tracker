package ru.panchenko.task_tracker.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import ru.panchenko.task_tracker.user.dto.UserCreateRequest;
import ru.panchenko.task_tracker.user.dto.UserResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    private UserResponse buildResponse(Long id, String username, String email) {
        return new UserResponse(id, username, email, LocalDateTime.now());
    }

    @Test
    @WithMockUser
    void getAll_shouldReturn200WithUsers() throws Exception {
        when(userService.findAll()).thenReturn(List.of(
                buildResponse(1L, "user1", "user1@mail.ru"),
                buildResponse(2L, "user2", "user2@mail.ru")
        ));

        mockMvc.perform(get("api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("user1"));
    }

    @Test
    @WithMockUser
    void create_shouldReturn201_whenRequestIsValid() throws Exception {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("user1");
        request.setEmail("user1@mail.ru");
        request.setPassword("123");

        when(userService.create(any())).thenReturn(buildResponse(1L, "user1", "user1@mail.ru"));

        mockMvc.perform(post("api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/1"))
                .andExpect(jsonPath("$.username").value("user1"));
    }
}
