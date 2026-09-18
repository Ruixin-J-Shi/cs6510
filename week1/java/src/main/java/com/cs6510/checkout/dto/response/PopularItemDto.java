package com.cs6510.checkout.dto.response;

public record PopularItemDto(String sku, String name, int scanCount, int rank) {}
