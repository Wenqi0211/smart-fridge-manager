package api.web;

import api.dto.GroceryAddRequest;
import controller.CatalogController;
import controller.GroceryController;
import model.FoodCatalogEntry;
import model.FoodCategory;
import model.GroceryItem;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HTTP entry layer for grocery endpoints used by the Flutter frontend.
 * <p>
 * Spring routing and request/response handling stay here. Grocery state changes delegate to
 * {@link GroceryController}, and catalog-backed name resolution delegates to {@link CatalogController}.
 */
@RestController
@RequestMapping("/api/grocery")
public class GroceryApiController {

    private final CatalogController catalogController;
    private final GroceryController groceryController;

    public GroceryApiController(CatalogController catalogController, GroceryController groceryController) {
        this.catalogController = catalogController;
        this.groceryController = groceryController;
    }

    /**
     * Returns the grocery list.
     *
     * Supports two optional query：
     * - category：filter items by category first
     * - search：then filter by name keyword
     */
    @GetMapping("/items")
    // ===== teammate note =====
    // Entry point for querying the grocery list.
    // If we later add sorting, pagination, or filters like collectedOnly, extend the query logic here.
    // insert your code here: add more filters like sort/collectedOnly/pagination
    public List<GroceryItem> items(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "search", required = false) String search) {

        // First handle category filtering.
        List<GroceryItem> base = (category == null || category.isBlank())
                ? groceryController.getItems()
                : groceryController.filterByCategory(category);

        // If no search term, return current results.
        if (search == null || search.isBlank()) {
            return base;
        }

        // Then filter by name using substring matching.
        String needle = search.trim().toLowerCase();
        return base.stream()
                .filter(item -> item.getName().toLowerCase().contains(needle))
                .collect(Collectors.toList());
    }

    /**
     * Manually add a grocery item.
     *
     * The system attempts to resolve the item using the food catalog:
     * - If recognized, use canonical name and category
     * - Otherwise, fall back to a default "Misc" category
     */
    @PostMapping("/items")
    // ===== teammate note =====
    // Handles manual addition of grocery items.
    // Currently tries catalog resolution first; otherwise falls back to Misc.
    // If we later enforce stricter validation or restrict free input, update this endpoint accordingly.
    // insert your code here: tighten validation or align this endpoint with final PRD rules
    public ResponseEntity<GroceryItem> addLine(@RequestBody GroceryAddRequest body) {
        if (body == null || body.getFoodName() == null || body.getFoodName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "foodName must not be blank");
        }

        if (body.getQuantity() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be a non-negative integer");
        }
        if (!Double.isFinite(body.getPrice()) || body.getPrice() < 0 || body.getPrice() > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "price must be a finite number between 0 and 1000000");
        }

        Optional<FoodCatalogEntry> resolved = catalogController.resolveEntry(body.getFoodName().trim());

        // If exact match not found, try suggestion.
        FoodCatalogEntry entry = resolved.orElseGet(
                () -> catalogController.searchSuggestions(body.getFoodName().trim()).stream()
                        .findFirst()
                        .orElse(null));

        // If still not found, assign to Misc category.
        if (entry == null) {
            FoodCategory misc = new FoodCategory("misc", "Misc", "box");
            entry = new FoodCatalogEntry(body.getFoodName().trim(), 3, misc);
        }

        FoodCategory cat = entry.getCategory();
        String id = UUID.randomUUID().toString();
        String name = catalogController.canonicalFoodName(body.getFoodName().trim());

        GroceryItem line = new GroceryItem(id, name, cat, body.getQuantity(), body.getPrice(), false);
        groceryController.addLine(line);
        return ResponseEntity.ok(line);
    }

    /**
     * Delete a grocery item.
     *
     * Verifies that the item exists before deleting;
     * otherwise returns 404.
     */
    @DeleteMapping("/items/{id}")
    // ===== teammate note =====
    // Handles deletion of a grocery item.
    // Can be extended for soft delete, undo, or batch deletion in the future.
    // insert your code here: extend delete behavior if needed
    public ResponseEntity<Void> deleteLine(@PathVariable String id) {
        boolean exists = groceryController.getItems().stream().anyMatch(i -> i.getId().equals(id));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grocery item not found");
        }
        groceryController.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Toggle whether an item has been collected (purchased).
     */
    @PatchMapping("/items/{id}/collected")
    public ResponseEntity<GroceryItem> toggleCollected(@PathVariable String id) {
        try {
            return ResponseEntity.ok(groceryController.toggleCollected(id));
        } catch (IllegalArgumentException missingInCurrentUser) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grocery item not found");
        }
    }

    /**
     * Update quantity using a delta value.
     *
     * For example:
     * - delta=1 increment by 1
     * - delta=-1 decrement by 1
     */
    @PatchMapping("/items/{id}/quantity")
    // ===== teammate note =====
    // Currently updates quantity using delta.
    // If the frontend switches to setting an absolute value instead, update this method along with controller/service logic.
    // insert your code here: support setQuantity mode if UI changes
    public ResponseEntity<GroceryItem> updateQuantity(
            @PathVariable String id,
            @RequestParam("delta") int delta) {
        try {
            return ResponseEntity.ok(groceryController.updateQuantity(id, delta));
        } catch (IllegalArgumentException missingInCurrentUser) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grocery item not found");
        }
    }

    /**
     * Checkout operation.
     *
     * Delegates to groceryController.checkout().
     */
    @PostMapping("/checkout")
    public void checkout() {
        groceryController.checkout();
    }

    /**
     * Returns aggregated totals.
     *
     * Common values used by frontend:
     * - subtotal
     * - tax
     * - total
     */
    @GetMapping("/totals")
    public Map<String, Double> totals() {
        double sub = groceryController.calculateSubtotal();
        double tax = groceryController.calculateTax(sub);
        double total = groceryController.calculateTotal(sub, tax);
        Map<String, Double> m = new HashMap<>();
        m.put("subtotal", sub);
        m.put("tax", tax);
        m.put("total", total);
        return m;
    }
}
