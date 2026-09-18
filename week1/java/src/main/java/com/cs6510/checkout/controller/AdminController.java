package com.cs6510.checkout.controller;

import com.cs6510.checkout.repository.TransactionItemRepository;
import com.cs6510.checkout.repository.TransactionRepository;
import com.cs6510.checkout.service.AnalyticsService;
import com.cs6510.checkout.service.CatalogService;
import com.cs6510.checkout.repository.InventoryRepository;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final TransactionItemRepository txItemRepo;
    private final TransactionRepository     txRepo;
    private final InventoryRepository       inventoryRepo;
    private final AnalyticsService          analyticsService;
    private final CatalogService            catalogService;
    private final CacheManager              cacheManager;

    public AdminController(TransactionItemRepository txItemRepo,
                           TransactionRepository txRepo,
                           InventoryRepository inventoryRepo,
                           AnalyticsService analyticsService,
                           CatalogService catalogService,
                           CacheManager cacheManager) {
        this.txItemRepo      = txItemRepo;
        this.txRepo          = txRepo;
        this.inventoryRepo   = inventoryRepo;
        this.analyticsService = analyticsService;
        this.catalogService  = catalogService;
        this.cacheManager    = cacheManager;
    }

    /**
     * Resets the server to a clean state without restarting Docker.
     * Useful for quick re-runs during development.
     * Full reset (wipe volume): run db/reset.sh instead.
     */
    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Map<String, Object>> reset() {
        analyticsService.reset();
        // Order matters: items before transactions (FK constraint)
        txItemRepo.truncateAll();
        txRepo.truncateAll();
        inventoryRepo.resetAllStock(10000);
        catalogService.evictCatalogCache(cacheManager);

        long count = inventoryRepo.count();
        return ResponseEntity.ok(Map.of(
                "message", "Reset complete",
                "itemsReset", count));
    }
}
