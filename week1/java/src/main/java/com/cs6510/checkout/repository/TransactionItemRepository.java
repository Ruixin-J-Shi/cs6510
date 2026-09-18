package com.cs6510.checkout.repository;

import com.cs6510.checkout.model.TransactionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TransactionItemRepository extends JpaRepository<TransactionItem, Long> {

    List<TransactionItem> findByTransactionId(String transactionId);

    /** Wipe all transaction items — admin reset only (called before truncating transactions). */
    @Modifying
    @Query(value = "TRUNCATE TABLE transaction_items", nativeQuery = true)
    void truncateAll();
}
