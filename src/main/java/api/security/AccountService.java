package api.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local accounts with password hashes only; this is not a durable account database. */
@Service
public class AccountService {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final PasswordEncoder passwords;
    private final String dummyPasswordHash;

    public AccountService(PasswordEncoder passwords) {
        this.passwords = passwords;
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    public Account register(String username, String password) {
        String normalized = normalize(username);
        if (!normalized.matches("[a-z0-9_.@-]{3,64}") || password == null
                || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username must be 3-64 letters/digits/_.@-; password must be 12+ characters and at most 72 UTF-8 bytes");
        }
        Account account = new Account(UUID.randomUUID().toString(), normalized, passwords.encode(password));
        if (accounts.putIfAbsent(normalized, account) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already registered");
        }
        return account;
    }

    public Account authenticate(String username, String password) {
        Account account = accounts.get(normalize(username));
        boolean validPassword = password != null && password.length() >= 12
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean matches = passwords.matches(validPassword ? password : "",
                account == null ? dummyPasswordHash : account.passwordHash);
        if (account == null || !validPassword || !matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return account;
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public static final class Account {
        private final String id;
        private final String username;
        private final String passwordHash;

        private Account(String id, String username, String passwordHash) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
        }

        public String getId() { return id; }
        public String getUsername() { return username; }
    }
}
