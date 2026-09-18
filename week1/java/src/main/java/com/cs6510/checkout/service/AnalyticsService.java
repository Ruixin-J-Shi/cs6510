package com.cs6510.checkout.service;

import com.cs6510.checkout.config.AppConfig;
import com.cs6510.checkout.dto.response.PopularItemDto;
import com.cs6510.checkout.dto.response.PopularItemsResponseDto;
import com.cs6510.checkout.model.PopularItem;
import com.cs6510.checkout.repository.InventoryRepository;
import com.cs6510.checkout.repository.PopularItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final PopularItemRepository popularItemRepo;
    private final InventoryRepository   inventoryRepo;
    private final int windowSize;
    private final int slideInterval;

    // In-memory circular window of the last `windowSize` scanned SKUs.
    private final ArrayDeque<String> scanWindow;
    private final ReentrantLock      windowLock = new ReentrantLock();

    // Global scan sequence number — monotonically increasing.
    private final AtomicLong scanCounter = new AtomicLong(0);

    public AnalyticsService(PopularItemRepository popularItemRepo,
                            InventoryRepository inventoryRepo,
                            AppConfig config) {
        this.popularItemRepo = popularItemRepo;
        this.inventoryRepo   = inventoryRepo;
        this.windowSize      = config.getWindowSize();
        this.slideInterval   = config.getSlideInterval();
        this.scanWindow      = new ArrayDeque<>(windowSize + 1);
    }

    /**
     * Records a scan in the sliding window and triggers a recompute every
     * {@code slideInterval} scans. The recompute runs asynchronously so it
     * never adds latency to the scan response.
     */
    public void recordScan(String sku) {
        long count = scanCounter.incrementAndGet();

        windowLock.lock();
        try {
            scanWindow.addLast(sku);
            if (scanWindow.size() > windowSize) {
                scanWindow.pollFirst();
            }
        } finally {
            windowLock.unlock();
        }

        // Exactly one thread will ever get a count divisible by slideInterval.
        if (count % slideInterval == 0) {
            computeAndPersist(count);
        }
    }

    /** Runs on the dedicated analytics executor thread — never blocks a scan response. */
    @Async("analyticsExecutor")
    @Transactional
    public void computeAndPersist(long endCount) {
        List<String> snapshot;
        windowLock.lock();
        try {
            snapshot = new ArrayList<>(scanWindow);
        } finally {
            windowLock.unlock();
        }

        if (snapshot.isEmpty()) return;

        // Count scan frequency per SKU.
        Map<String, Long> freq = snapshot.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        // Sort by count descending, take top 10.
        List<Map.Entry<String, Long>> top = freq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .toList();

        // Resolve names from inventory (best-effort; unknown SKU gets placeholder).
        long windowStart = Math.max(1L, endCount - windowSize + 1);
        Instant now      = Instant.now();

        // Build entities, replacing the whole snapshot atomically.
        List<PopularItem> rows = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            String sku   = top.get(i).getKey();
            int    count = top.get(i).getValue().intValue();
            // name comes from the cached catalog; fall back to sku if not found
            String name  = inventoryRepo.findById(sku)
                               .map(inv -> inv.getName())
                               .orElse(sku);
            rows.add(new PopularItem(i + 1, sku, name, count,
                                     windowSize, slideInterval,
                                     windowStart, endCount, now));
        }

        popularItemRepo.truncateAll();
        popularItemRepo.saveAll(rows);

        log.debug("Popular-items window [{}-{}] persisted ({} items)", windowStart, endCount, rows.size());
    }

    /** Returns the latest persisted window. */
    @Transactional(readOnly = true)
    public PopularItemsResponseDto getPopularItems(int limit) {
        List<PopularItem> rows = popularItemRepo.findAllByOrderByRankAsc();

        if (rows.isEmpty()) {
            // No window computed yet (too few scans so far).
            return new PopularItemsResponseDto(
                    windowSize, slideInterval, 0, 0, Instant.now(), List.of());
        }

        PopularItem meta = rows.get(0);
        List<PopularItemDto> items = rows.stream()
                .limit(limit)
                .map(r -> new PopularItemDto(r.getSku(), r.getName(), r.getScanCount(), r.getRank()))
                .toList();

        return new PopularItemsResponseDto(
                meta.getWindowSize(), meta.getSlideInterval(),
                meta.getWindowStart(), meta.getWindowEnd(),
                meta.getComputedAt(), items);
    }

    /** Returns the current global scan counter value (for window boundary tracking). */
    public long getScanCount() {
        return scanCounter.get();
    }

    /** Admin reset — wipes the in-memory window and DB snapshot. */
    @Transactional
    public void reset() {
        windowLock.lock();
        try {
            scanWindow.clear();
        } finally {
            windowLock.unlock();
        }
        scanCounter.set(0);
        popularItemRepo.truncateAll();
    }
}
