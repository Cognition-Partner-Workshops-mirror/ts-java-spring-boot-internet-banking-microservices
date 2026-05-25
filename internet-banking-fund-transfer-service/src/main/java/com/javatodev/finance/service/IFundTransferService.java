package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.FundTransfer;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;

import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Interface for fund transfer operations (OCP + DIP).
 * Controllers depend on this interface, not the concrete FundTransferService class.
 */
public interface IFundTransferService {
    FundTransferResponse fundTransfer(FundTransferRequest request);
    List<FundTransfer> readAllTransfers(Pageable pageable);
}
