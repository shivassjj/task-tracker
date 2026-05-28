package ru.panchenko.task_tracker.user;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.panchenko.task_tracker.user.dto.UserCreateRequest;
import ru.panchenko.task_tracker.user.dto.UserResponse;
import ru.panchenko.task_tracker.user.dto.UserUpdateRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        return UserResponse.from(user);
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email уже занят: " + request.email());
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username уже занят: " + request.username());
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));

        if (request.username() != null) {
            if (userRepository.existsByUsername(request.username())) {
                throw new IllegalArgumentException("Username уже занят");
            }
            user.setUsername(request.username());
        }

        if (request.email() != null) {
            if (userRepository.existsByEmail(request.username())) {
                throw new IllegalArgumentException("Email уже занят");
            }
            user.setEmail(request.email());
        }

        return UserResponse.from(user);
    }

    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("Пользователь не найден: " + id);
        }
        userRepository.deleteById(id);
    }
}
