package API.dto;

import api.dto.RecipeDetailResponse;
import api.dto.RecipeIngredientStatusResponse;
import model.FoodCategory;
import model.FoodItem;
import model.Recipe;
import model.RecipeCategory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RecipeDetailResponse.from().
 *
 * Strategy:
 * - Use Recipe.loaded() for basic template (10 params, available/missing empty by default)
 * - Use recipe.withComputed() to set available and missing ingredient lists
 * - Use s -> s as canonicalNameResolver so names pass through unchanged
 * - Each test targets one specific business rule or branch
 */
public class TestRecipeDetailResponse {

    private RecipeCategory recipeCategory;
    private FoodCategory foodCategory;

    @BeforeEach
    void setUp() {
        recipeCategory = new RecipeCategory("r1", "Quick", "bolt");
        foodCategory = new FoodCategory("f1", "Protein", "egg");
    }

    // Helpers
    /**
     * Builds a Recipe with required ingredients and computed available/missing lists.
     * Uses withComputed() because Recipe.loaded() always starts with empty lists.
     */
    private Recipe buildRecipe(
            List<Recipe.Ingredient> required,
            List<String> available,
            List<String> missing) {
        Recipe base = Recipe.loaded(
                "r1", "Egg Bowl", recipeCategory,
                List.of(Recipe.HealthTag.HIGH_PROTEIN),
                required,
                List.of(),
                4.5, 15, 300, "Simple egg dish");
        return base.withComputed(0.0, available, missing, 0);
    }

    /**
     * Creates a FoodItem for inventory use.
     */
    private FoodItem makeFoodItem(String name, int quantity, String unit) {
        return new FoodItem(
                "i1", name, foodCategory, quantity, unit,
                LocalDate.now().toString(), LocalDate.now().plusDays(7).toString());
    }

    // Basic field mapping

    /**
     * Tests that basic fields are mapped correctly from the Recipe.
     * Covers: id, title, category, rating, cookTime, calories, description.
     */
    @Test
    void testBasicFieldsAreMappedCorrectly() {
        Recipe recipe = buildRecipe(List.of(), List.of(), List.of());
        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(), s -> s);

