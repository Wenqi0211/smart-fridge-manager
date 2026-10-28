package api.web;

import api.config.SecurityConfig;
import api.security.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {
    private static final long TOKEN_LIFETIME_SECONDS = 1800;
    private final AccountService accounts;
    private final JwtEncoder encoder;

    public AuthApiController(AccountService accounts, JwtEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Credentials request) {
        AccountService.Account account = accounts.register(request.username, request.password);
        return ResponseEntity.status(HttpStatus.CREATED).body(token(account));
    }

    @PostMapping("/token")
    public Map<String, Object> login(@RequestBody Credentials request) {
        return token(accounts.authenticate(request.username, request.password));
    }

    private Map<String, Object> token(AccountService.Account account) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.ISSUER).audience(List.of(SecurityConfig.AUDIENCE))
                .subject(account.getId()).claim("username", account.getUsername())
                .issuedAt(now).expiresAt(now.plusSeconds(TOKEN_LIFETIME_SECONDS))
                .id(UUID.randomUUID().toString()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return Map.of("accessToken", token, "tokenType", "Bearer", "expiresIn", TOKEN_LIFETIME_SECONDS);
    }

    public static class Credentials {
        public String username;
        public String password;
    }
}
