package API.web;


import api.dto.GroceryAddRequest;
import api.web.GroceryApiController;
import controller.CatalogController;
import controller.GroceryController;
import model.FoodCatalogEntry;
import model.FoodCategory;
import model.GroceryItem;
import service.FoodCatalog;
import service.GroceryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GroceryApiController.
 * Directly instantiates the controller without starting a Spring server.
 * Each test targets one specific endpoint or branch.
 */
public class TestGroceryApiController {

    private GroceryApiController groceryApiController;
    private GroceryService groceryService;
    private FoodCategory cat;

    @BeforeEach
    void setUp() {
        cat = new FoodCategory("c1", "Dairy", "milk");

        // Set up catalog with one known item
        FoodCatalog foodCatalog = new FoodCatalog(List.of(
                new FoodCatalogEntry("Milk", 7, cat)));
        CatalogController catalogController = new CatalogController(foodCatalog);

        // Set up grocery service with one pre-existing item
        groceryService = new GroceryService(List.of(
                new GroceryItem("item-1", "Milk", cat, 2, 3.0, false)));
        GroceryController groceryController = new GroceryController(groceryService);

        groceryApiController = new GroceryApiController(catalogController, groceryController);
    }

    // GET /items

    /**
     * Tests GET /items with no filters returns all items.
     * Branch: category == null, search == null → return full list.
     */
    @Test
    void testItemsWithNoFiltersReturnsAllItems() {
        List<GroceryItem> result = groceryApiController.items(null, null);
        assertEquals(1, result.size());
        assertEquals("Milk", result.get(0).getName());
    }

    /**
     * Tests GET /items with category filter returns matching items.
     * Branch: category is not blank → call filterByCategory().
     */
    @Test
    void testItemsWithCategoryFilterReturnsMatchingItems() {
        List<GroceryItem> result = groceryApiController.items("Dairy", null);
        assertEquals(1, result.size());
    }

    // POST /items

    /**
     * Tests POST /items with a known catalog item returns 200 OK.
     * Branch: catalog resolves the name → use canonical name and category.
     */
    @Test
    void testAddLineWithKnownItemReturnsOk() {
        GroceryAddRequest req = new GroceryAddRequest();
        req.setFoodName("Milk");
        req.setQuantity(1);
        req.setPrice(3.0);

        ResponseEntity<GroceryItem> response = groceryApiController.addLine(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Milk", response.getBody().getName());
    }

    /**
     * Tests POST /items with an unknown item falls back to Misc category.
     * Branch: catalog cannot resolve → entry == null → use Misc category.
     */
    @Test
    void testAddLineWithUnknownItemFallsBackToMiscCategory() {
        GroceryAddRequest req = new GroceryAddRequest();
        req.setFoodName("DragonFruit");
        req.setQuantity(1);
        req.setPrice(2.0);

        ResponseEntity<GroceryItem> response = groceryApiController.addLine(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Misc", response.getBody().getCategory().getName());
    }

    /**
     * Tests POST /items with null body returns 400 Bad Request.
     * Branch: body == null → return badRequest.
     */
    @Test
    void testAddLineWithNullBodyReturnsBadRequest() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> groceryApiController.addLine(null));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    /**
     * Tests POST /items rejects a negative price without adding a row.
     */
    @Test
    void testAddLineWithNegativePriceIsRejected() {
        GroceryAddRequest req = new GroceryAddRequest();
        req.setFoodName("Milk");
        req.setQuantity(1);
        req.setPrice(-5.0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> groceryApiController.addLine(req));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals(1, groceryService.getItems().size());
    }

    /**
     * Tests POST /items rejects a negative quantity without adding a row.
     */
    @Test
    void testAddLineWithNegativeQuantityIsRejected() {
        GroceryAddRequest req = new GroceryAddRequest();
        req.setFoodName("Milk");
        req.setQuantity(-3);
        req.setPrice(3.0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> groceryApiController.addLine(req));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals(1, groceryService.getItems().size());
    }

    // DELETE /items/{id}

    /**
     * Tests DELETE /items/{id} with existing id returns 204 No Content.
     * Branch: item exists → delete → return noContent.
     */
    @Test
    void testDeleteLineWithExistingIdReturnsNoContent() {
        ResponseEntity<Void> response = groceryApiController.deleteLine("item-1");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, groceryService.getItems().size());
    }

    /**
     * Tests DELETE /items/{id} with non-existing id returns 404 Not Found.
     * Branch: item does not exist → return notFound.
     */
    @Test
    void testDeleteLineWithNonExistingIdReturnsNotFound() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> groceryApiController.deleteLine("nonexistent"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    // PATCH /items/{id}/collected

    /**
     * Tests PATCH /items/{id}/collected toggles collected status and returns 200 OK.
     */
    @Test
    void testToggleCollectedReturnsUpdatedItem() {
        ResponseEntity<GroceryItem> response =
                groceryApiController.toggleCollected("item-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isCollected());
    }


    // POST /checkout

    /**
     * Tests POST /checkout clears the grocery list.
     */
    @Test
    void testCheckoutClearsGroceryList() {
        groceryApiController.checkout();
        assertEquals(0, groceryService.getItems().size());
    }

    // GET /totals
    /**
     * Tests GET /totals returns correct subtotal, tax, and total.
     * Uses a collected item so subtotal > 0.
     */
    @Test
    void testTotalsReturnsCorrectValues() {
        // Toggle item to collected so it counts toward subtotal
        groceryApiController.toggleCollected("item-1");

        Map<String, Double> result = groceryApiController.totals();

        assertNotNull(result);
        assertEquals(6.0,  result.get("subtotal"), 0.0001); // 2 × 3.0
        assertEquals(0.48, result.get("tax"),      0.0001); // 6.0 × 8%
        assertEquals(6.48, result.get("total"),    0.0001); // 6.0 + 0.48
    }

    /**
     * Tests GET /totals returns zero values when no items are collected.
     */
    @Test
    void testTotalsReturnsZeroWhenNoItemsCollected() {
        Map<String, Double> result = groceryApiController.totals();
        assertEquals(0.0, result.get("subtotal"), 0.0001);
        assertEquals(0.0, result.get("tax"),      0.0001);
        assertEquals(0.0, result.get("total"),    0.0001);
    }
}