package service;

import api.dto.RecipeDetailResponse;
import model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Business-outcome boundaries: exact scoring, expiry, duplicate batches and optional ingredients. */
public class TestRecommendationBoundaries {
    private final FoodCategory category = new FoodCategory("food", "Food", "box");
    private final IFoodCatalog catalog = new FoodCatalog(List.of(
            new FoodCatalogEntry("Egg", 7, category), new FoodCatalogEntry("Milk", 7, category)));

    private FoodItem batch(String name, int quantity, int expiryDays) {
        return new FoodItem("batch-" + name + expiryDays, name, category, quantity, "count",
                LocalDate.now().toString(), LocalDate.now().plusDays(expiryDays).toString());
    }

    private Recipe recipe(int cookTime, Recipe.HealthTag tag, List<Recipe.Ingredient> ingredients) {
        return Recipe.loaded("r", "Recipe", new RecipeCategory("quick", "Quick", "bolt"),
                List.of(tag), ingredients, List.of(), 4.5, cookTime, 250, "steps");
    }

    private List<Recipe.Ingredient> required() {
        return List.of(new Recipe.Ingredient("Egg", "2 count", false),
                new Recipe.Ingredient("Milk", "1 count", false));
    }

    @Test
    void completeUrgentCoverageWithAlignedGoalReachesExactly100() {
        Recipe recipe = recipe(15, Recipe.HealthTag.HIGH_PROTEIN, required());
        Recipe scored = new RecommendationService(List.of(recipe), catalog).getRecommendations(
                List.of(batch("Egg", 2, 0), batch("Milk", 1, 0)), new Preference("p", HealthGoal.MUSCLE_BUILDING)).get(0);
        assertEquals(100.0, scored.getMatchScore()); // 60 coverage + 20 urgent + 15 goal + 5 time.
        assertEquals(2, scored.getUrgentMatchedCount());
        assertEquals(List.of(), scored.getMissingIngredients());
    }

    @Test
    void partialCoverageEarnsOnlyItsActualCoverageAndOneUrgentBonus() {
        Recipe recipe = recipe(15, Recipe.HealthTag.HIGH_PROTEIN, required());
        Recipe scored = new RecommendationService(List.of(recipe), catalog).getRecommendations(
                List.of(batch("Egg", 2, 0)), new Preference("p", HealthGoal.MUSCLE_BUILDING)).get(0);
        assertEquals(60.0, scored.getMatchScore()); // 30 + 10 + 15 + 5.
        assertEquals(List.of("Milk"), scored.getMissingIngredients());
    }

    @Test
    void expiredAndEmptyBatchesDoNotCountAsAvailableInScoreOrDetail() {
        Recipe recipe = recipe(40, Recipe.HealthTag.HIGH_PROTEIN, required());
        List<FoodItem> inventory = List.of(batch("Egg", 2, -1), batch("Milk", 0, 0));
        Recipe scored = new RecommendationService(List.of(recipe), catalog).getRecommendations(inventory, null).get(0);
        assertEquals(0.0, scored.getMatchScore());
        assertEquals(2, scored.getMissingIngredients().size());
        RecipeDetailResponse detail = RecipeDetailResponse.from(scored, inventory, catalog::canonicalFoodName);
        assertEquals(0, detail.getMatchPercent());
        assertTrue(detail.getRequiredIngredients().stream().allMatch(i -> !i.isInFridge() && "need to buy".equals(i.getStatus())));
    }

    @Test
    void urgentBatchIsChosenEvenWhenFreshBatchWasInsertedFirst() {
        Recipe recipe = recipe(40, Recipe.HealthTag.HIGH_PROTEIN,
                List.of(new Recipe.Ingredient("Egg", "2 count", false)));
        List<FoodItem> inventory = List.of(batch("Egg", 9, 7), batch("Egg", 2, 0));
        Recipe scored = new RecommendationService(List.of(recipe), catalog).getRecommendations(inventory, null).get(0);
        assertEquals(70.0, scored.getMatchScore());
        assertEquals(1, scored.getUrgentMatchedCount());
        RecipeDetailResponse detail = RecipeDetailResponse.from(scored, inventory, catalog::canonicalFoodName);
        assertEquals("2 count", detail.getRequiredIngredients().get(0).getCurrentStockText());
    }

    @Test
    void optionalIngredientsDoNotReduceCoverageOrMatchPercentage() {
        Recipe recipe = recipe(40, Recipe.HealthTag.HIGH_PROTEIN,
                List.of(new Recipe.Ingredient("Egg", "2 count", false), new Recipe.Ingredient("Salt", "1 pinch", true)));
        List<FoodItem> inventory = List.of(batch("Egg", 2, 3));
        Recipe scored = new RecommendationService(List.of(recipe), catalog).getRecommendations(inventory, null).get(0);
        assertEquals(60.0, scored.getMatchScore());
        assertTrue(scored.getMissingIngredients().isEmpty());
        assertEquals(100, RecipeDetailResponse.from(scored, inventory, catalog::canonicalFoodName).getMatchPercent());
    }

    @ParameterizedTest
    @CsvSource({"15,5", "16,3", "30,3", "31,0"})
    void cookingConvenienceUsesDocumentedBoundaries(int minutes, double points) {
        Recipe recipe = recipe(minutes, Recipe.HealthTag.HIGH_PROTEIN, required());
        Recipe scored = new RecommendationService(List.of(recipe), catalog).getRecommendations(List.of(), null).get(0);
        assertEquals(points, scored.getMatchScore());
    }

    @ParameterizedTest
    @CsvSource({"MUSCLE_BUILDING,HIGH_PROTEIN,15", "FAT_LOSS,LOW_CALORIE,15", "BLOOD_SUGAR_CARE,BLOOD_SUGAR_FRIENDLY,15",
            "MUSCLE_BUILDING,BALANCED,8", "FAT_LOSS,BALANCED,8", "BLOOD_SUGAR_CARE,BALANCED,8", "FAT_LOSS,HIGH_PROTEIN,0"})
    void healthGoalAlignmentIsVerifiedForEachGoal(HealthGoal goal, Recipe.HealthTag tag, double points) {
        Recipe recipe = recipe(40, tag, required());
        Recipe scored = new RecommendationService(List.of(recipe), catalog).getRecommendations(List.of(), new Preference("p", goal)).get(0);
        assertEquals(points, scored.getMatchScore());
    }
}
