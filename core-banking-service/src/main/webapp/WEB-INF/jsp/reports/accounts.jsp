<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Account Summary Report - Internet Banking</title>
    <style>
        /* Base styling for the account summary report page */
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        h1 { color: #2c3e50; border-bottom: 2px solid #e67e22; padding-bottom: 10px; }
        .filter-panel {
            background: #fff; padding: 20px; border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px;
        }
        .filter-panel label { font-weight: bold; margin-right: 8px; }
        .filter-panel select {
            padding: 8px 12px; border: 1px solid #ccc; border-radius: 4px; margin-right: 16px;
        }
        .filter-panel button {
            padding: 8px 20px; background-color: #e67e22; color: #fff;
            border: none; border-radius: 4px; cursor: pointer;
        }
        .filter-panel button:hover { background-color: #d35400; }
        .summary { margin-bottom: 10px; color: #555; }
        table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        th { background-color: #e67e22; color: #fff; padding: 12px 15px; text-align: left; }
        td { padding: 10px 15px; border-bottom: 1px solid #eee; }
        tr:hover { background-color: #fef5e7; }
        .status-active { color: #27ae60; font-weight: bold; }
        .status-dormant { color: #f39c12; font-weight: bold; }
        .status-blocked { color: #e74c3c; font-weight: bold; }
        .status-pending { color: #3498db; font-weight: bold; }
        .nav-links { margin-bottom: 20px; }
        .nav-links a { margin-right: 16px; color: #e67e22; text-decoration: none; }
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

    <h1>Account Summary Report</h1>

    <!-- Filter panel for narrowing down report results -->
    <div class="filter-panel">
        <form method="get" action="/reports/accounts">
            <label for="status">Status:</label>
            <select id="status" name="status">
                <option value="">All</option>
                <option value="ACTIVE" ${selectedStatus == 'ACTIVE' ? 'selected' : ''}>Active</option>
                <option value="PENDING" ${selectedStatus == 'PENDING' ? 'selected' : ''}>Pending</option>
                <option value="DORMANT" ${selectedStatus == 'DORMANT' ? 'selected' : ''}>Dormant</option>
                <option value="BLOCKED" ${selectedStatus == 'BLOCKED' ? 'selected' : ''}>Blocked</option>
            </select>

            <label for="accountType">Account Type:</label>
            <select id="accountType" name="accountType">
                <option value="">All</option>
                <option value="SAVINGS_ACCOUNT" ${selectedType == 'SAVINGS_ACCOUNT' ? 'selected' : ''}>Savings Account</option>
                <option value="FIXED_DEPOSIT" ${selectedType == 'FIXED_DEPOSIT' ? 'selected' : ''}>Fixed Deposit</option>
                <option value="LOAN_ACCOUNT" ${selectedType == 'LOAN_ACCOUNT' ? 'selected' : ''}>Loan Account</option>
            </select>

            <button type="submit">Filter</button>
        </form>
    </div>

    <div class="summary">Total Records: <strong>${totalRecords}</strong></div>

    <!-- Tableau / CSV export links for the current filter set -->
    <div class="export-links">
        <a href="/api/v1/tableau/accounts.csv?status=${selectedStatus}&accountType=${selectedType}">Export CSV</a>
        <a href="/api/v1/tableau/accounts.json?status=${selectedStatus}&accountType=${selectedType}">Export JSON</a>
    </div>

    <br/>

    <table>
        <thead>
            <tr>
                <th>ID</th>
                <th>Account Number</th>
                <th>Type</th>
                <th>Status</th>
                <th>Available Balance</th>
                <th>Actual Balance</th>
                <th>Owner</th>
                <th>Email</th>
            </tr>
        </thead>
        <tbody>
            <!-- Iterate over account summary rows provided by the controller -->
            <c:forEach var="acct" items="${accounts}">
                <tr>
                    <td>${acct.accountId}</td>
                    <td>${acct.accountNumber}</td>
                    <td>${acct.accountType}</td>
                    <td class="status-${acct.accountStatus.toLowerCase()}">${acct.accountStatus}</td>
                    <td><fmt:formatNumber value="${acct.availableBalance}" type="currency" currencySymbol="$" /></td>
                    <td><fmt:formatNumber value="${acct.actualBalance}" type="currency" currencySymbol="$" /></td>
                    <td>${acct.ownerName}</td>
                    <td>${acct.ownerEmail}</td>
                </tr>
            </c:forEach>
            <c:if test="${empty accounts}">
                <tr><td colspan="8" style="text-align:center; color:#999;">No accounts found.</td></tr>
            </c:if>
        </tbody>
    </table>

</body>
</html>
