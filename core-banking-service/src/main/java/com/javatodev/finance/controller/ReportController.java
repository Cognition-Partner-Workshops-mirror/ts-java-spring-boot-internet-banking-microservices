package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.report.AccountSummaryRow;
import com.javatodev.finance.model.dto.report.TransactionReportRow;
import com.javatodev.finance.service.ReportService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MVC controller that serves JSP-based custom report views.
 * Provides transaction reports and account summary reports with
 * optional query-parameter filters.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    /**
     * Renders the transaction report JSP page.
     * Supports optional filters: accountNumber, transactionType.
     */
    @GetMapping("/transactions")
    public String transactionReport(
            @RequestParam(value = "accountNumber", required = false) String accountNumber,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            Model model) {

        log.info("Generating transaction report - accountNumber={}, transactionType={}", accountNumber, transactionType);

        List<TransactionReportRow> rows = reportService.getTransactionReport(accountNumber, transactionType);

        // Populate model attributes consumed by the JSP view
        model.addAttribute("transactions", rows);
        model.addAttribute("selectedAccount", accountNumber);
        model.addAttribute("selectedType", transactionType);
        model.addAttribute("totalRecords", rows.size());

        return "reports/transactions";
    }

    /**
     * Renders the account summary report JSP page.
     * Supports optional filters: status, accountType.
     */
    @GetMapping("/accounts")
    public String accountSummaryReport(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "accountType", required = false) String accountType,
            Model model) {

        log.info("Generating account summary report - status={}, accountType={}", status, accountType);

        List<AccountSummaryRow> rows = reportService.getAccountSummaryReport(status, accountType);

        // Populate model attributes consumed by the JSP view
        model.addAttribute("accounts", rows);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedType", accountType);
        model.addAttribute("totalRecords", rows.size());

        return "reports/accounts";
    }

}
