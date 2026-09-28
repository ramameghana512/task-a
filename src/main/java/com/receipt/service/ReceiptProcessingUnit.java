package com.receipt.service;

import com.receipt.enums.ProcessingStatus;
import com.receipt.exception.ReceiptProcessingException;
import com.receipt.model.LineItem;
import com.receipt.model.Tax;
import com.receipt.model.Transaction;
import com.receipt.parser.ParsedDocument;
import com.receipt.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes the atomic unit of receipt processing work.
 * Lives in a separate bean so that @Transactional on saveAndComplete is honoured
 * by Spring's AOP proxy (self-calls within the same class bypass the proxy).
 *
 * Loads the stub transaction created during claim, fills it with parsed data,
 * and marks it PROCESSED — all inside ONE DB transaction.
 * If anything throws, Spring rolls back everything; the caller marks FAILED separately.
 */
@Service
public class ReceiptProcessingUnit {

    private final TransactionRepository transactionRepository;

    public ReceiptProcessingUnit(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction saveAndComplete(String transactionId, ParsedDocument parsed) {
        Transaction tx = transactionRepository.findByIdWithChildren(transactionId)
                .orElseThrow(() -> new ReceiptProcessingException(
                        "Stub transaction not found: " + transactionId));

        tx.setMerchant(parsed.getMerchant());
        tx.setTransactionDate(parsed.getTransactionDate());
        tx.setCurrency(parsed.getCurrency());
        tx.setTotal(parsed.getTotal());
        tx.setItemizeStatus(parsed.getItemizeStatus());
        tx.setProcessingStatus(ProcessingStatus.PROCESSED);

        tx.getTaxes().clear();
        for (Tax tax : parsed.getTaxes()) {
            tax.setTransaction(tx);
            tx.getTaxes().add(tax);
        }

        tx.getLineItems().clear();
        for (LineItem lineItem : parsed.getLineItems()) {
            lineItem.setTransaction(tx);
            tx.getLineItems().add(lineItem);
        }

        transactionRepository.save(tx);
        return tx;
    }
}
