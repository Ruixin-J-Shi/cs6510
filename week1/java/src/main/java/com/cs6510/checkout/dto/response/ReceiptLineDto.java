package com.cs6510.checkout.dto.response;

public record ReceiptLineDto(String sku, String name, double unitPrice, int quantity) {}
