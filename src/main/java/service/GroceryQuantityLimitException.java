package service;

/** A grocery change would exceed the integer quantity model; no quantity has been changed. */
public class GroceryQuantityLimitException extends RuntimeException {
    public GroceryQuantityLimitException() {
        super("Grocery quantity cannot exceed 2147483647");
    }
}
