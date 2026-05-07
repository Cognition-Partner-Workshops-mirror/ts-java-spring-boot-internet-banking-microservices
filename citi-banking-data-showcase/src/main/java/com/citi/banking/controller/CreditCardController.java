package com.citi.banking.controller;

import com.citi.banking.model.CreditCard;
import com.citi.banking.service.CreditCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/credit-cards")
@RequiredArgsConstructor
@Tag(name = "Credit Cards", description = "Credit card management APIs — Citi card products")
public class CreditCardController {

    private final CreditCardService creditCardService;

    @GetMapping
    @Operation(summary = "Get all credit cards", description = "Returns paginated list of all credit cards")
    public ResponseEntity<Page<CreditCard>> getAllCreditCards(Pageable pageable) {
        return ResponseEntity.ok(creditCardService.getAllCreditCards(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get credit card by ID")
    public ResponseEntity<CreditCard> getCreditCardById(@PathVariable Long id) {
        return ResponseEntity.ok(creditCardService.getCreditCardById(id));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get credit cards for a customer")
    public ResponseEntity<List<CreditCard>> getCreditCardsByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(creditCardService.getCreditCardsByCustomerId(customerId));
    }

    @GetMapping("/product/{product}")
    @Operation(summary = "Get credit cards by product type", description = "Filter by CITI_DOUBLE_CASH, CITI_PREMIER, CITI_CUSTOM_CASH, COSTCO_ANYWHERE_VISA, etc.")
    public ResponseEntity<List<CreditCard>> getCreditCardsByProduct(@PathVariable CreditCard.CardProduct product) {
        return ResponseEntity.ok(creditCardService.getCreditCardsByProduct(product));
    }
}
