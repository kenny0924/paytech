package com.paytech.reconciliation.app.ext.rest;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.statement.PlatformTxnApi;
import com.paytech.reconciliation.api.statement.response.PlatformTxnResponseBody;
import com.paytech.reconciliation.app.ext.rest.converter.ChannelStatementConverter;
import com.paytech.reconciliation.app.ext.rest.converter.ReconciliationWire;
import com.paytech.reconciliation.domainservice.service.ChannelStatementQueryService;

import lombok.AllArgsConstructor;

/** 平台流水查询的 HTTP 协议适配；业务日期区间按 UTC 自然日转为 [起日 00:00, 止日次日 00:00)。 */
@RestController
@AllArgsConstructor
public class PlatformTxnController implements PlatformTxnApi {
    private final ChannelStatementConverter converter;
    private final ChannelStatementQueryService channelStatementQueryService;

    /** 分页查询平台流水。 */
    @Override
    public PagingResponseBody<PlatformTxnResponseBody> listPlatformTxns(String clearingAccountId, String statementId,
        String status, String direction, String externalTransactionId, String businessDateFrom, String businessDateTo,
        Integer page, Integer size) {
        LocalDate to = ReconciliationWire.date(businessDateTo);
        return channelStatementQueryService.listPlatformTxns(ReconciliationWire.id(clearingAccountId),
            ReconciliationWire.id(statementId), ReconciliationWire.parse(PlatformTxnStatus.class, status),
            ReconciliationWire.parse(ClearingDirection.class, direction), externalTransactionId,
            ReconciliationWire.startOfDay(ReconciliationWire.date(businessDateFrom)),
            ReconciliationWire.startOfDay(to == null ? null : to.plusDays(1)), PagingRequestBody.of(page, size))
            .map(converter::toPlatformTxnResponseBody);
    }
}
