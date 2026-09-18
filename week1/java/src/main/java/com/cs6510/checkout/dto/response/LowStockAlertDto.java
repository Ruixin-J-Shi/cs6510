package com.cs6510.checkout.dto.response;

import java.time.Instant;

public record LowStockAlertDto(
    String  sku,
    String  name,
    int     currentStock,
    int     threshold,
    Instant triggeredAt
) {}
