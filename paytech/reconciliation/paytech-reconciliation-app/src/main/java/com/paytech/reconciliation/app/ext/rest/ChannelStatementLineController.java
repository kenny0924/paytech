package com.paytech.reconciliation.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.statement.ChannelStatementLineApi;
import com.paytech.reconciliation.api.statement.response.ChannelStatementLineSearchItemResponseBody;
import com.paytech.reconciliation.app.ext.rest.converter.ChannelStatementConverter;
import com.paytech.reconciliation.app.ext.rest.converter.ReconciliationWire;
import com.paytech.reconciliation.domain.ChannelLineSearch;
import com.paytech.reconciliation.domainservice.service.ChannelStatementQueryService;

import lombok.AllArgsConstructor;

/** 跨账单查询渠道账单行的 HTTP 协议适配，委托查询 Service。 */
@RestController
@AllArgsConstructor
public class ChannelStatementLineController implements ChannelStatementLineApi {
    private final ChannelStatementConverter converter;
    private final ChannelStatementQueryService channelStatementQueryService;

    /** 跨账单分页查询账单行。 */
    @Override
    public PagingResponseBody<ChannelStatementLineSearchItemResponseBody> searchChannelStatementLines(
        String clearingAccountId, String statementStatus, String matchStatus, String lineType, String referenceType,
        String externalTransactionId, String transactionDateFrom, String transactionDateTo, Integer page, Integer size) {
        ChannelLineSearch criteria = ChannelLineSearch.builder().clearingAccountId(ReconciliationWire.id(clearingAccountId))
            .statementStatus(ReconciliationWire.parse(ChannelStatementStatus.class, statementStatus))
            .matchStatus(ReconciliationWire.parse(LineMatchStatus.class, matchStatus))
            .lineType(ReconciliationWire.parse(ChannelLineType.class, lineType)).referenceType(referenceType)
            .externalTransactionId(externalTransactionId)
            .transactionDateFrom(ReconciliationWire.date(transactionDateFrom))
            .transactionDateTo(ReconciliationWire.date(transactionDateTo)).build();
        return channelStatementQueryService.searchLines(criteria, PagingRequestBody.of(page, size))
            .map(converter::toSearchItemBody);
    }
}
