package com.cs6510.checkout.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "transaction_id", length = 50)
    private String transactionId;

    @Column(name = "station_id", nullable = false, length = 50)
    private String stationId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";   // OPEN | COMPLETED | CANCELLED

    @Column(name = "item_count", nullable = false)
    private int itemCount = 0;

    @Column(name = "running_total", nullable = false)
    private double runningTotal = 0.0;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public Transaction() {}

    // Getters
    public String  getTransactionId()  { return transactionId; }
    public String  getStationId()      { return stationId; }
    public String  getStatus()         { return status; }
    public int     getItemCount()      { return itemCount; }
    public double  getRunningTotal()   { return runningTotal; }
    public Instant getStartedAt()      { return startedAt; }
    public Instant getCompletedAt()    { return completedAt; }

    // Setters
    public void setTransactionId(String id)   { this.transactionId = id; }
    public void setStationId(String sid)      { this.stationId = sid; }
    public void setStatus(String s)           { this.status = s; }
    public void setItemCount(int n)           { this.itemCount = n; }
    public void setRunningTotal(double t)     { this.runningTotal = t; }
    public void setStartedAt(Instant t)       { this.startedAt = t; }
    public void setCompletedAt(Instant t)     { this.completedAt = t; }
}
