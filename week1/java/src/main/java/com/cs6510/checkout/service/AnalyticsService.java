package com.cs6510.checkout.service;

import com.cs6510.checkout.config.AppConfig;
import com.cs6510.checkout.dto.response.PopularItemDto;
import com.cs6510.checkout.dto.response.PopularItemsResponseDto;
import com.cs6510.checkout.model.PopularItem;
import com.cs6510.checkout.repository.PopularItemRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AnalyticsService {

    private final PopularItemRepository popularItemRepo;
    private final AnalyticsSnapshotService snapshotService;
    private final Executor analyticsExecutor;
    private final int windowSize;
    private final int slideInterval;
    private final ArrayDeque<String> scanWindow;
    private final ReentrantLock windowLock = new ReentrantLock();
    private long scanCounter;
    private CompletableFuture<Void> latestSnapshot = CompletableFuture.completedFuture(null);

    public AnalyticsService(PopularItemRepository popularItemRepo,
                            AnalyticsSnapshotService snapshotService,
                            @Qualifier("analyticsExecutor") Executor analyticsExecutor,
                            AppConfig config) {
        this.popularItemRepo = popularItemRepo;
        this.snapshotService = snapshotService;
        this.analyticsExecutor = analyticsExecutor;
        this.windowSize = config.getWindowSize();
        this.slideInterval = config.getSlideInterval();
        this.scanWindow = new ArrayDeque<>(windowSize + 1);
    }

    public void recordScan(String sku) {
        windowLock.lock();
        try {
            long count = ++scanCounter;
            scanWindow.addLast(sku);
            if (scanWindow.size() > windowSize) {
                scanWindow.removeFirst();
            }
            if (count % slideInterval == 0) {
                List<String> snapshot = List.copyOf(scanWindow);
                latestSnapshot = CompletableFuture.runAsync(
                        () -> snapshotService.computeAndPersist(snapshot, count), analyticsExecutor);
            }
        } finally {
            windowLock.unlock();
        }
    }

    public PopularItemsResponseDto getPopularItems(int limit) {
        CompletableFuture<Void> pendingSnapshot;
        windowLock.lock();
        try {
            pendingSnapshot = latestSnapshot;
        } finally {
            windowLock.unlock();
        }
        pendingSnapshot.join();
        List<PopularItem> rows = popularItemRepo.findAllByOrderByRankAsc();

        if (rows.isEmpty()) {
            return new PopularItemsResponseDto(
                    windowSize, slideInterval, 0, 0, Instant.now(), List.of());
        }

        PopularItem metadata = rows.get(0);
        List<PopularItemDto> items = rows.stream()
                .limit(limit)
                .map(row -> new PopularItemDto(row.getSku(), row.getName(), row.getScanCount(), row.getRank()))
                .toList();

        return new PopularItemsResponseDto(
                metadata.getWindowSize(), metadata.getSlideInterval(),
                metadata.getWindowStart(), metadata.getWindowEnd(),
                metadata.getComputedAt(), items);
    }

    public long getScanCount() {
        windowLock.lock();
        try {
            return scanCounter;
        } finally {
            windowLock.unlock();
        }
    }

    @Transactional
    public void reset() {
        windowLock.lock();
        try {
            latestSnapshot.handle((result, failure) -> null).join();
            popularItemRepo.truncateAll();
            scanWindow.clear();
            scanCounter = 0;
            latestSnapshot = CompletableFuture.completedFuture(null);
        } finally {
            windowLock.unlock();
        }
    }
}
