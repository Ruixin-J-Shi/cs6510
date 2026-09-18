package com.cs6510.checkout.exception;

public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(String sku) {
        super("Item not found: " + sku);
    }
}
