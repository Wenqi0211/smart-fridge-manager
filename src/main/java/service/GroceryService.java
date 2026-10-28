package service;

import model.GroceryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class manages the state of the grocery list.
 *
 * In simple terms, it is responsible for:
 * - storing the current grocery items
 * - tracking collected status
 * - updating quantities
 * - calculating subtotal, tax, and total
 * - clearing the list after checkout
 *
 * All core grocery related business logic is handled here.
 */
public class GroceryService implements IGroceryService {
    // Fixed tax rate (8%) as specified in the PRD.
    private static final double TAX_RATE = 0.08;

    // Grocery list for the current session.
    // This list is mutable, so all add/update/delete operations modify it directly.
    private final List<GroceryItem> groceryItems;

    /**
     * Constructor.
     *
     * Creates a defensive copy of the provided list,
     * so external modifications do not affect internal state.
     */
    public GroceryService(List<GroceryItem> items) {
        this.groceryItems = new ArrayList<>(items);
    }

    /**
     * Returns the current grocery list.
     *
     * A copy is returned instead of the internal list,
     * to prevent external mutation.
     */
    @Override
    public List<GroceryItem> getItems() {
        return new ArrayList<>(groceryItems);
    }

    /**
     * Adds a new grocery item.
     *
     * Common scenarios:
     * - manually adding an item from the frontend
     * - importing missing ingredients from a recipe
     */
    @Override
    public void addLine(GroceryItem item) {
        groceryItems.add(item);
    }

    /**
     * Filters items by category.
     *
     * Rules:
     * - null / blank / "All" → return all items
     * - otherwise → exact match on category name
     */
    @Override
    // ===== teammate note =====
    // Category filtering extracted from team logic.
    // If category naming, casing, or id/name mapping changes,
    // update matching logic here.
    // insert your code here: refine category matching rules
    public List<GroceryItem> filterByCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank() || categoryName.equalsIgnoreCase("All")) {
            return new ArrayList<>(groceryItems);
        }
        String needle = categoryName.trim().toLowerCase(Locale.ROOT);
        return groceryItems.stream()
                .filter(i -> i.getCategory().getName().toLowerCase(Locale.ROOT).equals(needle))
                .collect(Collectors.toList());
    }

    /**
     * Searches items by name.
     *
     * Uses substring matching (not exact match).
     * Example:
     * - searching "mil" will match "milk"
     */
    @Override
    // ===== teammate note =====
    // Name search for grocery items.
    // Can be extended to support fuzzy matching, aliases,
    // or spell correction in the future.
    // insert your code here: upgrade search behavior
    public List<GroceryItem> searchByName(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>(groceryItems);
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return groceryItems.stream()
                .filter(i -> i.getName().toLowerCase(Locale.ROOT).contains(needle))
                .collect(Collectors.toList());
    }

    /**
     * Toggles the collected (purchased) status of an item.
     *
     * This status is important because subtotal calculation
     * only includes items that have been collected.
     */
    @Override
    public GroceryItem toggleCollected(String itemId) {
        GroceryItem item = findById(itemId);
        item.setCollected(!item.isCollected());
        return item;
    }

    /**
     * Updates item quantity using a delta value.
     *
     * Example:
     * - current = 2, delta = 3 → becomes 5
     * - negative delta decreases quantity
     *
     * Lower bound is enforced: quantity cannot go below 0.
     */
    @Override
    public GroceryItem updateQuantity(String itemId, int delta) {
        GroceryItem item = findById(itemId);
        long next = (long) item.getQuantity() + delta;
        if (next > Integer.MAX_VALUE) {
            throw new GroceryQuantityLimitException();
        }
        item.setQuantity((int) Math.max(0L, next));
        return item;
    }

    /**
     * Deletes a grocery item by ID.
     */
    @Override
    public void deleteItem(String itemId) {
        groceryItems.removeIf(i -> i.getId().equals(itemId));
    }

    /**
     * Calculates subtotal.
     *
     * Rules:
     * - only includes collected items
     * - item cost = price × quantity
     */
    @Override
    public double calculateSubtotal() {
        return groceryItems.stream()
                .filter(GroceryItem::isCollected)
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();
    }

    /**
     * Calculates tax as: subtotal × fixed tax rate
     */
    @Override
    public double calculateTax(double subtotal) {
        return subtotal * TAX_RATE;
    }

    /**
     * Calculates total as: subtotal + tax
     */
    @Override
    public double calculateTotal(double subtotal, double tax) {
        return subtotal + tax;
    }

    /**
     * Checkout operation for the grocery module.
     *
     * Currently, this only clears the grocery list.
     * If additional actions are needed (e.g., clearing inventory,
     * resetting preferences), they should be handled at a higher level.
     */
    @Override
    public void checkout() {
        clearGrocery();
    }

    /**
     * Clears the entire grocery list.
     */
    @Override
    public void clearGrocery() {
        groceryItems.clear();
    }

    /**
     * Finds an item by ID.
     *
     * Throws an exception if not found,
     * so the caller is aware of invalid input.
     */
    private GroceryItem findById(String itemId) {
        Optional<GroceryItem> item = groceryItems.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst();
        return item.orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
    }
}
