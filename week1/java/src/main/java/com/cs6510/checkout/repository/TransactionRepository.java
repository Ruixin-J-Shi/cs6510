package com.cs6510.checkout.repository;

import com.cs6510.checkout.model.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /**
     * Acquires a row-level write lock on the transaction before returning it.
     * Used by scan and complete to serialize concurrent requests on the same transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.transactionId = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") String id);

    /** Wipe all transactions — admin reset only. */
    @Modifying
    @Query(value = "TRUNCATE TABLE transactions CASCADE", nativeQuery = true)
    void truncateAll();
}
