package api.dto;

import model.FoodItem;
import model.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Response DTO for the recipe detail page.
 *
 * In simple terms:
 * When the user opens a recipe, the frontend needs a complete view including:
 * - basic recipe information
 * - match score and percentage
 * - which ingredients are available
 * - which ingredients are missing
 * - inventory status for each ingredient
 *
 * This class aggregates and delivers all of that data.
 */
public class RecipeDetailResponse {
    private String id;
    private String title;
    private String category;
    private double matchScore;
    private int matchPercent;
    private double rating;
    private int cookTime;
    private int calories;
    private String description;
    private int urgentMatchedCount;
    private List<String> availableIngredients;
    private List<String> missingIngredients;
    private List<RecipeIngredientStatusResponse> requiredIngredients;
    private List<RecipeIngredientStatusResponse> optionalIngredients;

    /**
     * Builds a detail response from a Recipe and current inventory.
     *
     * This static factory method is the main entry point for assembling
     * all data required by the frontend detail page.
     */
    // ===== teammate note =====
    // This method transforms a Recipe into a frontend-ready detail response.
    // If the detail page requires additional fields, extend the mapping here
    // instead of constructing them in the controller.
    // insert your code here: extend response mapping carefully
    public static RecipeDetailResponse from(
            Recipe recipe,
            List<FoodItem> inventory,
            Function<String, String> canonicalNameResolver) {
        RecipeDetailResponse r = new RecipeDetailResponse();
        r.id = recipe.getId();
        r.title = recipe.getTitle();
        r.category = recipe.getRecipeCategory().getName();
        r.matchScore = recipe.getMatchScore();

        // Match percentage = matched required ingredients / total required ingredients.
        int totalRequired = (int) recipe.getRequiredIngredients().stream().filter(i -> !i.isOptional()).count();
        int matchedRequired = recipe.getAvailableIngredients().size();
        r.matchPercent = totalRequired == 0 ? 0 : (int) Math.round((double) matchedRequired * 100.0 / totalRequired);

        r.rating = recipe.getRating();
        r.cookTime = recipe.getCookTime();
        r.calories = recipe.getCalories();
        r.description = recipe.getDescription();
        r.urgentMatchedCount = recipe.getUrgentMatchedCount();

        // These lists are already computed in the Recipe model, so we create defensive copies for the response.
        r.availableIngredients = new ArrayList<>(recipe.getAvailableIngredients());
        r.missingIngredients = new ArrayList<>(recipe.getMissingIngredients());

        // Build required and optional ingredient rows separately, to provide clearer structure for frontend rendering.
        r.requiredIngredients = buildIngredientRows(recipe.getRequiredIngredients(), inventory, canonicalNameResolver);
        r.optionalIngredients = buildIngredientRows(recipe.getOptionalIngredients(), inventory, canonicalNameResolver);
        return r;
    }

