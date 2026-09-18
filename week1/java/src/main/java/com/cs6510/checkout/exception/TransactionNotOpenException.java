package com.cs6510.checkout.exception;

public class TransactionNotOpenException extends RuntimeException {
    public TransactionNotOpenException(String id) {
        super("Transaction " + id + " is not open");
    }
}
