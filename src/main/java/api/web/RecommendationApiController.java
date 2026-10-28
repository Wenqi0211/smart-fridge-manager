package api.web;

import api.dto.RecipeDetailResponse;
import api.dto.RecipeToGroceryResponse;
import controller.CatalogController;
import controller.GroceryController;
import controller.InventoryController;
import controller.PreferenceController;
import controller.RecommendationController;
import model.FoodCatalogEntry;
import model.FoodCategory;
import model.FoodItem;
import model.GroceryItem;
import model.Preference;
import model.Recipe;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import service.GroceryQuantityLimitException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HTTP entry layer for recommendation, recipe-detail, and recipe-to-grocery endpoints.
 * <p>
 * Spring routing stays here. Cross-module actions delegate to the existing internal controller
 * layer so the HTTP API and the legacy Java demo share one backend call chain.
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationApiController {

    private final CatalogController catalogController;
    private final InventoryController inventoryController;
    private final PreferenceController preferenceController;
    private final RecommendationController recommendationController;
    private final GroceryController groceryController;

    public RecommendationApiController(
            CatalogController catalogController,
            InventoryController inventoryController,
            PreferenceController preferenceController,
            RecommendationController recommendationController,
            GroceryController groceryController) {
        this.catalogController = catalogController;
        this.inventoryController = inventoryController;
        this.preferenceController = preferenceController;
        this.recommendationController = recommendationController;
        this.groceryController = groceryController;
    }

    /**
     * 返回推荐列表。
     *
     * 大白话：
     * - 先根据“当前库存 + 当前偏好”重新算一遍推荐
     * - 然后如果前端传了 sort，就按指定方式排
     * - 如果传了 category，就再做一次分类过滤
     *
     * 支持的 query param：
     * - category：按菜谱分类过滤
     * - sort：score 或 cookTime
     */
    @GetMapping
    // ===== teammate note =====
    // 这里是推荐列表接口的主入口。
    // 后面如果有人要继续补“更多筛选条件 / 更多排序方式”，优先改这里。
    // insert your code here: add more query params or branch rules for recommendation list
    public List<Recipe> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sort", required = false) String sort) {
        // 每次请求都尽量基于“当前会话里的最新状态”来算推荐，
        // 这样前端改了库存或偏好后，这里能马上跟上。
        List<FoodItem> inventory = inventoryController.getVisibleItems();
        Preference preference = preferenceController.getPreference();
        List<Recipe> base = recommendationController.getRecommendations(inventory, preference);

        // sort 不传就保持当前推荐顺序。
        // 传 cookTime 就按做饭时间排序；其他值默认按 score。
        if (sort != null && !sort.isBlank()) {
            if ("cookTime".equalsIgnoreCase(sort)) {
                base = recommendationController.sortByCookTime();
            } else {
                base = recommendationController.sortByMatchScore();
            }
        }

        // category 不传就直接返回整包推荐。
        if (category == null || category.isBlank()) {
            return base;
        }

        // 这里做的是“包含匹配”，不是必须完全相等。
        String needle = category.trim().toLowerCase(Locale.ROOT);
        return base.stream()
                .filter(r -> r.getRecipeCategory().getName().toLowerCase(Locale.ROOT).contains(needle))
                .collect(Collectors.toList());
    }

    /**
     * 返回某一道菜的详情。
     *
     * 大白话：
     * 这个接口就是给 recipe detail page 用的。
     * 它会把菜谱本身信息 + ingredient status 一起打包返回。
     */
    @GetMapping("/{id}")
    // ===== teammate note =====
    // 这里是 recipe detail page 的后端入口。
    // 后面如果前端还想多拿字段，比如 nutrition、badge、替代食材等，优先从这里往下接。
    // insert your code here: extend detail response fields without breaking current API shape
    public ResponseEntity<RecipeDetailResponse> detail(@PathVariable String id) {
        List<FoodItem> inventory = inventoryController.getVisibleItems();
        Preference preference = preferenceController.getPreference();
        Recipe recipe = recommendationController.getRecommendationById(inventory, preference, id);
        if (recipe == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }

        // 真正把 recipe 组装成 detail response 的重活，
        // 放在 DTO 的 from(...) 里做，避免 controller 太臃肿。
        return ResponseEntity.ok(RecipeDetailResponse.from(recipe, inventory, catalogController::canonicalFoodName));
    }

    /**
     * 把某道菜里“当前缺失的必需食材”加入 grocery list。
     *
     * 这就是你 PRD 里很重要的那条闭环：
     * recommendation -> recipe detail -> grocery planning
     *
     * 这里做了两件比较实用的事：
     * 1. 尝试从 quantityText 里解析出数量
     * 2. 如果 grocery 里已经有同名食材，不重复插新行，而是合并数量
     */
    @PostMapping("/{id}/grocery")
    // ===== teammate note =====
    // 这是 recommendation -> grocery 的关键闭环。
    // 如果组员后面要做“按真实缺口数量加入”“保留 recipe 来源”“支持 unit”，优先从这个方法继续补。
    // insert your code here: improve recipe-to-grocery merge rules, quantity parsing, and unit handling
    public ResponseEntity<RecipeToGroceryResponse> addMissingIngredientsToGrocery(@PathVariable String id) {
        List<FoodItem> inventory = inventoryController.getVisibleItems();
        Preference preference = preferenceController.getPreference();
        Recipe recipe = recommendationController.getRecommendationById(inventory, preference, id);
        if (recipe == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }

        // 这个 response 主要是给前端一个交代：
        // 到底新加了几条，哪些是合并进旧条目的。
        RecipeToGroceryResponse response = new RecipeToGroceryResponse();
        response.setRecipeId(recipe.getId());
        response.setRecipeTitle(recipe.getTitle());

        List<GroceryItem> added = new ArrayList<>();
        List<String> merged = new ArrayList<>();

        // Preflight the complete plan before mutating a row. This keeps an overflow on a
        // later ingredient from leaving an earlier ingredient added or incremented.
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Long> quantities = new LinkedHashMap<>();
        for (Recipe.Ingredient ingredient : recipe.getRequiredIngredients()) {
            if (ingredient.isOptional() || !isMissingRequiredIngredient(recipe, ingredient)) {
                continue;
            }
            String canonical = catalogController.canonicalFoodName(ingredient.getName());
            String key = norm(canonical);
            names.putIfAbsent(key, canonical);
            long needed = quantities.getOrDefault(key, 0L)
                    + parseQuantityAsPositiveInt(ingredient.getQuantityText());
            if (needed > Integer.MAX_VALUE) {
                throw new GroceryQuantityLimitException();
            }
            quantities.put(key, needed);
        }
        for (Map.Entry<String, Long> plan : quantities.entrySet()) {
            GroceryItem existing = findExistingGroceryItem(names.get(plan.getKey()));
            if (existing != null && (long) existing.getQuantity() + plan.getValue() > Integer.MAX_VALUE) {
                throw new GroceryQuantityLimitException();
            }
        }

        for (Map.Entry<String, Long> plan : quantities.entrySet()) {
            String canonical = names.get(plan.getKey());
            int neededQty = plan.getValue().intValue();
            GroceryItem existing = findExistingGroceryItem(canonical);
            if (existing != null) {
                groceryController.updateQuantity(existing.getId(), neededQty);
                merged.add(existing.getName());
                continue;
            }

            // grocery item 还是需要 category，
            // 所以尽量从 catalog 里解析；找不到就先扔 Misc。
            FoodCatalogEntry entry = resolveEntry(canonical);
            FoodCategory category = entry == null
                    ? new FoodCategory("misc", "Misc", "box")
                    : entry.getCategory();

            GroceryItem line = new GroceryItem(
                    UUID.randomUUID().toString(),
                    canonical,
                    category,
                    neededQty,
                    0.0,
                    false);
            groceryController.addLine(line);
            added.add(line);
        }

        response.setAddedItems(added);
        response.setAddedCount(added.size());
        response.setMergedItemNames(merged);
        response.setMergedCount(merged.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 判断某个 required ingredient 是否真的属于“缺失项”。
     *
     * 注意这里不是看 ingredient 名字原文，
     * 而是先走 canonicalFoodName 再比较，尽量减少别名误差。
     */
    // ===== teammate note =====
    // 这个方法专门负责判断“这项到底算不算缺”。
    // 如果以后 missingIngredients 的算法改了，或者要区分 optional / required / substitute，先看这里。
    // insert your code here: refine missing-ingredient rule if PRD changes
    private boolean isMissingRequiredIngredient(Recipe recipe, Recipe.Ingredient ingredient) {
        String target = norm(catalogController.canonicalFoodName(ingredient.getName()));
        return recipe.getMissingIngredients().stream()
                .map(catalogController::canonicalFoodName)
                .map(RecommendationApiController::norm)
                .anyMatch(target::equals);
    }

    /**
     * 在当前 grocery list 里找有没有“同一种东西”。
     *
     * 这里也是统一名字后再比，
     * 否则 milk / whole milk / dairy milk 之类很容易重复。
     */
    private GroceryItem findExistingGroceryItem(String canonicalName) {
        String key = norm(canonicalName);
        return groceryController.getItems().stream()
                .filter(item -> norm(catalogController.canonicalFoodName(item.getName())).equals(key))
                .findFirst()
                .orElse(null);
    }

    /**
     * 把 recipe 里的数量文本尽量读成正整数。
     *
     * 例子：
     * - "2 count" -> 2
     * - "1.5 cup" -> 2（向上取整）
     * - 读不出来 -> 1
     */
    // ===== teammate note =====
    // 现在这里只做了一个很保守的数量解析。
    // 像 0.5、1-2、2 cups 这种复杂写法，如果后面要更准，就继续扩这里。
    // insert your code here: support richer quantity text parsing
    private int parseQuantityAsPositiveInt(String quantityText) {
        if (quantityText == null || quantityText.isBlank()) {
            return 1;
        }
        String[] parts = quantityText.trim().split("\\s+", 2);
        try {
            double parsed = Double.parseDouble(parts[0]);
            return Math.max(1, (int) Math.ceil(parsed));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    /**
     * 尽量把食材名解析成 catalog entry。
     *
     * 顺序是：
     * 1. 直接 resolve
     * 2. resolve 不到就用 suggestion 顶一个
     */
    private FoodCatalogEntry resolveEntry(String foodName) {
        Optional<FoodCatalogEntry> direct = catalogController.resolveEntry(foodName);
        if (direct.isPresent()) {
            return direct.get();
        }
        return catalogController.searchSuggestions(foodName).stream().findFirst().orElse(null);
    }

    /**
     * 做统一化比较用的小工具：去首尾空格 + 转小写。
     */
    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
