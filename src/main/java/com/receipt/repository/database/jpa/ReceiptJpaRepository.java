package com.receipt.repository.database.jpa;

import com.receipt.model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptJpaRepository extends JpaRepository<Receipt, String> {
}
