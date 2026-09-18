package com.cs6510.checkout.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ScanItemRequest(@NotBlank String sku) {}
