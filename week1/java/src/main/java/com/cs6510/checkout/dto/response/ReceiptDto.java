package com.cs6510.checkout.dto.response;

import java.time.Instant;
import java.util.List;

public record ReceiptDto(
    String          transactionId,
    String          stationId,
    int             itemCount,
    double          totalAmount,
    Instant         startedAt,
    Instant         completedAt,
    List<ReceiptLineDto> lines
) {}
