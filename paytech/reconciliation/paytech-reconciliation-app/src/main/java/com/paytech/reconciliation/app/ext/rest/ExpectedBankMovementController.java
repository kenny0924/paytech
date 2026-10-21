package com.paytech.reconciliation.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.bank.ExpectedBankMovementApi;
import com.paytech.reconciliation.api.bank.response.ExpectedBankMovementResponseBody;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.app.ext.rest.converter.BankReconciliationConverter;
import com.paytech.reconciliation.app.ext.rest.converter.ReconciliationWire;
import com.paytech.reconciliation.domainservice.service.BankReconciliationQueryService;
import lombok.AllArgsConstructor;

/** 预期银行流水查询的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class ExpectedBankMovementController implements ExpectedBankMovementApi {
    private final BankReconciliationConverter converter;
    private final BankReconciliationQueryService bankReconciliationQueryService;

    /** 分页查询预期银行流水。 */
    @Override
    public PagingResponseBody<ExpectedBankMovementResponseBody> listExpectedBankMovements(String cashAccountId,
        String movementType, String sourceId, String status, Integer page, Integer size) {
        return bankReconciliationQueryService.listExpectedMovements(ReconciliationWire.id(cashAccountId),
            ReconciliationWire.parse(ExpectedMovementType.class, movementType), ReconciliationWire.id(sourceId),
            ReconciliationWire.parse(ExpectedMovementStatus.class, status), PagingRequestBody.of(page, size))
            .map(converter::toMovementBody);
    }
}
