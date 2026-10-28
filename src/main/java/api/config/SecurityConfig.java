package api.config;

import api.security.AuthenticatedUserScope;
import api.security.UserOperationFilter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Configuration
public class SecurityConfig {
    public static final String ISSUER = "smartfridge";
    public static final String AUDIENCE = "smartfridge-api";

    @Bean
    public static AuthenticatedUserScope authenticatedUserScope() {
        return new AuthenticatedUserScope();
    }

    @Bean
    public static CustomScopeConfigurer userScopeConfigurer(AuthenticatedUserScope scope) {
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        configurer.addScope("authenticatedUser", scope);
        return configurer;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecret(@Value("${smartfridge.jwt.secret:}") String encodedSecret) {
        byte[] bytes;
        if (encodedSecret.isBlank()) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            LoggerFactory.getLogger(SecurityConfig.class).warn(
                    "Using an ephemeral local JWT key; tokens become invalid after restart. "
                            + "Set SMARTFRIDGE_JWT_SECRET for a stable base64-encoded key.");
        } else {
            bytes = Base64.getDecoder().decode(encodedSecret);
            if (bytes.length < 32) {
                throw new IllegalArgumentException("JWT secret must contain at least 32 random bytes");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secret));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey secret) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secret)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER),
                jwt -> jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                        && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                        && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                        && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                        && "JWT".equals(jwt.getHeaders().get("typ"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                  AuthenticatedUserScope scope) throws Exception {
        // Only Authorization-header bearer tokens are accepted; no cookie-based authentication.
        http.csrf().disable().cors().and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .authorizeRequests()
                .antMatchers("/error").permitAll()
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .antMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/token").permitAll()
                .antMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                .anyRequest().authenticated().and()
                .oauth2ResourceServer().jwt();
        http.addFilterAfter(new UserOperationFilter(scope), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
