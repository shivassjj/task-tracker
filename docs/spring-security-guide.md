# Spring Security + JWT — Полный гайд

> Гайд написан под этот проект: Spring Boot 3.5.4, Java 21, JJWT 0.13.x, PostgreSQL.

---

## Содержание

1. [Теория: как это работает](#1-теория-как-это-работает)
2. [Компоненты Spring Security](#2-компоненты-spring-security)
3. [Шаг 1 — UserDetailsService](#шаг-1--userdetailsservice)
4. [Шаг 2 — JWT утилита](#шаг-2--jwt-утилита)
5. [Шаг 3 — JWT фильтр](#шаг-3--jwt-фильтр)
6. [Шаг 4 — SecurityConfig](#шаг-4--securityconfig)
7. [Шаг 5 — AuthController](#шаг-5--authcontroller)
8. [Шаг 6 — Защита ресурсов (ownership)](#шаг-6--защита-ресурсов-ownership)
9. [Шаг 7 — Обработка ошибок](#шаг-7--обработка-ошибок)
10. [Итоговая схема](#итоговая-схема)
11. [Частые ошибки](#частые-ошибки)

---

## 1. Теория: как это работает

### Аутентификация vs Авторизация

| Понятие | Вопрос | Пример |
|---|---|---|
| **Аутентификация** | Кто ты? | Проверка логина и пароля |
| **Авторизация** | Что тебе можно? | Проверка, что проект принадлежит тебе |

### Почему JWT, а не сессии?

**Сессии (Stateful):** сервер хранит состояние пользователя в памяти/Redis.  
**JWT (Stateless):** сервер не хранит ничего — весь контекст пользователя зашит в токен.

Для REST API JWT предпочтительнее: масштабируется горизонтально, не нужен общий session store.

### Структура JWT токена

```
eyJhbGciOiJIUzI1NiJ9  ← Header (алгоритм подписи)
.
eyJzdWIiOiJ1c2VyQG1haWwuY29tIiwiaWF0IjoxNzE2MDAwMDAwfQ  ← Payload (данные)
.
SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c  ← Signature (подпись)
```

**Payload** содержит claims — утверждения о пользователе:
- `sub` — subject (обычно email или id)
- `iat` — issued at (когда выдан)
- `exp` — expiration (когда истекает)

Токен **не шифруется** — только подписывается. Не клади в него пароли и чувствительные данные.

### Поток запроса

```
Клиент                          Сервер
  |                               |
  |  POST /auth/login             |
  |  { email, password }  ──────► |  1. Проверяет email + password
  |                               |  2. Генерирует JWT
  |  { token: "eyJ..." }  ◄──────|
  |                               |
  |  GET /api/v1/projects         |
  |  Authorization: Bearer eyJ…  ─►|  3. JWT фильтр читает токен
  |                               |  4. Валидирует подпись + exp
  |                               |  5. Кладёт User в SecurityContext
  |  [{ id: 1, name: "..." }] ◄───|  6. Контроллер отвечает
```

---

## 2. Компоненты Spring Security

Прежде чем писать код — нужно понять, что за что отвечает.

### Filter Chain

Spring Security — это цепочка фильтров. Каждый запрос проходит через них по порядку.  
Твой JWT-фильтр встаёт **перед** `UsernamePasswordAuthenticationFilter`.

```
Request → [JwtAuthFilter] → [UsernamePasswordAuthFilter] → ... → Controller
```

### SecurityContext

Хранилище аутентификационных данных для **текущего потока** (thread-local).  
После валидации JWT ты кладёшь пользователя сюда — и он доступен везде в рамках запроса.

```java
SecurityContextHolder.getContext().getAuthentication();  // получить текущего пользователя
```

### UserDetails и UserDetailsService

`UserDetails` — интерфейс Spring Security для представления пользователя.  
`UserDetailsService` — как загрузить пользователя из БД по username (у нас — по email).

### AuthenticationManager

Оркестрирует аутентификацию: берёт `UsernamePasswordAuthenticationToken`,  
вызывает `UserDetailsService`, проверяет пароль через `PasswordEncoder`.

---

## Шаг 1 — UserDetailsService и UserPrincipal

**Задача:** научить Spring Security загружать нашего `User` из БД.

### Почему не делать `User implements UserDetails` напрямую

Казалось бы, проще всего — добавить `implements UserDetails` прямо в JPA-сущность `User`. Но это смешивает два разных слоя:

| Слой | Класс | Отвечает за |
|---|---|---|
| Persistence | `User` | Маппинг на таблицу `users` в БД |
| Security | `UserDetails` | Контракт Spring Security |

**Проблемы при смешивании:**

1. **Утечка security-данных через API.** Если вернуть `User` из контроллера как JSON — в ответ попадут поля `accountNonExpired`, `enabled`, `authorities`. Придётся вешать `@JsonIgnore` на каждое.

2. **Изменение БД ломает security.** Переименовал поле `passwordHash` → `password`? Обязан обновить `getPassword()`. Два несвязанных слоя меняются вместе.

3. **Тяжёлый объект в Security-контексте.** Spring Security хранит `UserDetails` на протяжении всего запроса. Когда это весь `User` с коллекцией `projects` — в памяти крутится лишнее.

### Правильный подход — класс-обёртка UserPrincipal

```
User     = что хранится в БД
UserPrincipal = что знает Spring Security
```

```java
// security/UserPrincipal.java
public class UserPrincipal implements UserDetails {

    private final User user;  // ← хранит ссылку на User, не копирует данные

    public UserPrincipal(User user) {
        this.user = user;  // ← конструктор принимает User и сохраняет ссылку
    }

    // Фасад — делегируем вызовы внутреннему user
    public Long getId()      { return user.getId(); }
    public String getEmail() { return user.getEmail(); }

    @Override
    public String getUsername() { return user.getUsername(); }

    @Override
    public String getPassword() { return user.getPasswordHash(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }

    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isAccountNonLocked()     { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled()              { return true; }
}
```

**Как работает обёртка в памяти:**

```
userRepository.findByUsername("dmitriy")
        │
        ▼
┌─────────────┐
│    User     │  ← объект с реальными данными из БД
│  id = 1     │
│  username   │
│  password   │
└──────┬──────┘
       │ ссылка передаётся в конструктор
       ▼
┌──────────────────┐
│  UserPrincipal   │  ← обёртка, своих данных нет
│  user ──────────►│──► указывает на тот же User
└──────────────────┘
```

`UserPrincipal` не копирует данные — он просто держит ссылку на `User` и делегирует ему вызовы.

### UserDetailsServiceImpl

```java
// security/UserDetailsServiceImpl.java
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)  // ← нужен чтобы Hibernate не упал на lazy-загрузке
    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(login)         // сначала ищем по email
                .or(() -> userRepository.findByUsername(login)) // потом по username
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + login));

        return new UserPrincipal(user);  // ← оборачиваем, не возвращаем User напрямую
    }
}
```

### Как получать текущего пользователя в контроллерах

```java
@GetMapping("/me")
public ResponseEntity<?> getMe(Authentication authentication) {
    UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
    Long userId = principal.getId();
    // ...
}
```

**Итоговое правило:** `UserPrincipal` знает о `User`, но `User` ничего не знает о `UserPrincipal`. Зависимость односторонняя.

---

## Шаг 2 — JWT утилита

**Задача:** генерировать токены и валидировать их.

Сначала добавь секретный ключ в `application.properties`:

```properties
# application.properties
jwt.secret=your-very-long-secret-key-at-least-256-bits-long-for-hs256-algorithm
jwt.expiration=86400000
# 86400000 мс = 24 часа
```

```java
// security/JwtUtils.java
package ru.panchenko.task_tracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtUtils(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    // Генерация токена по UserDetails
    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())          // email в поле sub
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey)
                .compact();
    }

    // Извлечение email из токена
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    // Проверка: токен валиден и принадлежит этому пользователю
    public boolean isValid(String token, UserDetails userDetails) {
        String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isExpired(token);
    }

    private boolean isExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

---

## Шаг 3 — JWT фильтр

**Задача:** перехватить каждый запрос, проверить токен, положить пользователя в SecurityContext.

```java
// security/JwtAuthFilter.java
package ru.panchenko.task_tracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Читаем заголовок Authorization
        String authHeader = request.getHeader("Authorization");

        // 2. Если заголовка нет или он не начинается с "Bearer " — пропускаем
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Извлекаем токен (убираем "Bearer ")
        String token = authHeader.substring(7);

        // 4. Извлекаем email из токена
        String email = jwtUtils.extractEmail(token);

        // 5. Если email есть и пользователь ещё не аутентифицирован в этом запросе
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // 6. Валидируем токен
            if (jwtUtils.isValid(token, userDetails)) {

                // 7. Создаём объект аутентификации
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 8. Кладём в SecurityContext — теперь пользователь аутентифицирован
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 9. Передаём запрос дальше по цепочке
        filterChain.doFilter(request, response);
    }
}
```

### Что происходит в каждом шаге

| Шаг | Если условие не выполнено |
|---|---|
| Нет заголовка Authorization | Запрос идёт дальше без аутентификации (публичные эндпоинты пройдут) |
| Токен невалиден / истёк | SecurityContext остаётся пустым → Spring вернёт 401 |
| Всё ок | Пользователь доступен через `SecurityContextHolder` до конца запроса |

---

## Шаг 4 — SecurityConfig

**Задача:** собрать всё вместе и настроить правила доступа.

```java
// security/SecurityConfig.java
package ru.panchenko.task_tracker.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // включает @PreAuthorize на методах
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)          // REST API — CSRF не нужен
                .authorizeHttpRequests(auth -> auth
                        // Публичные эндпоинты
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Всё остальное — только с токеном
                        .anyRequest().authenticated()
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // без сессий
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

---

## Шаг 5 — AuthController

**Задача:** эндпоинты для регистрации и входа.

### DTO

```java
// security/dto/LoginRequest.java
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}

// security/dto/AuthResponse.java
public record AuthResponse(String token) {}
```

### Контроллер

```java
// security/AuthController.java
package ru.panchenko.task_tracker.security;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.panchenko.task_tracker.security.dto.AuthResponse;
import ru.panchenko.task_tracker.security.dto.LoginRequest;
import ru.panchenko.task_tracker.user.UserService;
import ru.panchenko.task_tracker.user.dto.UserCreateRequest;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtUtils jwtUtils;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody UserCreateRequest request) {
        // Создаём пользователя (UserService уже умеет проверять уникальность email/username)
        userService.create(request);

        // Сразу выдаём токен — пользователь не должен логиниться после регистрации
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
        String token = jwtUtils.generateToken(userDetails);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // AuthenticationManager сам проверит пароль и бросит исключение если неверный
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
        String token = jwtUtils.generateToken(userDetails);
        return ResponseEntity.ok(new AuthResponse(token));
    }
}
```

---

## Шаг 6 — Защита ресурсов (ownership)

**Задача:** пользователь должен иметь доступ только к своим проектам/задачам.

### Получить текущего пользователя

```java
// Вспомогательный метод — можно вынести в утилитный класс
import org.springframework.security.core.context.SecurityContextHolder;
import ru.panchenko.task_tracker.user.User;

public static User getCurrentUser() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
}
```

### В сервисе

```java
// project/ProjectService.java
public ProjectResponse create(ProjectCreateRequest request) {
    User currentUser = (User) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();

    Project project = new Project();
    project.setName(request.name());
    project.setDescription(request.description());
    project.setOwner(currentUser);  // ← автоматически привязываем к текущему пользователю

    return ProjectResponse.from(projectRepository.save(project));
}

public ProjectResponse findById(Long id) {
    Project project = projectRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Project not found"));

    User currentUser = (User) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();

    // Проверка ownership
    if (!project.getOwner().getId().equals(currentUser.getId())) {
        throw new AccessDeniedException("You don't have access to this project");
    }

    return ProjectResponse.from(project);
}
```

### @PreAuthorize для тонкой авторизации

Если логика авторизации сложная — выносим в отдельный компонент:

```java
// security/ProjectSecurity.java
@Component("projectSecurity")
@RequiredArgsConstructor
public class ProjectSecurity {

    private final ProjectRepository projectRepository;

    public boolean isOwner(Long projectId, UserDetails principal) {
        return projectRepository.findById(projectId)
                .map(p -> p.getOwner().getEmail().equals(principal.getUsername()))
                .orElse(false);
    }
}
```

```java
// project/ProjectController.java
@DeleteMapping("/{id}")
@PreAuthorize("@projectSecurity.isOwner(#id, principal)")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    projectService.delete(id);
    return ResponseEntity.noContent().build();
}
```

---

## Шаг 7 — Обработка ошибок

**Задача:** возвращать корректные HTTP-коды при проблемах с аутентификацией.

```java
// common/SecurityExceptionHandler.java
package ru.panchenko.task_tracker.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SecurityExceptionHandler {

    // Неверный логин/пароль
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials(BadCredentialsException ex) {
        return new ErrorResponse("Invalid email or password");
    }

    // Нет прав на ресурс
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return new ErrorResponse("Access denied");
    }
}
```

Также настрой `AuthenticationEntryPoint` для случая, когда токен отсутствует или невалиден:

```java
// Добавить в SecurityConfig.filterChain():
.exceptionHandling(ex -> ex
    .authenticationEntryPoint((request, response, authException) -> {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"message\": \"Unauthorized\"}");
    })
    .accessDeniedHandler((request, response, accessDeniedException) -> {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"message\": \"Access denied\"}");
    })
)
```

---

## Итоговая схема

```
src/main/java/ru/panchenko/task_tracker/
├── security/
│   ├── SecurityConfig.java          ← Настройка цепочки фильтров
│   ├── JwtUtils.java                ← Генерация и валидация токена
│   ├── JwtAuthFilter.java           ← Фильтр: читает токен из заголовка
│   ├── UserDetailsServiceImpl.java  ← Загрузка User из БД
│   ├── ProjectSecurity.java         ← Проверки ownership для @PreAuthorize
│   ├── AuthController.java          ← /auth/register, /auth/login
│   └── dto/
│       ├── LoginRequest.java
│       └── AuthResponse.java
```

```
application.properties:
  jwt.secret=...   (минимум 32 символа для HS256)
  jwt.expiration=86400000
```

---

## Частые ошибки

| Ошибка | Причина | Решение |
|---|---|---|
| `403` вместо `401` | Не настроен `AuthenticationEntryPoint` | Добавить в `exceptionHandling()` |
| Токен истёк — нет понятной ошибки | `ExpiredJwtException` не обрабатывается | Поймать в фильтре или `@ExceptionHandler` |
| Пароль не проверяется | `PasswordEncoder` не тот же бин | Один `@Bean PasswordEncoder` на всё приложение |
| Все запросы 401 | Фильтр добавлен не перед `UsernamePasswordAuthFilter` | `addFilterBefore(jwtFilter, UsernamePasswordAuthFilter.class)` |
| `circular dependency` | `SecurityConfig` зависит от `UserService`, а тот — от `SecurityConfig` | Инжектировать `UserDetailsService`, а не `UserService` в `SecurityConfig` |
| Секрет слишком короткий | HS256 требует минимум 256 бит (32 байта) | Использовать строку длиной 32+ символов |
| JWT в логах | Случайно залогировал `authHeader` | Никогда не логировать токены |