        assertEquals("r1",               response.getId());
        assertEquals("Egg Bowl",          response.getTitle());
        assertEquals("Quick",             response.getCategory());
        assertEquals(4.5,                 response.getRating(), 0.0001);
        assertEquals(15,                  response.getCookTime());
        assertEquals(300,                 response.getCalories());
        assertEquals("Simple egg dish",   response.getDescription());
    }

    // matchPercent

    /**
     * Tests matchPercent is 0 when there are no required ingredients.
     * Guards against division-by-zero: totalRequired == 0 → return 0.
     */
    @Test
    void testMatchPercentIsZeroWhenNoRequiredIngredients() {
        Recipe recipe = buildRecipe(List.of(), List.of(), List.of());
        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(), s -> s);
        assertEquals(0, response.getMatchPercent());
    }

    /**
     * Tests matchPercent is 100 when all required ingredients are available.
     * 1 required, 1 available → 100%.
     */
    @Test
    void testMatchPercentIsOneHundredWhenAllIngredientsAvailable() {
        Recipe recipe = buildRecipe(
                List.of(new Recipe.Ingredient("Egg", "2 pcs", false)),
                List.of("Egg"),
                List.of());
        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(), s -> s);
        assertEquals(100, response.getMatchPercent());
    }

    /**
     * Tests matchPercent is 50 when half of required ingredients are available.
     * 2 required, 1 available → 50%.
     */
    @Test
    void testMatchPercentIsFiftyWhenHalfIngredientsAvailable() {
        Recipe recipe = buildRecipe(
                List.of(
                        new Recipe.Ingredient("Egg",  "2 pcs", false),
                        new Recipe.Ingredient("Milk", "1 cup", false)),
                List.of("Egg"),
                List.of("Milk"));
        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(), s -> s);
        assertEquals(50, response.getMatchPercent());
    }

    //  Ingredient status: from current fridge

    /**
     * Tests that a required ingredient found in the fridge with sufficient stock
     * gets status "from current fridge".
     * Inventory has 5 pcs, recipe needs 2 pcs → enough.
     */
    @Test
    void testIngredientStatusIsFromFridgeWhenSufficientStock() {
        Recipe recipe = buildRecipe(
                List.of(new Recipe.Ingredient("Egg", "2 pcs", false)),
                List.of(), List.of());
        FoodItem egg = makeFoodItem("Egg", 5, "pcs");

        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(egg), s -> s);

        RecipeIngredientStatusResponse row = response.getRequiredIngredients().get(0);
        assertEquals("from current fridge", row.getStatus());
        assertTrue(row.isInFridge());
    }

    //  Ingredient status: partially available

    /**
     * Tests that a required ingredient found in the fridge with insufficient stock
     * gets status "partially available".
     * Inventory has 2 pcs, recipe needs 5 pcs → not enough.
     */
    @Test
    void testIngredientStatusIsPartiallyAvailableWhenInsufficientStock() {
        Recipe recipe = buildRecipe(
                List.of(new Recipe.Ingredient("Egg", "5 pcs", false)),
                List.of(), List.of());
        FoodItem egg = makeFoodItem("Egg", 2, "pcs");

        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(egg), s -> s);

        RecipeIngredientStatusResponse row = response.getRequiredIngredients().get(0);
        assertEquals("partially available", row.getStatus());
        assertTrue(row.isInFridge());
        assertNotNull(row.getShortageText());
    }

    //  Ingredient status: need to buy

    /**
     * Tests that a required ingredient not found in the fridge
     * gets status "need to buy".
     * Inventory is empty.
     */
    @Test
    void testIngredientStatusIsNeedToBuyWhenNotInFridge() {
        Recipe recipe = buildRecipe(
                List.of(new Recipe.Ingredient("Egg", "2 pcs", false)),
                List.of(), List.of());

        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(), s -> s);

        RecipeIngredientStatusResponse row = response.getRequiredIngredients().get(0);
        assertEquals("need to buy", row.getStatus());
        assertFalse(row.isInFridge());
        assertEquals("2 pcs", row.getShortageText());
    }

    // Ingredient status: optional

    /**
     * Tests that an optional ingredient not found in the fridge
     * gets status "optional", not "need to buy".
     */
    @Test
    void testOptionalIngredientStatusIsOptionalWhenNotInFridge() {
        Recipe recipe = Recipe.loaded(
                "r1", "Egg Bowl", recipeCategory,
                List.of(Recipe.HealthTag.HIGH_PROTEIN),
                List.of(),
                List.of(new Recipe.Ingredient("Cheese", "1 pcs", true)),
                4.5, 15, 300, "desc");

        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(), s -> s);

        RecipeIngredientStatusResponse row = response.getOptionalIngredients().get(0);
        assertEquals("optional", row.getStatus());
        assertFalse(row.isInFridge());
    }

    // Available / missing lists

    /**
     * Tests that availableIngredients and missingIngredients are
     * copied correctly from the Recipe into the response.
     */
    @Test
    void testAvailableAndMissingListsAreCopiedFromRecipe() {
        Recipe recipe = buildRecipe(
                List.of(
                        new Recipe.Ingredient("Egg",  "2 pcs", false),
                        new Recipe.Ingredient("Milk", "1 cup", false)),
                List.of("Egg"),
                List.of("Milk"));

        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(), s -> s);

        assertEquals(List.of("Egg"),  response.getAvailableIngredients());
        assertEquals(List.of("Milk"), response.getMissingIngredients());
    }

    // Multiple ingredients mixed

    /**
     * Tests that multiple required ingredients each get the correct status
     * when inventory partially covers them.
     * Egg: in fridge (sufficient) → "from current fridge"
     * Milk: not in fridge → "need to buy"
     */
    @Test
    void testMixedIngredientStatusesWithPartialInventory() {
        Recipe recipe = buildRecipe(
                List.of(
                        new Recipe.Ingredient("Egg",  "2 pcs", false),
                        new Recipe.Ingredient("Milk", "1 cup", false)),
                List.of(), List.of());
        FoodItem egg = makeFoodItem("Egg", 5, "pcs");

        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(egg), s -> s);

        List<RecipeIngredientStatusResponse> rows = response.getRequiredIngredients();
        assertEquals("from current fridge", rows.get(0).getStatus());
        assertEquals("need to buy",         rows.get(1).getStatus());
    }

    @Test
    void testIngredientStatusIsFromFridgeWhenUnitsAreDifferent() {
        Recipe recipe = buildRecipe(
                List.of(new Recipe.Ingredient("Egg", "5 cups", false)),
                List.of(), List.of());
        FoodItem egg = makeFoodItem("Egg", 2, "pcs"); // 单位不同: cups vs pcs

        RecipeDetailResponse response = RecipeDetailResponse.from(
                recipe, List.of(egg), s -> s);

        RecipeIngredientStatusResponse row = response.getRequiredIngredients().get(0);
        assertEquals("from current fridge", row.getStatus());
    }
}
