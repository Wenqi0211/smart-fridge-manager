package api.web;

import api.dto.PreferenceRequest;
import controller.PreferenceController;
import model.HealthGoal;
import model.Preference;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry layer for preference endpoints used by the Flutter frontend.
 * <p>
 * Spring routing stays here. Preference changes delegate to {@link PreferenceController}, which
 * keeps the internal coordination layer separate from the HTTP boundary.
 */
@RestController
@RequestMapping("/api/preference")
public class PreferenceApiController {

    private final PreferenceController preferenceController;

    public PreferenceApiController(PreferenceController preferenceController) {
        this.preferenceController = preferenceController;
    }

    @GetMapping
    public Preference get() {
        return preferenceController.getPreference();
    }

    @PutMapping
    public ResponseEntity<Preference> save(@RequestBody PreferenceRequest body) {
        if (body == null || body.getHealthGoal() == null || body.getHealthGoal().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "healthGoal must be MUSCLE_BUILDING, FAT_LOSS or BLOOD_SUGAR_CARE");
        }
        try {
            HealthGoal goal = HealthGoal.valueOf(body.getHealthGoal().trim().toUpperCase(Locale.ROOT));
            return ResponseEntity.ok(preferenceController.savePreference(goal));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "healthGoal must be MUSCLE_BUILDING, FAT_LOSS or BLOOD_SUGAR_CARE");
        }
    }
}
