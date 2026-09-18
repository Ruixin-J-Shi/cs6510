package com.cs6510.checkout.dto.response;

import java.time.Instant;

public record TransactionDto(
    String  transactionId,
    String  stationId,
    String  status,
    int     itemCount,
    double  runningTotal,
    Instant startedAt
) {}
