package com.cs6510.checkout.repository;

import com.cs6510.checkout.model.PopularItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PopularItemRepository extends JpaRepository<PopularItem, Integer> {

    /** Ordered by rank ascending (rank 1 = most popular). */
    List<PopularItem> findAllByOrderByRankAsc();

    /** Replace the entire snapshot atomically. */
    @Modifying
    @Query(value = "TRUNCATE TABLE popular_items", nativeQuery = true)
    void truncateAll();
}