    /**
     * Convert a list of ingredients into response rows with inventory status.
     *
     * This part is extracted from your teammate’s recipe-detail logic,
     * but adapted to fit the current model design.
     */
    private static List<RecipeIngredientStatusResponse> buildIngredientRows(
            List<Recipe.Ingredient> ingredients,
            List<FoodItem> inventory,
            Function<String, String> canonicalNameResolver) {
        List<RecipeIngredientStatusResponse> rows = new ArrayList<>();
        for (Recipe.Ingredient ingredient : ingredients) {
            RecipeIngredientStatusResponse row = new RecipeIngredientStatusResponse();
            row.setName(ingredient.getName());
            row.setQuantityText(ingredient.getQuantityText());
            row.setOptional(ingredient.isOptional());

            // Try to find a matching ingredient in the current inventory.
            FoodItem matched = findMatchingInventoryItem(ingredient.getName(), inventory, canonicalNameResolver);

            // Parse quantity text from recipe.
            // Used to determine: sufficient / insufficient / missing.
            QuantitySpec recipeQty = QuantitySpec.parse(ingredient.getQuantityText());
            row.setInFridge(matched != null);

            if (matched != null) {
                // If matched in inventory, set current stock info.
                row.setCurrentStockText(matched.getQuantity() + " " + matched.getUnit());
                row.setInventoryCategory(matched.getCategory().getName());

                QuantitySpec stockQty = QuantitySpec.of(matched.getQuantity(), matched.getUnit());

                // Only if units match and stock is less than required, mark as partially available
                if (isSameUnit(recipeQty.unit(), stockQty.unit()) && stockQty.amount() < recipeQty.amount()) {
                    row.setStatus("partially available");
                    row.setShortageText(formatAmount(recipeQty.amount() - stockQty.amount(), recipeQty.unit()));
                } else {
                    row.setStatus("from current fridge");
                }
            } else if (ingredient.isOptional()) {
                // Optional ingredient missing is acceptable.
                row.setStatus("optional");
                row.setShortageText(ingredient.getQuantityText());
            } else {
                // Required ingredient missing from inventory
                row.setStatus("need to buy");
                row.setShortageText(ingredient.getQuantityText());
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Find a matching food item in inventory.
     *
     * Instead of comparing raw strings,
     * we normalize names using canonical mapping first.
     */
    private static FoodItem findMatchingInventoryItem(
            String ingredientName,
            List<FoodItem> inventory,
            Function<String, String> canonicalNameResolver) {
        String target = normalize(canonicalNameResolver.apply(ingredientName));
        FoodItem earliestUsable = null;
        for (FoodItem item : inventory) {
            String inv = normalize(canonicalNameResolver.apply(item.getName()));
            if (target.equals(inv) && item.isAvailableForCooking()
                    && (earliestUsable == null || item.getExpiryDate().compareTo(earliestUsable.getExpiryDate()) < 0)) {
                earliestUsable = item;
            }
        }
        return earliestUsable;
    }

    /**
     * Check whether two units are considered equivalent.
     *
     * For example: count / counts / pc / pcs are normalized into "count".
     */
    private static boolean isSameUnit(String a, String b) {
        String left = canonicalUnit(a);
        String right = canonicalUnit(b);
        return !left.isEmpty() && left.equals(right);
    }

    /**
     * Normalize unit variations to avoid mismatches.
     */
    private static String canonicalUnit(String raw) {
        String u = normalize(raw);
        if (u.equals("count") || u.equals("counts") || u.equals("pc") || u.equals("pcs")) {
            return "count";
        }
        if (u.equals("clove") || u.equals("cloves")) {
            return "clove";
        }
        return u;
    }

    /**
     * Format quantity into a human-readable string.
     *
     * Examples:
     * - 2.0 -> 2
     * - 1.25 -> 1.25
     */
    private static String formatAmount(double amount, String unit) {
        double rounded = Math.round(amount * 100.0) / 100.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return ((int) Math.rint(rounded)) + " " + unit;
        }
        return rounded + " " + unit;
    }

    /**
     * Normalize text: trim + lowercase.
     */
    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Helper class to store quantity + unit for comparison.
     */
    private static final class QuantitySpec {
        private final double amount;
        private final String unit;

        private QuantitySpec(double amount, String unit) {
            this.amount = amount;
            this.unit = unit;
        }

        double amount() {
            return amount;
        }

        String unit() {
            return unit;
        }

        /**
         * Parse quantity text into numeric value and unit.
         * Default is 1 count if parsing fails.
         */
        static QuantitySpec parse(String quantityText) {
            if (quantityText == null || quantityText.isBlank()) {
                return new QuantitySpec(1, "count");
            }
            String[] parts = quantityText.trim().split("\\s+", 2);
            double amt = 1;
            try {
                amt = Double.parseDouble(parts[0]);
            } catch (NumberFormatException ignored) {
                // fallback to default value
            }
            String u = parts.length > 1 ? parts[1] : "count";
            return new QuantitySpec(amt, u);
        }

        /**
         * Create QuantitySpec from inventory data.
         */
        static QuantitySpec of(int amount, String unit) {
            return new QuantitySpec(amount, unit == null || unit.isBlank() ? "count" : unit);
        }
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public double getMatchScore() {
        return matchScore;
    }

    public int getMatchPercent() {
        return matchPercent;
    }

    public double getRating() {
        return rating;
    }

    public int getCookTime() {
        return cookTime;
    }

    public int getCalories() {
        return calories;
    }

    public String getDescription() {
        return description;
    }

    public int getUrgentMatchedCount() {
        return urgentMatchedCount;
    }

    public List<String> getAvailableIngredients() {
        return new ArrayList<>(availableIngredients);
    }

    public List<String> getMissingIngredients() {
        return new ArrayList<>(missingIngredients);
    }

    public List<RecipeIngredientStatusResponse> getRequiredIngredients() {
        return new ArrayList<>(requiredIngredients);
    }

    public List<RecipeIngredientStatusResponse> getOptionalIngredients() {
        return new ArrayList<>(optionalIngredients);
    }
}
