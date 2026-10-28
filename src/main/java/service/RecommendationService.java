package service;

import model.FoodItem;
import model.HealthGoal;
import model.Preference;
import model.Recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * PRD scoring for recipes: coverage from required ingredients only, preference tag alignment,
 * urgent-ingredient bonus, convenience by cook time; caches sorted results per session.
 * <p>
 * PRD 菜谱打分：必选食材覆盖度、偏好标签对齐、临期食材加成、烹饪便利度；本会话内缓存排序结果。
 */
public class RecommendationService implements IRecommendationService {
    /** Immutable recipe templates from JSON. / 来自 JSON 的不可变菜谱模板。 */
    private final List<Recipe> recipeTemplates;
    /** Catalog for canonical ingredient matching. / 用于食材规范名匹配的目录。 */
    private final IFoodCatalog foodCatalog;
    /** Last scored list returned from {@link #getRecommendations}. / 上次 {@link #getRecommendations} 的打分结果。 */
    private List<Recipe> currentRecommendations;

    /**
     * @param recipeTemplates loaded recipes / 已加载菜谱模板
     * @param foodCatalog     catalog for name resolution / 用于名称解析的目录
     */
    public RecommendationService(List<Recipe> recipeTemplates, IFoodCatalog foodCatalog) {
        this.recipeTemplates = new ArrayList<>(recipeTemplates);
        this.foodCatalog = foodCatalog;
        this.currentRecommendations = new ArrayList<>();
    }

    /**
     * Normalizes a string for comparison (null-safe trim + lower ROOT).
     * <p>
     * 规范化字符串以便比较（null 安全 trim + ROOT 小写）。
     */
    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Finds inventory item whose canonical name matches the ingredient after catalog normalization.
     * <p>
     * 在目录规范化后，查找与配料名规范形式一致的库存项。
     */
    private FoodItem findMatchingInventoryItem(String ingredientName, List<FoodItem> inventory) {
        String ca = norm(foodCatalog.canonicalFoodName(ingredientName));
        FoodItem earliestUsable = null;
        for (FoodItem item : inventory) {
            String cb = norm(foodCatalog.canonicalFoodName(item.getName()));
            if (ca.equals(cb) && item.isAvailableForCooking()
                    && (earliestUsable == null || item.getExpiryDate().compareTo(earliestUsable.getExpiryDate()) < 0)) {
                earliestUsable = item;
            }
        }
        return earliestUsable;
    }

    /**
     * PRD convenience points by cook time buckets (≤15, ≤30, else 0).
     * <p>
     * 按烹饪时间档位的便利分（≤15、≤30、否则 0）。
     */
    private static int convenienceScore(int cookTime) {
        if (cookTime <= 15) {
            return 5;
        }
        if (cookTime <= 30) {
            return 3;
        }
        return 0;
    }

    /**
     * Points from aligning user {@link HealthGoal} with recipe {@link Recipe.HealthTag}s.
     * <p>
     * 用户 {@link HealthGoal} 与菜谱 {@link Recipe.HealthTag} 对齐得分。
     */
    private static int preferenceAlignment(Preference preference, List<Recipe.HealthTag> tags) {
        if (preference == null) {
            return 0;
        }
        HealthGoal g = preference.getHealthGoal();
        boolean balanced = tags.contains(Recipe.HealthTag.BALANCED);
        int best = 0;
        switch (g) {
            case MUSCLE_BUILDING:
                if (tags.contains(Recipe.HealthTag.HIGH_PROTEIN)) {
                    best = 15;
                } else if (balanced) {
                    best = 8;
                }
                break;
            case FAT_LOSS:
                if (tags.contains(Recipe.HealthTag.LOW_CALORIE)) {
                    best = 15;
                } else if (balanced) {
                    best = 8;
                }
                break;
            case BLOOD_SUGAR_CARE:
                if (tags.contains(Recipe.HealthTag.BLOOD_SUGAR_FRIENDLY)) {
                    best = 15;
                } else if (balanced) {
                    best = 8;
                }
                break;
            default:
                break;
        }
        return best;
    }

