package com.paytech.finance.ext.rest.accounting;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.finance.accounting.domainservice.service.LedgerQueryService;
import com.paytech.finance.api.accounting.LedgerBookQueryApi;
import com.paytech.finance.api.accounting.response.AccountJournalLineResponseBody;
import com.paytech.finance.api.accounting.response.AccountResponseBody;
import com.paytech.finance.api.accounting.response.JournalEntryDetailResponseBody;
import com.paytech.finance.api.accounting.response.LedgerBookResponseBody;
import com.paytech.finance.ext.rest.accounting.converter.LedgerQueryConverter;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 账本查询的 HTTP 协议适配；简单只读查询直接委托 LedgerQueryService。 */
@RestController
@AllArgsConstructor
public class LedgerBookQueryController implements LedgerBookQueryApi {
    private final LedgerQueryService ledgerQueryService;
    private final LedgerQueryConverter converter;

    /** 分页查询账簿。 */
    @Override
    public PagingResponseBody<LedgerBookResponseBody> listLedgerBooks(String entityType, String entityId,
        String currency, Integer page, Integer size) {
        return ledgerQueryService.pageLedgerBooks(converter.toLedgerBookQuery(entityType, entityId, currency, page, size))
            .map(converter::toResponseBody);
    }

    /** 分页查询账户。 */
    @Override
    public PagingResponseBody<AccountResponseBody> listAccounts(String ledgerBookId, String accountCode,
        String ownerType, String ownerId, String balanceBucket, Integer page, Integer size) {
        return ledgerQueryService.pageAccounts(converter.toAccountQuery(ledgerBookId, accountCode, ownerType, ownerId,
            balanceBucket, PagingRequestBody.of(page, size))).map(converter::toResponseBody);
    }

    /** 分页查询账户流水。 */
    @Override
    public PagingResponseBody<AccountJournalLineResponseBody> listAccountJournalLines(String ledgerBookId,
        String accountId, String postingDateFrom, String postingDateTo, Integer page, Integer size) {
        LocalDate from = converter.toDate("postingDateFrom", postingDateFrom);
        LocalDate to = converter.toDate("postingDateTo", postingDateTo);
        converter.checkDateRange(from, to);
        return ledgerQueryService.pageAccountJournalLines(Long.parseLong(ledgerBookId), Long.parseLong(accountId), from,
            to, PagingRequestBody.of(page, size)).map(converter::toResponseBody);
    }

    /** 查询分录详情。 */
    @Override
    public JournalEntryDetailResponseBody getJournalEntry(String ledgerBookId, String journalEntryId) {
        return converter.toResponseBody(
            ledgerQueryService.getJournalEntry(Long.parseLong(ledgerBookId), Long.parseLong(journalEntryId)));
    }
}
