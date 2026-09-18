package com.cs6510.checkout.controller;

import com.cs6510.checkout.dto.response.PopularItemsResponseDto;
import com.cs6510.checkout.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/popular-items")
    public ResponseEntity<PopularItemsResponseDto> popularItems(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getPopularItems(limit));
    }
}
