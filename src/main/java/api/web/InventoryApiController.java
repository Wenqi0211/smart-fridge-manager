package api.web;

import api.dto.AddFoodRequest;
import api.dto.FoodItemResponse;
import controller.InventoryController;
import model.FoodItem;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HTTP entry layer for inventory endpoints used by the Flutter frontend.
 * <p>
 * Spring request mapping and request/response handling live here. Inventory actions delegate to
 * {@link InventoryController}; catalog search stays in {@link CatalogApiController}.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryApiController {

    private final InventoryController inventoryController;

    public InventoryApiController(InventoryController inventoryController) {
        this.inventoryController = inventoryController;
    }

    @GetMapping
    public List<FoodItemResponse> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sort", required = false) String sort) {
        List<FoodItem> items;
        if (category != null && !category.isBlank()) {
            items = inventoryController.filterByCategory(category);
        } else {
            items = inventoryController.getVisibleItems();
        }
        if (sort != null && !sort.isBlank()) {
            items = new ArrayList<>(items);
            if ("expiry".equalsIgnoreCase(sort)) {
                items.sort(Comparator.comparing(FoodItem::getExpiryDate));
            } else if ("created".equalsIgnoreCase(sort)) {
                items.sort(Comparator.comparing(FoodItem::getCreatedAt).reversed());
            }
        }
        return items.stream()
                .map(FoodItemResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<FoodItemResponse> add(@RequestBody AddFoodRequest body) {
        if (body == null || body.getFoodName() == null || body.getFoodName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "foodName must not be blank");
        }
        FoodItem added = inventoryController.addItem(body.getFoodName().trim());
        return ResponseEntity.ok(FoodItemResponse.from(added));
    }
}
