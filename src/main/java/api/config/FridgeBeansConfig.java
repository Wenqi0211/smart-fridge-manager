package api.config;

import controller.CatalogController;
import controller.GroceryController;
import controller.InventoryController;
import controller.PreferenceController;
import controller.RecommendationController;
import loader.JsonFoodCatalogLoader;
import loader.JsonRecipeLoader;
import model.FoodCatalogEntry;
import model.Recipe;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import service.FoodCatalog;
import service.GroceryService;
import service.IFoodCatalog;
import service.IGroceryService;
import service.IPreferenceService;
import service.IRecommendationService;
import service.InventoryService;
import service.PreferenceService;
import service.RecommendationService;
import service.SessionReset;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring bean wiring for the official HTTP backend.
 * <p>
 * This configuration intentionally preserves the existing controller/service split used by the
 * legacy Java demo, so the Spring Boot API and the console flow share the same internal modules.
 */
@Configuration
public class FridgeBeansConfig {

    private static final String FOOD_CATALOG_RESOURCE = "/food_catalog.json";
    private static final String RECIPE_RESOURCE = "/recipes.json";

    @Bean
    public IFoodCatalog foodCatalog() {
        List<FoodCatalogEntry> catalogEntries = JsonFoodCatalogLoader.loadFromFileSafe(FOOD_CATALOG_RESOURCE);
        return new FoodCatalog(catalogEntries);
    }

    @Bean
    public CatalogController catalogController(IFoodCatalog foodCatalog) {
        return new CatalogController(foodCatalog);
    }

    @Bean
    @Scope(value = "authenticatedUser", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public InventoryService inventoryService(IFoodCatalog foodCatalog) {
        return new InventoryService(foodCatalog);
    }

    @Bean
    @Scope(value = "authenticatedUser", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public IPreferenceService preferenceService() {
        return new PreferenceService();
    }

    @Bean
    @Scope(value = "authenticatedUser", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public RecommendationService recommendationService(IFoodCatalog foodCatalog) {
        List<Recipe> recipes = JsonRecipeLoader.loadFromFileSafe(RECIPE_RESOURCE);
        return new RecommendationService(recipes, foodCatalog);
    }

    @Bean
    @Scope(value = "authenticatedUser", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public IGroceryService groceryService() {
        return new GroceryService(new ArrayList<>());
    }

    @Bean
    public InventoryController inventoryController(InventoryService inventoryService) {
        return new InventoryController(inventoryService);
    }

    @Bean
    public PreferenceController preferenceController(IPreferenceService preferenceService) {
        return new PreferenceController(preferenceService);
    }

    @Bean
    public RecommendationController recommendationController(IRecommendationService recommendationService) {
        return new RecommendationController(recommendationService);
    }

    @Bean
    public GroceryController groceryController(
            IGroceryService groceryService,
            IPreferenceService preferenceService,
            InventoryService inventoryService,
            IRecommendationService recommendationService) {
        return new GroceryController(
                groceryService,
                () -> SessionReset.clearAll(
                        preferenceService,
                        inventoryService,
                        recommendationService,
                        groceryService));
    }
}
