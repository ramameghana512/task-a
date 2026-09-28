package com.receipt.repository.database.jpa;

import com.receipt.model.Tax;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxJpaRepository extends JpaRepository<Tax, String> {

    List<Tax> findByTransaction_Id(String transactionId);

    void deleteByTransaction_Id(String transactionId);
}
