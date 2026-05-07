package com.citi.banking.service;

import com.citi.banking.model.CreditCard;
import com.citi.banking.repository.CreditCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;

    public Page<CreditCard> getAllCreditCards(Pageable pageable) {
        return creditCardRepository.findAll(pageable);
    }

    public CreditCard getCreditCardById(Long id) {
        return creditCardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Credit card not found with id: " + id));
    }

    public List<CreditCard> getCreditCardsByCustomerId(Long customerId) {
        return creditCardRepository.findByCustomerId(customerId);
    }

    public List<CreditCard> getCreditCardsByProduct(CreditCard.CardProduct product) {
        return creditCardRepository.findByCardProduct(product);
    }
}
