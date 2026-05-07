<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Transaction Report - Internet Banking</title>
    <style>
        /* Base styling for the transaction report page */
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
        .filter-panel {
            background: #fff; padding: 20px; border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px;
        }
        .filter-panel label { font-weight: bold; margin-right: 8px; }
        .filter-panel input, .filter-panel select {
            padding: 8px 12px; border: 1px solid #ccc; border-radius: 4px; margin-right: 16px;
        }
        .filter-panel button {
            padding: 8px 20px; background-color: #3498db; color: #fff;
            border: none; border-radius: 4px; cursor: pointer;
        }
        .filter-panel button:hover { background-color: #2980b9; }
        .summary { margin-bottom: 10px; color: #555; }
        table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        th { background-color: #3498db; color: #fff; padding: 12px 15px; text-align: left; }
        td { padding: 10px 15px; border-bottom: 1px solid #eee; }
        tr:hover { background-color: #f0f7ff; }
        .amount-negative { color: #e74c3c; }
        .amount-positive { color: #27ae60; }
        .nav-links { margin-bottom: 20px; }
        .nav-links a { margin-right: 16px; color: #3498db; text-decoration: none; }
        .nav-links a:hover { text-decoration: underline; }
        .export-links { margin-top: 15px; }
        .export-links a {
            display: inline-block; padding: 6px 14px; margin-right: 10px;
            background-color: #2ecc71; color: #fff; border-radius: 4px; text-decoration: none;
        }
        .export-links a:hover { background-color: #27ae60; }
    </style>
</head>
<body>

    <div class="nav-links">
        <a href="/reports/transactions">Transaction Report</a>
        <a href="/reports/accounts">Account Summary Report</a>
        <a href="/api/v1/tableau/wdc">Tableau WDC</a>
    </div>

    <h1>Transaction Report</h1>

    <!-- Filter panel for narrowing down report results -->
    <div class="filter-panel">
        <form method="get" action="/reports/transactions">
            <label for="accountNumber">Account Number:</label>
            <input type="text" id="accountNumber" name="accountNumber" value="${selectedAccount}" placeholder="e.g. 1234567890" />

            <label for="transactionType">Type:</label>
            <select id="transactionType" name="transactionType">
                <option value="">All</option>
                <option value="FUND_TRANSFER" ${selectedType == 'FUND_TRANSFER' ? 'selected' : ''}>Fund Transfer</option>
                <option value="UTILITY_PAYMENT" ${selectedType == 'UTILITY_PAYMENT' ? 'selected' : ''}>Utility Payment</option>
            </select>

            <button type="submit">Filter</button>
        </form>
    </div>

    <div class="summary">Total Records: <strong>${totalRecords}</strong></div>

    <!-- Tableau / CSV export links for the current filter set -->
    <div class="export-links">
        <a href="/api/v1/tableau/transactions.csv?accountNumber=${selectedAccount}&transactionType=${selectedType}">Export CSV</a>
        <a href="/api/v1/tableau/transactions.json?accountNumber=${selectedAccount}&transactionType=${selectedType}">Export JSON</a>
    </div>

    <br/>

    <table>
        <thead>
            <tr>
                <th>ID</th>
                <th>Transaction UUID</th>
                <th>Type</th>
                <th>Amount</th>
                <th>Account Number</th>
                <th>Reference Number</th>
            </tr>
        </thead>
        <tbody>
            <!-- Iterate over transaction rows provided by the controller -->
            <c:forEach var="txn" items="${transactions}">
                <tr>
                    <td>${txn.transactionId}</td>
                    <td>${txn.transactionUuid}</td>
                    <td>${txn.transactionType}</td>
                    <td class="${txn.amount < 0 ? 'amount-negative' : 'amount-positive'}">
                        <fmt:formatNumber value="${txn.amount}" type="currency" currencySymbol="$" />
                    </td>
                    <td>${txn.accountNumber}</td>
                    <td>${txn.referenceNumber}</td>
                </tr>
            </c:forEach>
            <c:if test="${empty transactions}">
                <tr><td colspan="6" style="text-align:center; color:#999;">No transactions found.</td></tr>
            </c:if>
        </tbody>
    </table>

</body>
</html>
