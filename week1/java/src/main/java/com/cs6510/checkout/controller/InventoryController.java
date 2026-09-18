package com.cs6510.checkout.controller;

import com.cs6510.checkout.dto.response.LowStockResponseDto;
import com.cs6510.checkout.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/low-stock")
    public ResponseEntity<LowStockResponseDto> lowStock(
            @RequestParam(required = false) Integer threshold) {
        return ResponseEntity.ok(inventoryService.getLowStockAlerts(threshold));
    }
}
