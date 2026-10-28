package API.security;

import api.SmartFridgeApiApplication;
import api.config.SecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.SecretKey;
import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full Spring HTTP/filter/service-chain tests, not direct controller mocks. */
@SpringBootTest(classes = SmartFridgeApiApplication.class, properties = {
        "smartfridge.jwt.secret=dGhpcy1pcy1hLXRlc3Qta2V5LXdpdGgtMzItYnl0ZXMhISE="
})
@AutoConfigureMockMvc
public class TestAuthenticatedWorkflow {
    private static final String PASSWORD = "a-strong-test-password";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtEncoder encoder;
    @Autowired private SecretKey signingKey;

    private String user() throws Exception {
        return register("user-" + UUID.randomUUID());
    }

    private String register(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode token = json.readTree(response);
        assertEquals("Bearer", token.get("tokenType").asText());
        assertEquals(1800, token.get("expiresIn").asInt());
        return token.get("accessToken").asText();
    }

    private JsonNode request(MockHttpServletRequestBuilder request, String token) throws Exception {
        String response = mvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return response.isBlank() ? json.nullNode() : json.readTree(response);
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(value));
    }

    @Test
    void registrationLoginAndNormalizedIdentityKeepState() throws Exception {
        String username = "Login-" + UUID.randomUUID();
        String token = register(username);
        request(body(post("/api/inventory"), Map.of("foodName", "Whole Milk")), token);
        String response = mvc.perform(body(post("/api/auth/token"),
                        Map.of("username", username.toUpperCase(), "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String loginToken = json.readTree(response).get("accessToken").asText();
        assertEquals(1, request(get("/api/inventory"), loginToken).size());
        mvc.perform(body(post("/api/auth/register"), Map.of("username", username, "password", PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void authenticationRejectsMissingMalformedUnsignedAndTamperedTokens() throws Exception {
        mvc.perform(get("/api/inventory")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/grocery/items").header("Authorization", "Bearer nonsense"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/preference").header("Authorization", "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJhIn0."))
                .andExpect(status().isUnauthorized());
        String token = user();
        String[] pieces = token.split("\\.");
        pieces[1] = pieces[1].substring(0, pieces[1].length() - 1)
                + (pieces[1].endsWith("A") ? "B" : "A");
        mvc.perform(get("/api/inventory").header("Authorization", "Bearer " + String.join(".", pieces)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedButExpiredWrongIssuerOrWrongAudienceTokensAreRejected() throws Exception {
        Instant now = Instant.now();
        for (JwtClaimsSet claims : List.of(
                claims(SecurityConfig.ISSUER, SecurityConfig.AUDIENCE, now.minusSeconds(180)),
                claims("another-app", SecurityConfig.AUDIENCE, now.plusSeconds(1800)),
                claims(SecurityConfig.ISSUER, "another-api", now.plusSeconds(1800)))) {
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
            mvc.perform(get("/api/inventory").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void wrongAlgorithmTypeAndMissingRequiredClaimsAreRejected() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet valid = claims(SecurityConfig.ISSUER, SecurityConfig.AUDIENCE, now.plusSeconds(1800));
        for (JwtClaimsSet claims : List.of(
                JwtClaimsSet.builder().issuer(SecurityConfig.ISSUER).subject("user")
                        .issuedAt(now).expiresAt(now.plusSeconds(1800)).build(),
                JwtClaimsSet.builder().issuer(SecurityConfig.ISSUER).audience(List.of(SecurityConfig.AUDIENCE))
                        .issuedAt(now).expiresAt(now.plusSeconds(1800)).build(),
                JwtClaimsSet.builder().issuer(SecurityConfig.ISSUER).audience(List.of(SecurityConfig.AUDIENCE))
                        .subject("user").issuedAt(now).build())) {
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
            mvc.perform(get("/api/inventory").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        String wrongType = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("not-a-jwt").build(), valid)).getTokenValue();
        mvc.perform(get("/api/inventory").header("Authorization", "Bearer " + wrongType))
                .andExpect(status().isUnauthorized());
        // Sign with the real key but a disallowed algorithm, isolating the algorithm allowlist.
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"HS512\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(json.writeValueAsBytes(Map.of("iss", SecurityConfig.ISSUER,
                "aud", List.of(SecurityConfig.AUDIENCE), "sub", "user", "iat", now.getEpochSecond(),
                "exp", now.plusSeconds(1800).getEpochSecond())));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new javax.crypto.spec.SecretKeySpec(signingKey.getEncoded(), "HmacSHA512"));
        String wrongAlgorithm = signingInput + "." + b64.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        mvc.perform(get("/api/inventory").header("Authorization", "Bearer " + wrongAlgorithm))
                .andExpect(status().isUnauthorized());
    }

    private JwtClaimsSet claims(String issuer, String audience, Instant expires) {
        return JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now().minusSeconds(3600))
                .expiresAt(expires).build();
    }

    @Test
    void badCredentialsAndUnsafePasswordLengthsAreRejected() throws Exception {
        String username = "pwd-" + UUID.randomUUID();
        register(username);
        mvc.perform(body(post("/api/auth/token"), Map.of("username", username, "password", "wrong-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/auth/token"), Map.of("username", "missing-user", "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/auth/token"), Map.of("username", username, "password", "x".repeat(100))))
                .andExpect(status().isUnauthorized());
        for (String password : List.of("short", "x".repeat(73), "密".repeat(25))) {
            mvc.perform(body(post("/api/auth/register"), Map.of("username", "bad-" + UUID.randomUUID(), "password", password)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(body(post("/api/auth/register"), Map.of("username", "!", "password", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoUsersCannotReadOrModifyEachOthersInventoryPreferencesAndGroceries() throws Exception {
        String alice = user(); String bob = user();
        request(body(post("/api/inventory"), Map.of("foodName", "Whole Milk")), alice);
        request(body(put("/api/preference"), Map.of("healthGoal", "MUSCLE_BUILDING")), alice);
        JsonNode line = request(body(post("/api/grocery/items"),
                Map.of("foodName", "Garlic", "quantity", 2, "price", 3.0)), alice);
        String id = line.get("id").asText();
        assertEquals(1, request(get("/api/inventory"), alice).size());
        assertEquals(0, request(get("/api/inventory"), bob).size());
        assertTrue(request(get("/api/preference"), bob).isNull());
        assertEquals(0, request(get("/api/grocery/items"), bob).size());
        mvc.perform(patch("/api/grocery/items/" + id + "/collected").header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/grocery/items/" + id + "/quantity?delta=3").header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/grocery/items/" + id).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        assertEquals(2, request(get("/api/grocery/items"), alice).get(0).get("quantity").asInt());
        assertFalse(request(get("/api/grocery/items"), alice).get(0).get("collected").asBoolean());
        request(post("/api/grocery/checkout"), bob);
        assertEquals(1, request(get("/api/inventory"), alice).size());
        assertEquals(1, request(get("/api/grocery/items"), alice).size());
        assertEquals("MUSCLE_BUILDING", request(get("/api/preference"), alice).get("healthGoal").asText());
    }

    @Test
    void inventoryRecipeMissingIngredientsMergeAndCheckoutWorkThroughHttp() throws Exception {
        String token = user();
        request(body(post("/api/inventory"), Map.of("foodName", "Large Eggs")), token);
        request(body(put("/api/preference"), Map.of("healthGoal", "MUSCLE_BUILDING")), token);
        JsonNode detail = request(get("/api/recommendations/rec_001"), token);
        assertNotNull(detail);
        JsonNode first = request(post("/api/recommendations/rec_001/grocery"), token);
        assertEquals(2, first.get("addedCount").asInt());
        assertEquals(0, first.get("mergedCount").asInt());
        JsonNode second = request(post("/api/recommendations/rec_001/grocery"), token);
        assertEquals(0, second.get("addedCount").asInt());
        assertEquals(2, second.get("mergedCount").asInt());
        JsonNode groceries = request(get("/api/grocery/items"), token);
        assertEquals(2, groceries.size());
        Map<String, Integer> quantities = new java.util.HashMap<>();
        for (JsonNode line : groceries) { quantities.put(line.get("name").asText(), line.get("quantity").asInt()); }
        assertEquals(28, quantities.get("Unsalted Butter"));
        assertEquals(60, quantities.get("Whole Milk"));
        request(post("/api/grocery/checkout"), token);
        assertEquals(0, request(get("/api/grocery/items"), token).size());
        assertEquals(0, request(get("/api/inventory"), token).size());
        assertTrue(request(get("/api/preference"), token).isNull());
    }

    @Test
    void concurrentRecipeAddsForSameUserMergeWithoutLostUpdates() throws Exception {
        String token = user();
        int attempts = 12;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<JsonNode>> calls = java.util.stream.IntStream.range(0, attempts)
                    .mapToObj(i -> (Callable<JsonNode>) () -> request(post("/api/recommendations/rec_001/grocery"), token))
                    .collect(java.util.stream.Collectors.toList());
            for (Future<JsonNode> future : pool.invokeAll(calls)) { assertNotNull(future.get()); }
        } finally { pool.shutdownNow(); }
        JsonNode groceries = request(get("/api/grocery/items"), token);
        assertEquals(3, groceries.size());
        Map<String, Integer> quantities = new java.util.HashMap<>();
        for (JsonNode line : groceries) { quantities.put(line.get("name").asText(), line.get("quantity").asInt()); }
        assertEquals(4 * attempts, quantities.get("Large Eggs"));
        assertEquals(14 * attempts, quantities.get("Unsalted Butter"));
        assertEquals(30 * attempts, quantities.get("Whole Milk"));
    }
}
