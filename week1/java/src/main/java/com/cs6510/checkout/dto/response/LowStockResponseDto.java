package com.cs6510.checkout.dto.response;

import java.time.Instant;
import java.util.List;

public record LowStockResponseDto(
    int                  threshold,
    Instant              generatedAt,
    List<LowStockAlertDto> alerts
) {}
