package com.cs6510.checkout.service;

import com.cs6510.checkout.config.AppConfig;
import com.cs6510.checkout.model.PopularItem;
import com.cs6510.checkout.repository.InventoryRepository;
import com.cs6510.checkout.repository.PopularItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsSnapshotService {

    private final PopularItemRepository popularItemRepo;
    private final InventoryRepository inventoryRepo;
    private final int windowSize;
    private final int slideInterval;

    public AnalyticsSnapshotService(PopularItemRepository popularItemRepo,
                                    InventoryRepository inventoryRepo,
                                    AppConfig config) {
        this.popularItemRepo = popularItemRepo;
        this.inventoryRepo = inventoryRepo;
        this.windowSize = config.getWindowSize();
        this.slideInterval = config.getSlideInterval();
    }

    @Transactional
    public void computeAndPersist(List<String> snapshot, long endCount) {
        Map<String, Long> frequencies = snapshot.stream()
                .collect(Collectors.groupingBy(sku -> sku, Collectors.counting()));
        List<Map.Entry<String, Long>> top = frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .toList();
        Map<String, String> names = inventoryRepo.findAllById(
                        top.stream().map(Map.Entry::getKey).toList()).stream()
                .collect(Collectors.toMap(inventory -> inventory.getSku(), inventory -> inventory.getName()));
        long windowStart = Math.max(1L, endCount - windowSize + 1);
        Instant computedAt = Instant.now();
        List<PopularItem> rows = new ArrayList<>(top.size());
        for (int index = 0; index < top.size(); index++) {
            Map.Entry<String, Long> entry = top.get(index);
            rows.add(new PopularItem(index + 1, entry.getKey(), names.get(entry.getKey()),
                    entry.getValue().intValue(), windowSize, slideInterval,
                    windowStart, endCount, computedAt));
        }
        popularItemRepo.truncateAll();
        popularItemRepo.saveAll(rows);
    }
}
