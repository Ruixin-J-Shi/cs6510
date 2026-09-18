package com.cs6510.checkout.service;

import com.cs6510.checkout.config.AppConfig;
import com.cs6510.checkout.dto.response.PopularItemDto;
import com.cs6510.checkout.dto.response.PopularItemsResponseDto;
import com.cs6510.checkout.model.PopularItem;
import com.cs6510.checkout.repository.InventoryRepository;
import com.cs6510.checkout.repository.PopularItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

    private final ArrayDeque<String> scanWindow;
    private final ReentrantLock      windowLock = new ReentrantLock();
    private final AtomicLong         scanCounter = new AtomicLong(0);

    // Self-reference through Spring proxy so @Async is honoured on internal calls.
    @Lazy @Autowired
    private AnalyticsService self;

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
     * Records a scan. Every slideInterval scans a snapshot is taken atomically
     * under the window lock and handed to the async worker through the Spring proxy,
     * so the @Async annotation actually fires a background thread.
     */
    public void recordScan(String sku) {
        List<String> snapshot = null;
        long count;

        windowLock.lock();
        try {
            count = scanCounter.incrementAndGet();
            scanWindow.addLast(sku);
            if (scanWindow.size() > windowSize) scanWindow.pollFirst();
            if (count % slideInterval == 0) {
                snapshot = new ArrayList<>(scanWindow);
            }
        } finally {
            windowLock.unlock();
        }

        if (snapshot != null) {
            self.computeAndPersist(snapshot, count);
        }
    }

    /** Runs on the dedicated analytics executor — never blocks a scan response. */
    @Async("analyticsExecutor")
    @Transactional
    public void computeAndPersist(List<String> snapshot, long endCount) {
        if (snapshot.isEmpty()) return;

        Map<String, Long> freq = snapshot.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        List<Map.Entry<String, Long>> top = freq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .toList();

        long windowStart = Math.max(1L, endCount - windowSize + 1);
        Instant now      = Instant.now();

        List<PopularItem> rows = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            String sku   = top.get(i).getKey();
            int    count = top.get(i).getValue().intValue();
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

    @Transactional(readOnly = true)
    public PopularItemsResponseDto getPopularItems(int limit) {
        List<PopularItem> rows = popularItemRepo.findAllByOrderByRankAsc();

        if (rows.isEmpty()) {
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

    public long getScanCount() {
        return scanCounter.get();
    }

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
