package com.cs6510.checkout.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "sku", length = 20)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private double price;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "low_stock_at")
    private Instant lowStockAt;

    public Inventory() {}

    // Getters
    public String getSku()          { return sku; }
    public String getName()         { return name; }
    public double getPrice()        { return price; }
    public int    getStock()        { return stock; }
    public Instant getLowStockAt()  { return lowStockAt; }

    // Setters
    public void setSku(String sku)              { this.sku = sku; }
    public void setName(String name)            { this.name = name; }
    public void setPrice(double price)          { this.price = price; }
    public void setStock(int stock)             { this.stock = stock; }
    public void setLowStockAt(Instant t)        { this.lowStockAt = t; }
}
