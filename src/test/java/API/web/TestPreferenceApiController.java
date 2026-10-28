package API.web;


import api.dto.PreferenceRequest;
import api.web.PreferenceApiController;
import controller.PreferenceController;
import model.Preference;
import service.PreferenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PreferenceApiController.
 * Directly instantiates the controller without starting a Spring server.
 * Each test targets one specific endpoint or branch.
 */
public class TestPreferenceApiController {

    private PreferenceApiController preferenceApiController;

    @BeforeEach
    void setUp() {
        PreferenceController preferenceController =
                new PreferenceController(new PreferenceService());
        preferenceApiController = new PreferenceApiController(preferenceController);
    }

    //  GET /api/preference 

    /**
     * Tests GET returns null when no preference has been saved yet.
     */
    @Test
    void testGetReturnsNullWhenNoPreferenceSaved() {
        assertNull(preferenceApiController.get());
    }

    /**
     * Tests GET returns saved preference after PUT.
     */
    @Test
    void testGetReturnsSavedPreferenceAfterPut() {
        preferenceApiController.save(makeRequest("FAT_LOSS"));
        Preference result = preferenceApiController.get();
        assertNotNull(result);
        assertEquals("FAT_LOSS", result.getHealthGoal().name());
    }

    // PUT /api/preference

    /**
     * Tests PUT with valid health goal returns 200 OK.
     * Branch: body is valid, goal parses correctly → return ok.
     */
    @Test
    void testSaveWithValidGoalReturnsOk() {
        ResponseEntity<Preference> response = preferenceApiController.save(makeRequest("FAT_LOSS"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAT_LOSS", response.getBody().getHealthGoal().name());
    }

    /**
     * Tests PUT with lowercase goal name returns 200 OK.
     * Verifies toUpperCase() normalization works correctly.
     */
    @Test
    void testSaveWithLowercaseGoalReturnsOk() {
        ResponseEntity<Preference> response = preferenceApiController.save(makeRequest("fat_loss"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    /**
     * Tests PUT with MUSCLE_BUILDING returns 200 OK.
     */
    @Test
    void testSaveWithMuscleBuildingGoalReturnsOk() {
        ResponseEntity<Preference> response =
                preferenceApiController.save(makeRequest("MUSCLE_BUILDING"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    /**
     * Tests PUT with null body returns 400 Bad Request.
     * Branch: body == null → return badRequest.
     */
    @Test
    void testSaveWithNullBodyReturnsBadRequest() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> preferenceApiController.save(null));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    /**
     * Tests PUT with null health goal returns 400 Bad Request.
     * Branch: healthGoal == null → return badRequest.
     */
    @Test
    void testSaveWithNullHealthGoalReturnsBadRequest() {
        PreferenceRequest req = new PreferenceRequest();
        req.setHealthGoal(null);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> preferenceApiController.save(req));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    /**
     * Tests PUT with blank health goal returns 400 Bad Request.
     * Branch: healthGoal is blank → return badRequest.
     */
    @Test
    void testSaveWithBlankHealthGoalReturnsBadRequest() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> preferenceApiController.save(makeRequest("   ")));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    /**
     * Tests PUT with invalid health goal string returns 400 Bad Request.
     * Branch: HealthGoal.valueOf() throws IllegalArgumentException → return badRequest.
     */
    @Test
    void testSaveWithInvalidGoalStringReturnsBadRequest() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> preferenceApiController.save(makeRequest("INVALID_GOAL")));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    // Helper

    /**
     * Creates a PreferenceRequest with the given health goal string.
     */
    private PreferenceRequest makeRequest(String healthGoal) {
        PreferenceRequest req = new PreferenceRequest();
        req.setHealthGoal(healthGoal);
        return req;
    }
}