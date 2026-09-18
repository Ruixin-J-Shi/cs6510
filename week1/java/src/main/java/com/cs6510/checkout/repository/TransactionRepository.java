package com.cs6510.checkout.repository;

import com.cs6510.checkout.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** Atomically increments itemCount and adds to runningTotal — avoids read-modify-write race. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Transaction t
        SET t.itemCount    = t.itemCount    + 1,
            t.runningTotal = t.runningTotal + :price
        WHERE t.transactionId = :id
        """)
    void incrementScan(String id, double price);

    /** Wipe all transactions — admin reset only. */
    @Modifying
    @Query(value = "TRUNCATE TABLE transactions CASCADE", nativeQuery = true)
    void truncateAll();
}
