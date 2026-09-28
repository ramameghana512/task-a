package com.receipt.repository.database.jpa;

import com.receipt.model.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface TransactionJpaRepository extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByReceiptId(String receiptId);

    /**
     * Retry claim: FAILED → PROCESSING. Returns 1 if this thread won, 0 if another took it.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE transactions SET processing_status = 'PROCESSING', raw_ocr_text = :rawOcrText " +
                   "WHERE receipt_id = :receiptId AND processing_status = 'FAILED'", nativeQuery = true)
    int claimForRetry(@Param("receiptId") String receiptId, @Param("rawOcrText") String rawOcrText);

    /**
     * Marks a claimed transaction as processing-failed: PROCESSING → FAILED.
     * Called in catch block as its own auto-commit so it survives Tx-Work rollback.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE transactions SET processing_status = 'FAILED' " +
                   "WHERE id = :id AND processing_status = 'PROCESSING'", nativeQuery = true)
    int markProcessingFailed(@Param("id") String id);

    /**
     * Itemize claim: any status except ITEMIZING or PATCHING → ITEMIZING.
     * Excludes PATCHING so a concurrent manual patch blocks auto-itemize.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE transactions SET itemize_status = 'ITEMIZING' " +
                   "WHERE id = :id AND itemize_status NOT IN ('ITEMIZING', 'PATCHING')", nativeQuery = true)
    int claimForItemizing(@Param("id") String id);

    /**
     * Marks itemization as failed: ITEMIZING → FAILED.
     * Called in catch block as its own auto-commit so it survives Tx-Work rollback.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE transactions SET itemize_status = 'FAILED' " +
                   "WHERE id = :id AND itemize_status = 'ITEMIZING'", nativeQuery = true)
    int markItemizeFailed(@Param("id") String id);

    /**
     * Patch claim: any status except ITEMIZING or PATCHING → PATCHING.
     * Blocks concurrent patch calls and concurrent auto-itemize.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE transactions SET itemize_status = 'PATCHING' " +
                   "WHERE id = :id AND itemize_status NOT IN ('ITEMIZING', 'PATCHING')", nativeQuery = true)
    int claimForPatching(@Param("id") String id);

    /**
     * Marks patch as failed: PATCHING → FAILED.
     * Called in catch block as its own auto-commit so it survives Tx-Work rollback.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE transactions SET itemize_status = 'FAILED' " +
                   "WHERE id = :id AND itemize_status = 'PATCHING'", nativeQuery = true)
    int markPatchFailed(@Param("id") String id);

    /**
     * Fetches taxes and lineItems via @EntityGraph so Hibernate issues separate SELECTs
     * per collection instead of a single JOIN — avoids MultipleBagFetchException when
     * fetching two List<> associations simultaneously.
     */
    /**
     * Directly sets itemize_status for a transaction by native UPDATE.
     * Bypasses Hibernate dirty-checking — use this instead of entity save
     * whenever collection state changes are also in flight in the same @Transactional.
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE transactions SET itemize_status = :status WHERE id = :id", nativeQuery = true)
    int setItemizeStatus(@Param("id") String id, @Param("status") String status);

    @EntityGraph(attributePaths = {"taxes", "lineItems"})
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdWithChildren(@Param("id") String id);

    @EntityGraph(attributePaths = {"taxes", "lineItems"})
    @Query("SELECT t FROM Transaction t WHERE t.receiptId = :receiptId")
    Optional<Transaction> findByReceiptIdWithChildren(@Param("receiptId") String receiptId);
}
