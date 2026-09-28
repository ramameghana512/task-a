package com.receipt.repository.database.jpa;

import com.receipt.model.LineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LineItemJpaRepository extends JpaRepository<LineItem, String> {

    List<LineItem> findByTransaction_Id(String transactionId);

    void deleteByTransaction_Id(String transactionId);

    // Explicit bulk DELETE — unambiguous and executes immediately when called
    // (clearAutomatically detaches any stale in-memory entities for these rows,
    // flushAutomatically forces the DELETE to hit the DB before any later insert).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM LineItem li WHERE li.transaction.id = :transactionId")
    void bulkDeleteByTransactionId(@Param("transactionId") String transactionId);
}
