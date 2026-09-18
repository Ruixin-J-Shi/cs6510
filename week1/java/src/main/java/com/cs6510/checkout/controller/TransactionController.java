package com.cs6510.checkout.controller;

import com.cs6510.checkout.dto.request.ScanItemRequest;
import com.cs6510.checkout.dto.request.StartTransactionRequest;
import com.cs6510.checkout.dto.response.ReceiptDto;
import com.cs6510.checkout.dto.response.ScanResultDto;
import com.cs6510.checkout.dto.response.TransactionDto;
import com.cs6510.checkout.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService txService;

    public TransactionController(TransactionService txService) {
        this.txService = txService;
    }

    @PostMapping
    public ResponseEntity<TransactionDto> startTransaction(
            @Valid @RequestBody StartTransactionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(txService.startTransaction(req));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ScanResultDto> scanItem(
            @PathVariable String id,
            @Valid @RequestBody ScanItemRequest req) {
        return ResponseEntity.ok(txService.scanItem(id, req));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ReceiptDto> completeTransaction(@PathVariable String id) {
        return ResponseEntity.ok(txService.completeTransaction(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDto> getTransaction(@PathVariable String id) {
        return ResponseEntity.ok(txService.getTransaction(id));
    }
}
