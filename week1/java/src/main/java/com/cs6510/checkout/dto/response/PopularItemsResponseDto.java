package com.cs6510.checkout.dto.response;

import java.time.Instant;
import java.util.List;

public record PopularItemsResponseDto(
    int                  windowSize,
    int                  slideInterval,
    long                 windowStart,
    long                 windowEnd,
    Instant              computedAt,
    List<PopularItemDto> items
) {}
