package com.cs6510.checkout.service;

import com.cs6510.checkout.dto.response.CatalogItemDto;
import com.cs6510.checkout.dto.response.CatalogResponseDto;
import com.cs6510.checkout.repository.InventoryRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {

    private final InventoryRepository inventoryRepo;

    public CatalogService(InventoryRepository inventoryRepo) {
        this.inventoryRepo = inventoryRepo;
    }

    /**
     * Returns the full catalog (sku, name, price).
     * Cached in memory — the catalog never changes during a test run.
     */
    @Cacheable("catalog")
    @Transactional(readOnly = true)
    public CatalogResponseDto getCatalog() {
        List<CatalogItemDto> items = inventoryRepo.findAll().stream()
                .map(i -> new CatalogItemDto(i.getSku(), i.getName(), i.getPrice()))
                .toList();
        return new CatalogResponseDto(items);
    }

    /** Called by admin reset to bust the catalog cache. */
    public void evictCatalogCache(org.springframework.cache.CacheManager cm) {
        var cache = cm.getCache("catalog");
        if (cache != null) cache.clear();
    }
}
