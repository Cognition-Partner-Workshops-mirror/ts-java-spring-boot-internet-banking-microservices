package com.citi.banking.repository;

import com.citi.banking.model.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {
    Optional<CreditCard> findByCardNumber(String cardNumber);
    List<CreditCard> findByCustomerId(Long customerId);
    List<CreditCard> findByCardProduct(CreditCard.CardProduct cardProduct);
    List<CreditCard> findByStatus(CreditCard.CardStatus status);
}
