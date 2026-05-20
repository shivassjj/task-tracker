package ru.panchenko.task_tracker.user;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.panchenko.task_tracker.user.dto.UserCreateRequest;
import ru.panchenko.task_tracker.user.dto.UserResponse;
import ru.panchenko.task_tracker.user.dto.UserUpdateRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User buildUser(Long id,
                           String username,
                           String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hash");
        return user;
    }

    @Test
    void findById_shouldReturnResponse_whenUserExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                buildUser(1L,
                        "user1",
                        "user1@mail.ru")));

        UserResponse response = userService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("user1");
        assertThat(response.email()).isEqualTo("user1@mail.ru");
    }

    @Test
    void findById_shouldReturnEmpty_whenUserDoesNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("1");
    }

    @Test
    void create_shouldThrow_whenEmailAlreadyTaken() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("user1");
        request.setEmail("user1@mail.ru");
        request.setPassword("123");

        when(userRepository.existsByEmail("user1@mail.ru"))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email уже занят");

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_shouldThrow_whenUsernameAlreadyTaken() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("user1");
        request.setEmail("user1@mail.ru");
        request.setPassword("123");

        when(userRepository.existsByUsername("user1"))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username уже занят");

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_shouldSaveUser_whenDataIsValid() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("user1");
        request.setEmail("user1@mail.ru");
        request.setPassword("123");

        when(userRepository.existsByEmail("user1@mail.ru"))
                .thenReturn(false);
        when(userRepository.existsByUsername("user1"))
                .thenReturn(false);
        when(passwordEncoder.encode("123")).thenReturn("hash");

        User saved = buildUser(1L, "user1", "user1@mail.ru");
        saved.setPasswordHash("hash");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserResponse response = userService.create(request);
        assertThat(response.username()).isEqualTo("user1");
        verify(passwordEncoder).encode("123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void findAll_shouldReturnAllUsers() {
            when(userRepository.findAll()).thenReturn(
                    List.of(
                            buildUser(1L, "user1", "user1@mail.ru"),
                            buildUser(2L, "user2", "user2@mail.ru")
                    )
            );

            List<UserResponse> result = userService.findAll();
            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserResponse::username)
                    .containsExactlyInAnyOrder("user1", "user2");
    }

    @Test
    void update_shouldChangeUsername_whenProvided() {
        User user = buildUser(1L, "user1", "user1@mail.ru");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("newUser1")).thenReturn(false);
        when(userRepository.existsByEmail("newEmail1@mail.ru")).thenReturn(false);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setUsername("newUser1");
        request.setEmail("newEmail1@mail.ru");

        UserResponse response = userService.update(1L, request);

        assertThat(response.username()).isEqualTo("newUser1");
        assertThat(response.email()).isEqualTo("newEmail1@mail.ru");
    }

    @Test
    void update_shouldThrow_whenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(1L, new UserUpdateRequest()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void delete_shouldCallDeleteById_whenUserExists() {
        when(userRepository.findById(1L)).thenReturn(
                Optional.of(buildUser(
                        1L,
                        "user1",
                        "user1@mail.ru")));

        userService.delete(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    void delete_shouldThrow_whenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("1");

        verify(userRepository, never()).deleteById(any());
    }
}
