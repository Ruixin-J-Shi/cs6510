package com.cs6510.checkout.dto.response;

public record ScanResultDto(
    String transactionId,
    String sku,
    String name,
    double unitPrice,
    int    itemCount,
    double runningTotal
) {}
