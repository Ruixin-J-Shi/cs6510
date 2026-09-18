package com.cs6510.checkout.repository;

import com.cs6510.checkout.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, String> {

    /** Items currently below the given threshold. */
    List<Inventory> findByStockLessThan(int threshold);

    /**
     * Atomically decrements stock by {@code quantity} and marks low-stock
     * timestamp the first time the new stock falls below {@code threshold}.
     * The single UPDATE prevents negative-stock races.
     *
     * @return 1 if the decrement succeeded (stock was >= quantity), 0 otherwise.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE inventory
        SET stock        = stock - :quantity,
            low_stock_at = CASE
                WHEN (stock - :quantity) < :threshold AND low_stock_at IS NULL
                THEN NOW()
                ELSE low_stock_at
            END
        WHERE sku = :sku
          AND stock >= :quantity
        """, nativeQuery = true)
    int decrementStock(@Param("sku")       String sku,
                       @Param("quantity")  int    quantity,
                       @Param("threshold") int    threshold);

    /** Bulk stock reset — used by the admin reset endpoint. */
    @Modifying
    @Query(value = "UPDATE inventory SET stock = :stock, low_stock_at = NULL",
           nativeQuery = true)
    void resetAllStock(@Param("stock") int stock);
}
