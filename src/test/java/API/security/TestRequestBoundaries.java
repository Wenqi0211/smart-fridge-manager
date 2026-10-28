package API.security;

import api.SmartFridgeApiApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Request parsing, numeric safety and atomicity through the real Spring security/filter chain. */
@SpringBootTest(classes = SmartFridgeApiApplication.class, properties = {
        "smartfridge.jwt.secret=dGhpcy1pcy1hLXRlc3Qta2V5LXdpdGgtMzItYnl0ZXMhISE="
})
@AutoConfigureMockMvc
public class TestRequestBoundaries {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private String user() throws Exception {
        return call(post("/api/auth/register"), null, 201,
                Map.of("username", "bounds-" + UUID.randomUUID(), "password", "strong-test-password")).get("accessToken").asText();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, int expected, Object body) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String payload = result.getResponse().getContentAsString();
        return payload.isBlank() ? json.nullNode() : json.readTree(payload);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, int expected) throws Exception {
        return call(request, token, expected, null);
    }

    private void error(JsonNode body, String code) {
        assertEquals(code, body.get("code").asText());
        assertTrue(body.get("message").asText().length() > 5);
        assertFalse(body.has("exception"));
        assertFalse(body.has("trace"));
    }

    @Test
    void malformedAndMissingJsonUseSafeErrorContract() throws Exception {
        String token = user();
        for (String payload : List.of("{", "null", "{\"foodName\":\"Milk\",\"quantity\":null}",
                "{\"foodName\":\"Milk\",\"quantity\":1.5}", "{\"foodName\":\"Milk\",\"quantity\":2147483648}")) {
            JsonNode result = call(post("/api/grocery/items").contentType(MediaType.APPLICATION_JSON).content(payload), token, 400);
            error(result, "INVALID_REQUEST");
        }
        error(call(post("/api/grocery/items"), token, 400), "INVALID_REQUEST");
        assertEquals(0, call(get("/api/grocery/items"), token, 200).size());
    }

    @Test
    void invalidGroceryInputsDoNotCreateRows() throws Exception {
        String token = user();
        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("foodName", "  "), Map.of("foodName", "Milk", "quantity", -1),
                Map.of("foodName", "Milk", "price", -0.1), Map.of("foodName", "Milk", "price", 1_000_001),
                Map.of("foodName", "Milk", "price", "Infinity"), Map.of("foodName", "Milk", "price", "NaN"))) {
            error(call(post("/api/grocery/items"), token, 400, body), "BAD_REQUEST");
        }
        assertEquals(0, call(get("/api/grocery/items"), token, 200).size());
    }

    @Test
    void zeroAndDefaultNumericValuesRemainValid() throws Exception {
        String token = user();
        JsonNode defaults = call(post("/api/grocery/items"), token, 200, Map.of("foodName", "Garlic"));
        assertEquals(1, defaults.get("quantity").asInt());
        assertEquals(0.0, defaults.get("price").asDouble());
        JsonNode zero = call(post("/api/grocery/items"), token, 200,
                Map.of("foodName", "Garlic", "quantity", 0, "price", 0));
        assertEquals(0, zero.get("quantity").asInt());
    }

    @Test
    void invalidDeltaAndMissingRowsHaveDistinctErrors() throws Exception {
        String token = user();
        for (String query : List.of("", "?delta=nope", "?delta=2147483648")) {
            error(call(patch("/api/grocery/items/missing/quantity" + query), token, 400), "INVALID_REQUEST");
        }
        error(call(patch("/api/grocery/items/missing/quantity?delta=1"), token, 404), "NOT_FOUND");
        error(call(delete("/api/grocery/items/missing"), token, 404), "NOT_FOUND");
    }

    @Test
    void invalidInventoryAndPreferenceDoNotEraseValidState() throws Exception {
        String token = user();
        call(put("/api/preference"), token, 200, Map.of("healthGoal", "FAT_LOSS"));
        error(call(put("/api/preference"), token, 400, Map.of("healthGoal", "unknown")), "BAD_REQUEST");
        error(call(post("/api/inventory"), token, 400, Map.of("foodName", "  ")), "BAD_REQUEST");
        assertEquals("FAT_LOSS", call(get("/api/preference"), token, 200).get("healthGoal").asText());
        assertEquals(0, call(get("/api/inventory"), token, 200).size());
        error(call(get("/api/recommendations/missing"), token, 404), "NOT_FOUND");
    }

    @Test
    void quantityOverflowReturnsConflictWithoutChangingStoredLine() throws Exception {
        String token = user();
        String id = call(post("/api/grocery/items"), token, 200,
                Map.of("foodName", "Garlic", "quantity", Integer.MAX_VALUE)).get("id").asText();
        error(call(patch("/api/grocery/items/" + id + "/quantity?delta=1"), token, 409), "QUANTITY_LIMIT_EXCEEDED");
        assertEquals(Integer.MAX_VALUE, call(get("/api/grocery/items"), token, 200).get(0).get("quantity").asInt());
        assertEquals(0, call(patch("/api/grocery/items/" + id + "/quantity?delta=" + Integer.MIN_VALUE), token, 200)
                .get("quantity").asInt());
    }

    @Test
    void recipeOverflowOnLastIngredientDoesNotAddEarlierIngredients() throws Exception {
        String token = user();
        call(post("/api/grocery/items"), token, 200, Map.of("foodName", "Whole Milk", "quantity", Integer.MAX_VALUE));
        error(call(post("/api/recommendations/rec_001/grocery"), token, 409), "QUANTITY_LIMIT_EXCEEDED");
        JsonNode groceries = call(get("/api/grocery/items"), token, 200);
        assertEquals(1, groceries.size());
        assertEquals("Whole Milk", groceries.get(0).get("name").asText());
        assertEquals(Integer.MAX_VALUE, groceries.get(0).get("quantity").asInt());
    }

    @Test
    void recipeOverflowDoesNotIncrementAnEarlierExistingLine() throws Exception {
        String token = user();
        call(post("/api/grocery/items"), token, 200, Map.of("foodName", "Large Eggs", "quantity", 2));
        call(post("/api/grocery/items"), token, 200, Map.of("foodName", "Whole Milk", "quantity", Integer.MAX_VALUE));
        error(call(post("/api/recommendations/rec_001/grocery"), token, 409), "QUANTITY_LIMIT_EXCEEDED");
        JsonNode groceries = call(get("/api/grocery/items"), token, 200);
        assertEquals(2, groceries.size());
        assertEquals(2, groceries.get(0).get("quantity").asInt());
    }

    @Test
    void concurrentIncrementsAndTogglesDoNotLoseChangesOrAffectAnotherUser() throws Exception {
        String token = user(); String other = user();
        String id = call(post("/api/grocery/items"), token, 200, Map.of("foodName", "Garlic", "quantity", 1)).get("id").asText();
        int attempts = 24;
        List<Callable<JsonNode>> work = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            work.add(() -> call(patch("/api/grocery/items/" + id + "/quantity?delta=1"), token, 200));
            work.add(() -> call(patch("/api/grocery/items/" + id + "/collected"), token, 200));
        }
        parallel(work);
        JsonNode line = call(get("/api/grocery/items"), token, 200).get(0);
        assertEquals(1 + attempts, line.get("quantity").asInt());
        assertFalse(line.get("collected").asBoolean());
        assertEquals(0, call(get("/api/grocery/items"), other, 200).size());
    }

    @Test
    void competingIncrementsAtLimitProduceOneSuccessAndOneConflict() throws Exception {
        String token = user();
        String id = call(post("/api/grocery/items"), token, 200,
                Map.of("foodName", "Garlic", "quantity", Integer.MAX_VALUE - 1)).get("id").asText();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> increment = () -> mvc.perform(patch("/api/grocery/items/" + id + "/quantity?delta=1")
                    .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
            List<Future<Integer>> results = pool.invokeAll(List.of(increment, increment));
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) statuses.add(result.get());
            statuses.sort(Integer::compare);
            assertEquals(List.of(200, 409), statuses);
        } finally { pool.shutdownNow(); }
        assertEquals(Integer.MAX_VALUE, call(get("/api/grocery/items"), token, 200).get(0).get("quantity").asInt());
    }

    @Test
    void concurrentRecipePlanningAndCheckoutNeverLeaveAPartialRecipe() throws Exception {
        String token = user(); String other = user();
        call(post("/api/inventory"), other, 200, Map.of("foodName", "Garlic"));
        List<Callable<JsonNode>> work = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            work.add(() -> call(post("/api/recommendations/rec_001/grocery"), token, 200));
            work.add(() -> call(post("/api/grocery/checkout"), token, 200));
        }
        parallel(work);
        JsonNode groceries = call(get("/api/grocery/items"), token, 200);
        assertTrue(groceries.size() == 0 || groceries.size() == 3);
        if (groceries.size() == 3) {
            Map<String, Integer> quantities = new java.util.HashMap<>();
            for (JsonNode line : groceries) quantities.put(line.get("name").asText(), line.get("quantity").asInt());
            int plans = quantities.get("Large Eggs") / 4;
            assertTrue(plans >= 1 && plans <= 12);
            assertEquals(4 * plans, quantities.get("Large Eggs"));
            assertEquals(14 * plans, quantities.get("Unsalted Butter"));
            assertEquals(30 * plans, quantities.get("Whole Milk"));
        }
        assertEquals(1, call(get("/api/inventory"), other, 200).size());
    }

    private void parallel(List<Callable<JsonNode>> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (Future<JsonNode> future : pool.invokeAll(work)) assertNotNull(future.get());
        } finally { pool.shutdownNow(); }
    }
}
