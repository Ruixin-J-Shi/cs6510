package com.cs6510.checkout.service;

import com.cs6510.checkout.dto.request.ScanItemRequest;
import com.cs6510.checkout.dto.request.StartTransactionRequest;
import com.cs6510.checkout.dto.response.*;
import com.cs6510.checkout.exception.*;
import com.cs6510.checkout.model.*;
import com.cs6510.checkout.repository.*;
import com.cs6510.checkout.config.AppConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository     txRepo;
    private final TransactionItemRepository txItemRepo;
    private final InventoryRepository       inventoryRepo;
    private final AnalyticsService          analyticsService;
    private final int                       lowStockThreshold;

    public TransactionService(TransactionRepository txRepo,
                              TransactionItemRepository txItemRepo,
                              InventoryRepository inventoryRepo,
                              AnalyticsService analyticsService,
                              AppConfig config) {
        this.txRepo            = txRepo;
        this.txItemRepo        = txItemRepo;
        this.inventoryRepo     = inventoryRepo;
        this.analyticsService  = analyticsService;
        this.lowStockThreshold = config.getLowStockThreshold();
    }

    // -------------------------------------------------------------------------
    // POST /transactions
    // -------------------------------------------------------------------------

    @Transactional
    public TransactionDto startTransaction(StartTransactionRequest req) {
        Transaction tx = new Transaction();
        tx.setTransactionId("tx-" + UUID.randomUUID());
        tx.setStationId(req.stationId());
        tx.setStatus("OPEN");
        tx.setItemCount(0);
        tx.setRunningTotal(0.0);
        tx.setStartedAt(Instant.now());
        txRepo.save(tx);
        return toDto(tx);
    }

    // -------------------------------------------------------------------------
    // POST /transactions/{id}/items
    // -------------------------------------------------------------------------

    @Transactional
    public ScanResultDto scanItem(String transactionId, ScanItemRequest req) {
        Transaction tx = txRepo.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (!"OPEN".equals(tx.getStatus())) {
            throw new TransactionNotOpenException(transactionId);
        }

        Inventory item = inventoryRepo.findById(req.sku())
                .orElseThrow(() -> new ItemNotFoundException(req.sku()));

        // Persist the scanned item.
        txItemRepo.save(new TransactionItem(transactionId, item.getSku(), item.getName(), item.getPrice()));

        // Each transaction belongs to exactly one station (one thread), so a
        // read-modify-write is safe — no concurrent scan for the same txId.
        tx.setItemCount(tx.getItemCount() + 1);
        tx.setRunningTotal(Math.round((tx.getRunningTotal() + item.getPrice()) * 100.0) / 100.0);
        txRepo.save(tx);

        // Record in the analytics sliding window (triggers async persist every slideInterval).
        analyticsService.recordScan(req.sku());

        return new ScanResultDto(
                transactionId,
                item.getSku(),
                item.getName(),
                item.getPrice(),
                tx.getItemCount(),
                tx.getRunningTotal());
    }

    // -------------------------------------------------------------------------
    // POST /transactions/{id}/complete
    // -------------------------------------------------------------------------

    @Transactional
    public ReceiptDto completeTransaction(String transactionId) {
        Transaction tx = txRepo.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (!"OPEN".equals(tx.getStatus())) {
            throw new TransactionNotOpenException(transactionId);
        }

        List<TransactionItem> items = txItemRepo.findByTransactionId(transactionId);
        if (items.isEmpty()) {
            throw new EmptyBasketException(transactionId);
        }

        // Group into (sku -> total quantity) and sort by SKU to prevent deadlocks
        // when two transactions compete to decrement the same SKUs.
        Map<String, Integer> qtyBySku = new TreeMap<>();
        for (TransactionItem item : items) {
            qtyBySku.merge(item.getSku(), 1, Integer::sum);
        }

        // Atomic decrement per SKU — sorted order prevents deadlock.
        for (Map.Entry<String, Integer> entry : qtyBySku.entrySet()) {
            int updated = inventoryRepo.decrementStock(
                    entry.getKey(), entry.getValue(), lowStockThreshold);
            if (updated == 0) {
                throw new InsufficientStockException(entry.getKey());
            }
        }

        // Mark transaction completed.
        Instant now = Instant.now();
        tx.setStatus("COMPLETED");
        tx.setCompletedAt(now);
        txRepo.save(tx);

        // Build receipt lines (one per unique SKU, with quantity).
        List<ReceiptLineDto> lines = qtyBySku.entrySet().stream()
                .map(e -> {
                    TransactionItem sample = items.stream()
                            .filter(i -> i.getSku().equals(e.getKey()))
                            .findFirst().orElseThrow();
                    return new ReceiptLineDto(
                            sample.getSku(), sample.getName(),
                            sample.getUnitPrice(), e.getValue());
                })
                .toList();

        return new ReceiptDto(
                tx.getTransactionId(),
                tx.getStationId(),
                tx.getItemCount(),
                tx.getRunningTotal(),
                tx.getStartedAt(),
                now,
                lines);
    }

    // -------------------------------------------------------------------------
    // GET /transactions/{id}  (debug / instructor only)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TransactionDto getTransaction(String transactionId) {
        return txRepo.findById(transactionId)
                .map(this::toDto)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TransactionDto toDto(Transaction tx) {
        return new TransactionDto(
                tx.getTransactionId(), tx.getStationId(), tx.getStatus(),
                tx.getItemCount(), tx.getRunningTotal(), tx.getStartedAt());
    }
}
