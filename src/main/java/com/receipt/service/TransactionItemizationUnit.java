package com.receipt.service;

import com.receipt.enums.ItemizeStatus;
import com.receipt.exception.TransactionNotFoundException;
import com.receipt.model.LineItem;
import com.receipt.model.Transaction;
import com.receipt.repository.LineItemRepository;
import com.receipt.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Executes the atomic unit of itemization work.
 * Lives in a separate bean so that @Transactional on replaceAndComplete is honoured
 * by Spring's AOP proxy (self-calls within the same class bypass the proxy).
 *
 * Clears existing line items, inserts the new ones, and updates itemize_status —
 * all inside ONE DB transaction. If anything throws, Spring rolls back everything;
 * the caller marks itemize_status = FAILED separately in its own auto-commit.
 */
@Service
public class TransactionItemizationUnit {

    private final TransactionRepository transactionRepository;
    private final LineItemRepository lineItemRepository;

    public TransactionItemizationUnit(TransactionRepository transactionRepository,
                                      LineItemRepository lineItemRepository) {
        this.transactionRepository = transactionRepository;
        this.lineItemRepository = lineItemRepository;
    }

    @Transactional
    public Transaction replaceAndComplete(String transactionId,
                                          List<LineItem> newItems,
                                          ItemizeStatus resultStatus) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        lineItemRepository.deleteByTransactionId(transactionId);

        for (LineItem item : newItems) {
            item.setTransaction(tx);
        }
        lineItemRepository.saveAll(newItems);

        transactionRepository.setItemizeStatus(transactionId, resultStatus.name());

        return transactionRepository.findByIdWithChildren(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }
}
