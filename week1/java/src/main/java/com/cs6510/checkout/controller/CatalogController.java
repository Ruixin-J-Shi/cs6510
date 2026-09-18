package com.cs6510.checkout.controller;

import com.cs6510.checkout.dto.response.CatalogResponseDto;
import com.cs6510.checkout.service.CatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/items")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<CatalogResponseDto> getCatalog() {
        return ResponseEntity.ok(catalogService.getCatalog());
    }
}
