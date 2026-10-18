package com.paytech.account.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.test.mocker.external.LedgerBookMocker;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.LedgerBookQueryApi;
import com.paytech.ledger.api.response.AccountJournalLineResponseBody;
import com.paytech.ledger.api.response.AccountResponseBody;
import com.paytech.ledger.api.response.JournalEntryDetailResponseBody;
import com.paytech.ledger.api.response.LedgerBookResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承 ledger 账本查询合同（无 Context Path）；account 只按主体查账簿，其余入口记为意外。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockLedgerBookQueryApi implements LedgerBookQueryApi {
    private final LedgerBookMocker mocker;

    @Override
    public PagingResponseBody<LedgerBookResponseBody> listLedgerBooks(String entityType, String entityId,
        String currency, Integer page, Integer size) {
        MockRequestId.mark(LedgerBookMocker.REQUEST_ID);
        return mocker.listBooks(entityType, entityId, currency);
    }

    @Override
    public PagingResponseBody<AccountResponseBody> listAccounts(String ledgerBookId, String accountCode,
        String ownerType, String ownerId, String balanceBucket, Integer page, Integer size) {
        MockRequestId.mark(LedgerBookMocker.REQUEST_ID);
        throw mocker.unexpectedCall("listAccounts");
    }

    @Override
    public PagingResponseBody<AccountJournalLineResponseBody> listAccountJournalLines(String ledgerBookId,
        String accountId, String postingDateFrom, String postingDateTo, Integer page, Integer size) {
        MockRequestId.mark(LedgerBookMocker.REQUEST_ID);
        throw mocker.unexpectedCall("listAccountJournalLines");
    }

    @Override
    public JournalEntryDetailResponseBody getJournalEntry(String ledgerBookId, String journalEntryId) {
        MockRequestId.mark(LedgerBookMocker.REQUEST_ID);
        throw mocker.unexpectedCall("getJournalEntry");
    }
}
