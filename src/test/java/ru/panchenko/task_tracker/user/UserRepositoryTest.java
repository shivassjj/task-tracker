package ru.panchenko.task_tracker.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;


    private User saveUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hash");
        return userRepository.save(user);
    }

    @Test
    void findByEmail_shouldReturnUser_whenExists() {
        saveUser("user1", "user1@mail.ru");

        Optional<User> result = userRepository.findByEmail("user1@mail.ru");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("user1@mail.ru");
    }

    @Test
    void findByEmail_shouldReturnEmpty_whenNotExists() {
        Optional<User> result = userRepository.findByEmail("user1");

        assertThat(result).isEmpty();
    }

    @Test
    void existsByEmail_shouldReturnTrue_whenExists() {
        saveUser("user1", "user1@mail.ru");

        assertThat(userRepository.existsByEmail("user1@mail.ru")).isTrue();
    }

    @Test
    void existsByUsername_shouldReturnTrue_whenExists() {
        saveUser("user1", "user1@mail.ru");

        assertThat(userRepository.existsByUsername("user1")).isTrue();
    }
}
