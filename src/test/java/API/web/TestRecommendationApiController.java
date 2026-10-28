package API.web;


import api.web.RecommendationApiController;
import controller.CatalogController;
import controller.GroceryController;
import controller.InventoryController;
import controller.PreferenceController;
import controller.RecommendationController;
import model.FoodCatalogEntry;
import model.FoodCategory;
import model.GroceryItem;
import model.HealthGoal;
import model.Recipe;
import model.RecipeCategory;
import service.FoodCatalog;
import service.GroceryService;
import service.InventoryService;
import service.PreferenceService;
import service.RecommendationService;

import api.dto.RecipeDetailResponse;
import api.dto.RecipeToGroceryResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RecommendationApiController.
 * Directly instantiates all controllers without starting a Spring server.
 */
public class TestRecommendationApiController {

    private RecommendationApiController recommendationApiController;
    private GroceryService groceryService;
    private PreferenceService preferenceService;
    private Recipe recipeWithMissingEgg;
    private Recipe recipeWithNoMissing;
    private FoodCategory foodCat;
    private RecipeCategory recipeCat;

    @BeforeEach
    void setUp() {
        foodCat   = new FoodCategory("f1", "Protein", "egg");
        recipeCat = new RecipeCategory("r1", "Quick", "bolt");

        // Recipe where Egg is missing
        recipeWithMissingEgg = Recipe.loaded(
                        "recipe-1", "Egg Bowl", recipeCat,
                        List.of(Recipe.HealthTag.HIGH_PROTEIN),
                        List.of(new Recipe.Ingredient("Egg", "2 pcs", false)),
                        List.of(),
                        4.5, 15, 300, "Simple egg dish")
                .withComputed(0.8, List.of(), List.of("Egg"), 0);

        // Recipe where all ingredients are available
        recipeWithNoMissing = Recipe.loaded(
                        "recipe-2", "Milk Soup", recipeCat,
                        List.of(Recipe.HealthTag.BALANCED),
                        List.of(new Recipe.Ingredient("Milk", "1 cup", false)),
                        List.of(),
                        4.1, 20, 200, "Simple milk soup")
                .withComputed(1.0, List.of("Milk"), List.of(), 0);

        FoodCatalog foodCatalog = new FoodCatalog(List.of(
                new FoodCatalogEntry("Egg",  7, foodCat),
                new FoodCatalogEntry("Milk", 7, foodCat)));

        InventoryService inventoryService = new InventoryService(foodCatalog);
        preferenceService = new PreferenceService();
        groceryService    = new GroceryService(List.of());

        CatalogController        catalogController        = new CatalogController(foodCatalog);
        InventoryController      inventoryController      = new InventoryController(inventoryService);
        PreferenceController     preferenceController     = new PreferenceController(preferenceService);
        RecommendationController recommendationController = new RecommendationController(
                new RecommendationService(List.of(recipeWithMissingEgg, recipeWithNoMissing), foodCatalog));
        GroceryController        groceryController        = new GroceryController(groceryService);

        recommendationApiController = new RecommendationApiController(
                catalogController, inventoryController, preferenceController,
                recommendationController, groceryController);
    }

    // GET /api/recommendations

    /**
     * Tests list() with no filters returns all recipes.
     * Branch: sort == null, category == null → return base list.
     */
    @Test
    void testListWithNoFiltersReturnsAllRecipes() {
        List<Recipe> result = recommendationApiController.list(null, null);
        assertEquals(2, result.size());
    }

    /**
     * Tests list() with sort=cookTime returns recipes sorted by cook time.
     * Branch: sort == "cookTime" → sortByCookTime().
     */
    @Test
    void testListWithSortCookTimeReturnsSortedList() {
        List<Recipe> result = recommendationApiController.list(null, "cookTime");
        assertEquals(2, result.size());
        assertTrue(result.get(0).getCookTime() <= result.get(1).getCookTime());
    }

    /**
     * Tests list() with sort=score returns recipes sorted by match score.
     * Branch: sort is not blank and not "cookTime" → sortByMatchScore().
     */
    @Test
    void testListWithSortScoreReturnsSortedByMatchScore() {
        List<Recipe> result = recommendationApiController.list(null, "score");
        assertEquals(2, result.size());
    }

    /**
     * Tests list() with category filter returns matching recipes.
     * Branch: category is not blank → apply contains filter.
     */
    @Test
    void testListWithCategoryFilterReturnsMatchingRecipes() {
        List<Recipe> result = recommendationApiController.list("quick", null);
        assertEquals(2, result.size());
    }

    /**
     * Tests list() with non-matching category returns empty list.
     */
    @Test
    void testListWithNonMatchingCategoryReturnsEmptyList() {
        List<Recipe> result = recommendationApiController.list("nonexistent", null);
        assertEquals(0, result.size());
    }

    /**
     * Tests list() with both sort and category applied together.
     */
    @Test
    void testListWithSortAndCategoryAppliedTogether() {
        List<Recipe> result = recommendationApiController.list("quick", "cookTime");
        assertEquals(2, result.size());
    }

    /**
     * Tests list() with preference set uses preference in scoring.
     */
    @Test
    void testListWithPreferenceSetReturnsRecipes() {
        preferenceService.savePreference(HealthGoal.FAT_LOSS);
        List<Recipe> result = recommendationApiController.list(null, null);
        assertFalse(result.isEmpty());
    }

