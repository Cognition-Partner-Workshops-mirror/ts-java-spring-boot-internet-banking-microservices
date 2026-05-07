package com.javatodev.finance.controller;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.javatodev.finance.model.dto.report.AccountSummaryRow;
import com.javatodev.finance.model.dto.report.TransactionReportRow;
import com.javatodev.finance.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller providing Tableau integration endpoints.
 *
 * <p>Exposes JSON and CSV data feeds that Tableau can consume through its
 * Web Data Connector (WDC) or direct URL-based data source connections.
 * Also serves the WDC HTML page that Tableau Desktop/Server loads
 * to discover and fetch report data.</p>
 */
@Slf4j
@Tag(name = "Tableau Export Controller", description = "APIs for Tableau data integration and export")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tableau")
public class TableauExportController {

    private final ReportService reportService;

    // ------------------------------------------------------------------ JSON

    /**
     * Returns transaction report data as JSON for Tableau WDC consumption.
     */
    @Operation(summary = "Export Transactions JSON", description = "Returns transaction data as JSON for Tableau Web Data Connector")
    @GetMapping(value = "/transactions.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TransactionReportRow>> transactionsJson(
            @RequestParam(value = "accountNumber", required = false) String accountNumber,
            @RequestParam(value = "transactionType", required = false) String transactionType) {

        log.info("Tableau JSON export - transactions (accountNumber={}, transactionType={})", accountNumber, transactionType);
        List<TransactionReportRow> rows = reportService.getTransactionReport(accountNumber, transactionType);
        return ResponseEntity.ok(rows);
    }

    /**
     * Returns account summary report data as JSON for Tableau WDC consumption.
     */
    @Operation(summary = "Export Accounts JSON", description = "Returns account summary data as JSON for Tableau Web Data Connector")
    @GetMapping(value = "/accounts.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AccountSummaryRow>> accountsJson(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "accountType", required = false) String accountType) {

        log.info("Tableau JSON export - accounts (status={}, accountType={})", status, accountType);
        List<AccountSummaryRow> rows = reportService.getAccountSummaryReport(status, accountType);
        return ResponseEntity.ok(rows);
    }

    // ------------------------------------------------------------------- CSV