    /**
     * Bonus when required ingredients are matched by urgent inventory items.
     * <p>
     * 当必选食材由「临期」库存项匹配时的加分。
     */
    private static int urgentUsageScore(int urgentMatchedRequiredCount) {
        if (urgentMatchedRequiredCount <= 0) {
            return 0;
        }
        if (urgentMatchedRequiredCount == 1) {
            return 10;
        }
        return 20;
    }

    /**
     * Computes match score and availability lists for one template recipe.
     * <p>
     * 为单条模板菜谱计算匹配分与可用/缺失列表。
     */
    private Recipe scoreRecipe(Recipe template, List<FoodItem> inventory, Preference preference) {
        List<Recipe.Ingredient> required = template.getRequiredIngredients().stream()
                .filter(ri -> !ri.isOptional())
                .collect(Collectors.toList());

        int totalRequired = required.size();
        int matched = 0;
        int urgentMatched = 0;
        List<String> available = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (Recipe.Ingredient ri : required) {
            FoodItem hit = findMatchingInventoryItem(ri.getName(), inventory);
            if (hit != null) {
                matched++;
                if (hit.isUrgent()) {
                    urgentMatched++;
                }
                available.add(ri.getName());
            } else {
                missing.add(ri.getName());
            }
        }

        double coverage = 0.0;
        if (totalRequired > 0) {
            coverage = 60.0 * matched / totalRequired;
        }

        double score = coverage
                + urgentUsageScore(urgentMatched)
                + preferenceAlignment(preference, template.getHealthTags())
                + convenienceScore(template.getCookTime());

        if (score > 100.0) {
            score = 100.0;
        }

        return template.withComputed(score, available, missing, urgentMatched);
    }

    /**
     * Comparator for PRD tie-break after primary match score (Java 8-safe nested reversed keys).
     * <p>
     * PRD 在匹配分之后的决胜比较器（Java 8 安全嵌套 reversed）。
     */
    private static Comparator<Recipe> tieBreak() {
        return Comparator.comparingDouble(Recipe::getMatchScore).reversed()
                .thenComparingInt(r -> r.getMissingIngredients().size())
                .thenComparing(Comparator.comparingInt(Recipe::getUrgentMatchedCount).reversed())
                .thenComparingInt(Recipe::getCookTime)
                .thenComparing(Comparator.comparingDouble(Recipe::getRating).reversed())
                .thenComparing(Recipe::getTitle, String.CASE_INSENSITIVE_ORDER);
    }

    /** {@inheritDoc} */
    @Override
    public List<Recipe> getRecommendations(List<FoodItem> inventory, Preference preference) {
        List<Recipe> scored = new ArrayList<>();
        for (Recipe tmpl : recipeTemplates) {
            scored.add(scoreRecipe(tmpl, inventory == null ? List.of() : inventory, preference));
        }
        scored.sort(tieBreak());
        currentRecommendations = new ArrayList<>(scored);
        return new ArrayList<>(currentRecommendations);
    }

    /** {@inheritDoc} */
    @Override
    public List<Recipe> filterByRecipeCategory(String categoryName) {
        String needle = categoryName == null ? "" : categoryName.trim().toLowerCase(Locale.ROOT);
        return currentRecommendations.stream()
                .filter(r -> r.getRecipeCategory().getName().toLowerCase(Locale.ROOT).contains(needle))
                .collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public List<Recipe> sortByMatchScore() {
        return currentRecommendations.stream()
                .sorted(tieBreak())
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>
     * 主键为烹饪时间升序；次级键使用单字段 {@code reversed()}，避免对整个复合比较器 {@code reversed()} 误伤主键。
     */
    @Override
    public List<Recipe> sortByCookTime() {
        return currentRecommendations.stream()
                .sorted(Comparator.comparingInt(Recipe::getCookTime)
                        .thenComparing(Comparator.comparingDouble(Recipe::getMatchScore).reversed())
                        .thenComparingInt(r -> r.getMissingIngredients().size())
                        .thenComparing(Comparator.comparingInt(Recipe::getUrgentMatchedCount).reversed())
                        .thenComparing(Comparator.comparingDouble(Recipe::getRating).reversed())
                        .thenComparing(Recipe::getTitle, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public void clearRecommendations() {
        currentRecommendations = new ArrayList<>();
    }
}
