package com.cs6510.checkout.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AppConfig {

    /** Low-stock threshold read from application.yml */
    @Value("${checkout.low-stock-threshold:50}")
    private int lowStockThreshold;

    @Value("${checkout.window-size:1000}")
    private int windowSize;

    @Value("${checkout.slide-interval:500}")
    private int slideInterval;

    public int getLowStockThreshold() { return lowStockThreshold; }
    public int getWindowSize()        { return windowSize; }
    public int getSlideInterval()     { return slideInterval; }

    @Bean(name = "analyticsExecutor")
    public Executor analyticsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setThreadNamePrefix("analytics-");
        executor.initialize();
        return executor;
    }

    /** Simple in-memory cache — used only for the static item catalog. */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("catalog");
    }
}
