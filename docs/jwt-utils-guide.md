# JwtUtils — разбор класса

## Что такое JWT токен

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkbWl0cml5In0.SflKxwRJSMeKKF2QT4fwpMeJf36P
│                   │ │                        │ │                              │
└─── Header ────────┘ └──────── Payload ───────┘ └──────── Signature ──────────┘
     (алгоритм)              (данные)                  (подпись)
```

Три части, разделённые точками. `JwtUtils` умеет создавать и читать такой токен.

---

## Откуда взять JWT секрет

Секрет — случайная строка минимум 32 байта (256 бит для HS256). Генерируется один раз:

```bash
# OpenSSL
openssl rand -base64 64

# PowerShell (Windows)
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
```

**Никогда не храни секрет в `application.yml` напрямую** — попадёт в git. Используй переменную окружения:

```yaml
# application.yml
jwt:
  secret: ${JWT_SECRET}   # упадёт при старте если переменная не задана — это правильно
  expiration: 86400000    # 86400000 мс = 24 часа
```

Задать переменную локально:
- **IntelliJ IDEA:** `Run → Edit Configurations → Environment variables`
- **PowerShell:** `$env:JWT_SECRET = "твой_секрет"`
- **Файл `.env`** (с плагином EnvFile): добавить `.env` в `.gitignore`

---

## Разбор класса построчно

### Конструктор — инициализация

```java
@Component  // Spring управляет этим классом как бином
public class JwtUtils {

    private final SecretKey secretKey;   // криптографический ключ для подписи
    private final long expirationTime;   // время жизни токена в миллисекундах

    public JwtUtils(@Value("${jwt.secret}") String secret,        // читает из application.yml
                    @Value("${jwt.expiration}") long expirationTime) {

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // ↑ превращает строку "K3pVq2mN..." в криптографический ключ
        // HMAC-SHA = алгоритм подписи, гарантирует что токен не подделан

        this.expirationTime = expirationTime;
    }
}
```

---

### `generateToken` — создание токена

```java
public String generateToken(UserDetails userDetails) {
    return Jwts.builder()                        // начинаем строить токен
            .subject(userDetails.getUsername())  // кладём username в поле sub (Payload)
            .issuedAt(new Date())                // время выдачи токена (iat)
            .expiration(new Date(System.currentTimeMillis() + expirationTime))
            // ↑ время истечения = сейчас + expirationTime (например 86400000 мс = 1 день)
            .signWith(secretKey)                 // подписываем токен секретным ключом
            // ↑ без этого кто угодно мог бы подделать токен
            .compact();                          // собираем всё в строку "xxx.yyy.zzz"
}
```

Результат выглядит так:
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkbWl0cml5IiwiaWF0IjoxNzE2...
```

---

### `extractUsername` — извлечение username из токена

```java
public String extractUsername(String token) {
    return extractClaims(token).getSubject();
    // Claims = всё что лежит в Payload токена
    // getSubject() = достаём поле "sub" (username который клали при генерации)
}
```

---

### `isValid` — проверка токена

```java
public boolean isValid(String token, UserDetails userDetails) {
    String username = extractUsername(token);         // достаём username из токена
    return username.equals(userDetails.getUsername()) // username совпадает с тем что в БД?
            && !isExpired(token);                     // токен ещё не истёк?
    // оба условия должны быть true
}
```

---

### `isExpired` — проверка срока жизни

```java
private boolean isExpired(String token) {
    return extractClaims(token).getExpiration().before(new Date());
    // getExpiration() = достаём дату истечения из токена
    // .before(new Date()) = эта дата раньше чем сейчас?
    // если да — токен просрочен, возвращаем true
}
```

---

### `extractClaims` — расшифровка токена

```java
private Claims extractClaims(String token) {
    return Jwts.parser()               // создаём парсер
            .verifyWith(secretKey)     // говорим каким ключом проверять подпись
            .build()
            .parseSignedClaims(token)  // парсим токен + проверяем подпись
            // ↑ если токен подделан — здесь выбросит исключение
            .getPayload();             // возвращаем Payload (данные внутри токена)
}
```

---

## Что можно улучшить

### 1. Обработать исключения в `extractClaims`

Если токен невалидный — `parseSignedClaims` выбросит исключение прямо в вызывающий код. Лучше поймать здесь:

```java
private Claims extractClaims(String token) {
    try {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    } catch (JwtException e) {
        throw new UsernameNotFoundException("Invalid JWT token");
    }
}
```

### 2. Добавить `userId` как дополнительный claim

Сейчас в токен кладётся только `username`. Добавив `userId`, можно не ходить в БД лишний раз:

```java
.subject(userDetails.getUsername())
.claim("userId", ((UserPrincipal) userDetails).getId())  // дополнительное поле
```