    //  GET /api/recommendations/{id}

    /**
     * Tests detail() with existing id returns 200 OK with RecipeDetailResponse.
     * Branch: recipe found → return ok.
     */
    @Test
    void testDetailWithExistingIdReturnsOk() {
        ResponseEntity<RecipeDetailResponse> response =
                recommendationApiController.detail("recipe-1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Egg Bowl", response.getBody().getTitle());
    }

    /**
     * Tests detail() with non-existing id returns 404 Not Found.
     * Branch: recipe == null → return notFound.
     */
    @Test
    void testDetailWithNonExistingIdReturnsNotFound() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> recommendationApiController.detail("nonexistent"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    //  POST /api/recommendations/{id}/grocery

    /**
     * Tests addMissingIngredientsToGrocery() with non-existing id returns 404.
     * Branch: recipe == null → return notFound.
     */
    @Test
    void testAddMissingIngredientsWithNonExistingIdReturnsNotFound() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> recommendationApiController.addMissingIngredientsToGrocery("nonexistent"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    /**
     * Tests addMissingIngredientsToGrocery() adds missing ingredients to grocery list.
     * Branch: ingredient is missing → create new GroceryItem → add to grocery.
     */
    @Test
    void testAddMissingIngredientsAddsNewItemToGrocery() {
        ResponseEntity<RecipeToGroceryResponse> response =
                recommendationApiController.addMissingIngredientsToGrocery("recipe-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getAddedCount());
        assertEquals(0, response.getBody().getMergedCount());
        assertEquals(1, groceryService.getItems().size());
    }



    /**
     * Tests addMissingIngredientsToGrocery() merges quantity when item already exists in grocery.
     * Branch: existing grocery item found → updateQuantity → add to merged list.
     */
    @Test
    void testAddMissingIngredientsMergesWhenItemAlreadyInGrocery() {
        // Pre-add Egg to grocery
        FoodCategory cat = new FoodCategory("f1", "Protein", "egg");
        groceryService.addLine(new GroceryItem("g1", "Egg", cat, 1, 0.0, false));

        ResponseEntity<RecipeToGroceryResponse> response =
                recommendationApiController.addMissingIngredientsToGrocery("recipe-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().getAddedCount());
        assertEquals(1, response.getBody().getMergedCount());
        assertTrue(response.getBody().getMergedItemNames().contains("Egg"));
    }

    /**
     * Tests addMissingIngredientsToGrocery() returns correct recipeId and title.
     */
    @Test
    void testAddMissingIngredientsResponseContainsRecipeMetadata() {
        ResponseEntity<RecipeToGroceryResponse> response =
                recommendationApiController.addMissingIngredientsToGrocery("recipe-1");

        assertEquals("recipe-1",  response.getBody().getRecipeId());
        assertEquals("Egg Bowl",  response.getBody().getRecipeTitle());
    }
    @Test
    void optionalAliasIsNotAddedWhenRequiredIngredientHasSameCanonicalName() {
        RecommendationApiController planner = planner(List.of(
                new Recipe.Ingredient("Egg", "2 count", false),
                new Recipe.Ingredient("eggs", "5 count", true)));
        planner.addMissingIngredientsToGrocery("plan");
        assertEquals(1, groceryService.getItems().size());
        assertEquals(2, groceryService.getItems().get(0).getQuantity());
    }

    @Test
    void requiredAliasesAggregateIntoOneCanonicalGroceryLine() {
        RecommendationApiController planner = planner(List.of(
                new Recipe.Ingredient("Egg", "2 count", false),
                new Recipe.Ingredient("eggs", "3 count", false)));
        RecipeToGroceryResponse response = planner.addMissingIngredientsToGrocery("plan").getBody();
        assertEquals(1, response.getAddedCount());
        assertEquals(0, response.getMergedCount());
        assertEquals(1, groceryService.getItems().size());
        assertEquals(5, groceryService.getItems().get(0).getQuantity());
    }

    @Test
    void combinedAliasQuantitiesOverflowBeforeAnyNewLineIsAdded() {
        RecommendationApiController planner = planner(List.of(
                new Recipe.Ingredient("Egg", "2147483647 count", false),
                new Recipe.Ingredient("eggs", "1 count", false)));
        assertThrows(service.GroceryQuantityLimitException.class,
                () -> planner.addMissingIngredientsToGrocery("plan"));
        assertEquals(0, groceryService.getItems().size());
    }

    private RecommendationApiController planner(List<Recipe.Ingredient> ingredients) {
        FoodCatalog catalog = new FoodCatalog(List.of(
                new FoodCatalogEntry("egg", "Egg", List.of("eggs"), 7, foodCat)));
        Recipe recipe = Recipe.loaded("plan", "Egg plan", recipeCat,
                List.of(Recipe.HealthTag.BALANCED), ingredients, List.of(), 4, 10, 100, "Plan");
        return new RecommendationApiController(new CatalogController(catalog),
                new InventoryController(new InventoryService(catalog)),
                new PreferenceController(preferenceService),
                new RecommendationController(new RecommendationService(List.of(recipe), catalog)),
                new GroceryController(groceryService));
    }
}