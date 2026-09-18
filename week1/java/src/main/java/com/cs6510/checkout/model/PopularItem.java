package com.cs6510.checkout.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "popular_items")
public class PopularItem {

    @Id
    @Column(name = "rank")
    private int rank;

    @Column(name = "sku", nullable = false, length = 20)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "scan_count", nullable = false)
    private int scanCount;

    @Column(name = "window_size", nullable = false)
    private int windowSize;

    @Column(name = "slide_interval", nullable = false)
    private int slideInterval;

    @Column(name = "window_start", nullable = false)
    private long windowStart;

    @Column(name = "window_end", nullable = false)
    private long windowEnd;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public PopularItem() {}

    public PopularItem(int rank, String sku, String name, int scanCount,
                       int windowSize, int slideInterval,
                       long windowStart, long windowEnd, Instant computedAt) {
        this.rank         = rank;
        this.sku          = sku;
        this.name         = name;
        this.scanCount    = scanCount;
        this.windowSize   = windowSize;
        this.slideInterval = slideInterval;
        this.windowStart  = windowStart;
        this.windowEnd    = windowEnd;
        this.computedAt   = computedAt;
    }

    // Getters
    public int     getRank()          { return rank; }
    public String  getSku()           { return sku; }
    public String  getName()          { return name; }
    public int     getScanCount()     { return scanCount; }
    public int     getWindowSize()    { return windowSize; }
    public int     getSlideInterval() { return slideInterval; }
    public long    getWindowStart()   { return windowStart; }
    public long    getWindowEnd()     { return windowEnd; }
    public Instant getComputedAt()    { return computedAt; }
}
