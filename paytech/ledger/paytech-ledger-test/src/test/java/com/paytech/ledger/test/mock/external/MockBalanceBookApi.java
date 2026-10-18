package com.paytech.ledger.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.api.BalanceBookApi;
import com.paytech.account.api.request.RebuildBookBalancesRequestBody;
import com.paytech.account.api.response.BalanceConsistencyResponseBody;
import com.paytech.account.api.response.BalanceRebuildResponseBody;
import com.paytech.account.api.response.BookBalancesResponseBody;
import com.paytech.ledger.test.mocker.external.AccountConsistencyMocker;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承 account 正式合同（无 Context Path）；ledger 只调用 consistency，其余入口记为意外。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockBalanceBookApi implements BalanceBookApi {
    private final AccountConsistencyMocker mocker;

    @Override
    public BalanceConsistencyResponseBody getBalanceConsistency(String bookId) {
        MockRequestId.mark(AccountConsistencyMocker.REQUEST_ID);
        return mocker.answer(bookId);
    }

    @Override
    public BookBalancesResponseBody listBookBalances(String bookId) {
        MockRequestId.mark(AccountConsistencyMocker.REQUEST_ID);
        throw mocker.unexpectedCall("listBookBalances");
    }

    @Override
    public BalanceRebuildResponseBody rebuildBookBalances(String bookId, RebuildBookBalancesRequestBody body) {
        MockRequestId.mark(AccountConsistencyMocker.REQUEST_ID);
        throw mocker.unexpectedCall("rebuildBookBalances");
    }
}
