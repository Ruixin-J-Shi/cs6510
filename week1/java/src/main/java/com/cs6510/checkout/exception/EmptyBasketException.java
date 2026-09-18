package com.cs6510.checkout.exception;

public class EmptyBasketException extends RuntimeException {
    public EmptyBasketException(String id) {
        super("Transaction " + id + " has an empty basket");
    }
}
