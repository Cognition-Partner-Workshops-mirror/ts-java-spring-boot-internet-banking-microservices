package com.javatodev.finance.common.service;

/**
 * Abstract template method for transaction processing (Phase 11: Template Method Pattern).
 * Both fund-transfer-service and utility-payment-service follow the same workflow:
 * 1. Create entity from request with initial status
 * 2. Save entity to database
 * 3. Call core banking via Feign
 * 4. Update entity with success status and transaction reference
 * 5. Build and return response
 *
 * @param <REQ>    The request type
 * @param <RES>    The response type
 * @param <ENTITY> The entity type
 */
public abstract class AbstractTransactionProcessingService<REQ, RES, ENTITY> {

    /**
     * Template method implementing the standard transaction processing workflow.
     */
    public RES processTransaction(REQ request) {
        ENTITY entity = createEntity(request);
        entity = saveInitial(entity);
        RES response = callCoreBanking(request);
        updateSuccess(entity, response);
        return buildResponse(entity, response);
    }

    /** Create entity from request with initial PENDING/PROCESSING status. */
    protected abstract ENTITY createEntity(REQ request);

    /** Persist the initial entity to the database. */
    protected abstract ENTITY saveInitial(ENTITY entity);

    /** Call core banking service via Feign client. */
    protected abstract RES callCoreBanking(REQ request);

    /** Update entity with SUCCESS status and transaction reference from core banking response. */
    protected abstract void updateSuccess(ENTITY entity, RES response);

    /** Build final response to return to the caller. */
    protected abstract RES buildResponse(ENTITY entity, RES response);
}
