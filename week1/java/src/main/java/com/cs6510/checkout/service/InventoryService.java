package com.cs6510.checkout.service;

import com.cs6510.checkout.config.AppConfig;
import com.cs6510.checkout.dto.response.LowStockAlertDto;
import com.cs6510.checkout.dto.response.LowStockResponseDto;
import com.cs6510.checkout.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepo;
    private final int                 defaultThreshold;

    public InventoryService(InventoryRepository inventoryRepo, AppConfig config) {
        this.inventoryRepo    = inventoryRepo;
        this.defaultThreshold = config.getLowStockThreshold();
    }

    @Transactional(readOnly = true)
    public LowStockResponseDto getLowStockAlerts(Integer thresholdOverride) {
        int threshold = (thresholdOverride != null) ? thresholdOverride : defaultThreshold;
        Instant now   = Instant.now();

        List<LowStockAlertDto> alerts = inventoryRepo.findByStockLessThan(threshold).stream()
                .map(i -> new LowStockAlertDto(
                        i.getSku(),
                        i.getName(),
                        i.getStock(),
                        threshold,
                        // Use the recorded trigger time; fall back to now if not set.
                        i.getLowStockAt() != null ? i.getLowStockAt() : now))
                .toList();

        return new LowStockResponseDto(threshold, now, alerts);
    }
}
