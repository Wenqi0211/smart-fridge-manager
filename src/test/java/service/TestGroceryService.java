package service;

import model.FoodCategory;
import model.GroceryItem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TDD：Grocery list and pricing rules(Maps to: {@link GroceryService}，including PRD subtotal, tax, and checkout logic).
 */
public class TestGroceryService {
    private GroceryService groceryService;

    @BeforeEach
    void setUp() {
        FoodCategory cat = new FoodCategory("c1", "Dairy", "milk");
        groceryService = new GroceryService(List.of(
                new GroceryItem("1", "Milk", cat, 2, 3.0, false)));
    }

    /**
     * Test : Retrieves all grocery items in the current list.
     * Verification: Matches the number of items initialized.
     * <p>
     * Maps to: {@link GroceryService#getItems()}
     */
    @Test
    void testGetItemsReturnsCurrentGroceryItems() {
        assertEquals(1, groceryService.getItems().size());
    }

    /**
     * Test: append a new item to the grocery list(e.g., importing missing ingredients).
     * Verification: list size increases by 1 after {@code addLine}.
     * <p>
     * Maps to: {@link GroceryService#addLine(GroceryItem)}、{@link GroceryService#getItems()}
     */
    @Test
    void testAddLineAppendsRow() {
        FoodCategory cat = new FoodCategory("c2", "Produce", "leaf");
        groceryService.addLine(new GroceryItem("2", "Spinach", cat, 1, 1.5, false));
        assertEquals(2, groceryService.getItems().size());
    }

    /**
     * Test: Toggles "collected" (purchased) status.
     * Verification: Only the target item's collected flag is updated.
     * <p>
     * Maps to: {@link GroceryService#toggleCollected(String)}
     */
    @Test
    void testToggleCollectedUpdatesState() {
        assertEquals(true, groceryService.toggleCollected("1").isCollected());
    }

    /**
     * Test: Updates item quantity using a delta.
     * Verification: Quantity is updated within valid bounds.
     * <p>
     * Maps to: {@link GroceryService#updateQuantity(String, int)}
     */
    @Test
    void testUpdateQuantityChangesAmount() {
        assertEquals(3, groceryService.updateQuantity("1", 1).getQuantity());
    }

    /**
     * Test: Deletes an item from the grocery list.
     * Verification: List becomes empty after deletion.
     * <p>
     * Maps to: {@link GroceryService#deleteItem(String)}、{@link GroceryService#getItems()}
     */
    @Test
    void testDeleteItemRemovesItem() {
        groceryService.deleteItem("1");
        assertEquals(0, groceryService.getItems().size());
    }

    /**
     * Test: subtotal = Σ(price × quantity), counting only collected items.
     * Verification: 2 × 3.0 = 6.0.
     * <p>
     * Maps to: {@link GroceryService#calculateSubtotal()}
     */
    @Test
    void testCalculateSubtotalUsesCollectedItems() {
        FoodCategory cat = new FoodCategory("c1", "Dairy", "milk");
        GroceryService svc = new GroceryService(List.of(
                new GroceryItem("1", "Milk", cat, 2, 3.0, true)));
        assertEquals(6.0, svc.calculateSubtotal());
    }

    /**
     * Test: tax = subtotal × 8%.
     * Verification: 6.0 → 0.48.
     * <p>
     * Maps to: {@link GroceryService#calculateTax(double)}
     */
    @Test
    void testCalculateTaxUsesEightPercent() {
        assertEquals(0.48, groceryService.calculateTax(6.0), 0.0001);
    }

    /**
     * Test: total = subtotal + tax.
     * Verification: 6.0 + 0.48 = 6.48.
     * <p>
     * Maps to: {@link GroceryService#calculateTotal(double, double)}
     */
    @Test
    void testCalculateTotalAddsSubtotalAndTax() {
        assertEquals(6.48, groceryService.calculateTotal(6.0, 0.48), 0.0001);
    }

    /**
     * Test: Checkout clears the grocery list(full session reset handled at a higher level).
     * Verification: List size is 0 after checkout.
     * <p>
     * Maps to: {@link GroceryService#checkout()}、{@link GroceryService#getItems()}
     */
    @Test
    void testCheckoutClearsGroceryItems() {
        groceryService.checkout();
        assertEquals(0, groceryService.getItems().size());
    }

    /**
     * Test: Clears grocery list directly (session reset).
     * Verification: List is empty after {@code clearGrocery}.
     * <p>
     * Maps to: {@link GroceryService#clearGrocery()}
     */
    @Test
    void testClearGroceryRemovesAllItems() {
        groceryService.clearGrocery();
        assertEquals(0, groceryService.getItems().size());
    }

    /** Test: null category returns all items */
    @Test
    void testFilterByCategoryReturnsAllForNullCategory() {
        assertEquals(1, groceryService.filterByCategory(null).size());
    }

    /** Test: "All" keyword returns all items */
    @Test
    void testFilterByCategoryReturnsAllForAllKeyword() {
        assertEquals(1, groceryService.filterByCategory("All").size());
    }

    /** Test: quantity floor at zero when delta is negative */
    @Test
    void testUpdateQuantityFloorIsZero() {
        assertEquals(0, groceryService.updateQuantity("1", -999).getQuantity());
    }

    /** Test: uncollected items are excluded from subtotal */
    @Test
    void testCalculateSubtotalExcludesUncollectedItems() {
        assertEquals(0.0, groceryService.calculateSubtotal());
    }

    /** Test: null keyword returns all items */
    @Test
    void testSearchByNameReturnsAllForNullKeyword() {
        assertEquals(1, groceryService.searchByName(null).size());
    }

    /** Test: blank keyword returns all items */
    @Test
    void testSearchByNameReturnsAllForBlankKeyword() {
        assertEquals(1, groceryService.searchByName("   ").size());
    }
    @Test
    void testOverflowDoesNotResetOrMutateQuantity() {
        groceryService.updateQuantity("1", Integer.MAX_VALUE - 2);
        assertThrows(GroceryQuantityLimitException.class,
                () -> groceryService.updateQuantity("1", 1));
        assertEquals(Integer.MAX_VALUE, groceryService.getItems().get(0).getQuantity());
    }

    @Test
    void testLargestNegativeDeltaStillFloorsAtZero() {
        assertEquals(0, groceryService.updateQuantity("1", Integer.MIN_VALUE).getQuantity());
    }
}
