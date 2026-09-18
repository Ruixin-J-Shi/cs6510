package com.cs6510.checkout.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transaction_items")
public class TransactionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;

    @Column(name = "sku", nullable = false, length = 20)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "unit_price", nullable = false)
    private double unitPrice;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt = Instant.now();

    public TransactionItem() {}

    public TransactionItem(String transactionId, String sku, String name, double unitPrice) {
        this.transactionId = transactionId;
        this.sku           = sku;
        this.name          = name;
        this.unitPrice     = unitPrice;
        this.scannedAt     = Instant.now();
    }

    // Getters
    public Long    getId()             { return id; }
    public String  getTransactionId()  { return transactionId; }
    public String  getSku()            { return sku; }
    public String  getName()           { return name; }
    public double  getUnitPrice()      { return unitPrice; }
    public Instant getScannedAt()      { return scannedAt; }
}