    /**
     * Returns transaction report data as a downloadable CSV file.
     * Tableau can connect to this URL directly as a web data source.
     */
    @Operation(summary = "Export Transactions CSV", description = "Returns transaction data as downloadable CSV for Tableau")
    @GetMapping(value = "/transactions.csv", produces = "text/csv")
    public ResponseEntity<String> transactionsCsv(
            @RequestParam(value = "accountNumber", required = false) String accountNumber,
            @RequestParam(value = "transactionType", required = false) String transactionType) throws Exception {

        log.info("Tableau CSV export - transactions (accountNumber={}, transactionType={})", accountNumber, transactionType);
        List<TransactionReportRow> rows = reportService.getTransactionReport(accountNumber, transactionType);

        // Build CSV with header from TransactionReportRow fields
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = csvMapper.schemaFor(TransactionReportRow.class).withHeader();
        String csv = csvMapper.writer(schema).writeValueAsString(rows);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions_report.csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    /**
     * Returns account summary report data as a downloadable CSV file.
     * Tableau can connect to this URL directly as a web data source.
     */
    @Operation(summary = "Export Accounts CSV", description = "Returns account summary data as downloadable CSV for Tableau")
    @GetMapping(value = "/accounts.csv", produces = "text/csv")
    public ResponseEntity<String> accountsCsv(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "accountType", required = false) String accountType) throws Exception {

        log.info("Tableau CSV export - accounts (status={}, accountType={})", status, accountType);
        List<AccountSummaryRow> rows = reportService.getAccountSummaryReport(status, accountType);

        // Build CSV with header from AccountSummaryRow fields
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = csvMapper.schemaFor(AccountSummaryRow.class).withHeader();
        String csv = csvMapper.writer(schema).writeValueAsString(rows);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"accounts_report.csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    // ------------------------------------------------------------- WDC page

    /**
     * Serves the Tableau Web Data Connector (WDC) HTML page.
     * Tableau Desktop or Tableau Server loads this URL to discover
     * available tables and fetch data via the WDC JavaScript API.
     */
    @Operation(summary = "Tableau WDC Page", description = "Serves the Web Data Connector HTML page for Tableau")
    @GetMapping(value = "/wdc", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> tableauWdcPage() {
        log.info("Serving Tableau WDC page");
        String html = buildWdcHtml();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    /**
     * Builds the Tableau WDC HTML page inline.
     * The page uses the Tableau WDC 2.x JavaScript SDK to register
     * two tables: banking_transactions and banking_accounts.
     */
    private String buildWdcHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Banking Reports - Tableau WDC</title>
                <meta charset="utf-8">
                <!-- Tableau WDC SDK v2 -->
                <script src="https://connectors.tableau.com/libs/tableauwdc-2.3.latest/js/tableauwdc-2.3.latest.min.js"></script>
            </head>
            <body>
                <h2>Internet Banking - Tableau Web Data Connector</h2>
                <p>Click <strong>Get Data</strong> in Tableau to load banking report data.</p>

                <script>
                (function() {
                    // Resolve the base URL dynamically from the page origin
                    var baseUrl = window.location.protocol + '//' + window.location.host;

                    var connector = tableau.makeConnector();

                    // Schema: defines the two tables Tableau can import
                    connector.getSchema = function(schemaCallback) {
                        var transactionCols = [
                            { id: 'transactionId',   alias: 'Transaction ID',   dataType: tableau.dataTypeEnum.int },
                            { id: 'transactionUuid', alias: 'Transaction UUID', dataType: tableau.dataTypeEnum.string },
                            { id: 'transactionType', alias: 'Type',             dataType: tableau.dataTypeEnum.string },
                            { id: 'amount',          alias: 'Amount',           dataType: tableau.dataTypeEnum.float },
                            { id: 'accountNumber',   alias: 'Account Number',   dataType: tableau.dataTypeEnum.string },
                            { id: 'referenceNumber', alias: 'Reference Number', dataType: tableau.dataTypeEnum.string }
                        ];

                        var accountCols = [
                            { id: 'accountId',        alias: 'Account ID',        dataType: tableau.dataTypeEnum.int },
                            { id: 'accountNumber',    alias: 'Account Number',    dataType: tableau.dataTypeEnum.string },
                            { id: 'accountType',      alias: 'Account Type',      dataType: tableau.dataTypeEnum.string },
                            { id: 'accountStatus',    alias: 'Status',            dataType: tableau.dataTypeEnum.string },
                            { id: 'availableBalance', alias: 'Available Balance', dataType: tableau.dataTypeEnum.float },
                            { id: 'actualBalance',    alias: 'Actual Balance',    dataType: tableau.dataTypeEnum.float },
                            { id: 'ownerName',        alias: 'Owner Name',        dataType: tableau.dataTypeEnum.string },
                            { id: 'ownerEmail',       alias: 'Owner Email',       dataType: tableau.dataTypeEnum.string }
                        ];

                        var transactionTable = { id: 'banking_transactions', alias: 'Banking Transactions', columns: transactionCols };
                        var accountTable     = { id: 'banking_accounts',     alias: 'Banking Accounts',     columns: accountCols };

                        schemaCallback([transactionTable, accountTable]);
                    };

                    // Data: fetches JSON from the Tableau export endpoints
                    connector.getData = function(table, doneCallback) {
                        var url;
                        if (table.tableInfo.id === 'banking_transactions') {
                            url = baseUrl + '/api/v1/tableau/transactions.json';
                        } else {
                            url = baseUrl + '/api/v1/tableau/accounts.json';
                        }

                        fetch(url)
                            .then(function(resp) { return resp.json(); })
                            .then(function(data) {
                                table.appendRows(data);
                                doneCallback();
                            });
                    };

                    tableau.registerConnector(connector);
                })();
                </script>
            </body>
            </html>
            """;
    }

}
