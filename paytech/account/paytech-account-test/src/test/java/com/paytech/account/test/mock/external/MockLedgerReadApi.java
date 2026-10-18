package com.paytech.account.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.test.mocker.external.LedgerBookMocker;
import com.paytech.ledger.api.LedgerReadApi;
import com.paytech.ledger.api.response.ChartAccountListResponseBody;
import com.paytech.ledger.api.response.LedgerAccountTotalsResponseBody;
import com.paytech.ledger.api.response.LedgerBookDetailResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承 ledger 内部读取合同（无 Context Path），由场景配置 DTO；account 不读科目批量接口。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockLedgerReadApi implements LedgerReadApi {
    private final LedgerBookMocker mocker;

    @Override
    public LedgerBookDetailResponseBody getLedgerBook(String bookId) {
        MockRequestId.mark(LedgerBookMocker.REQUEST_ID);
        return mocker.book(bookId);
    }

    @Override
    public ChartAccountListResponseBody listChartAccounts(String ids) {
        MockRequestId.mark(LedgerBookMocker.REQUEST_ID);
        throw mocker.unexpectedCall("listChartAccounts");
    }

    @Override
    public LedgerAccountTotalsResponseBody getAccountTotals(String bookId) {
        MockRequestId.mark(LedgerBookMocker.REQUEST_ID);
        return mocker.accountTotals(bookId);
    }
}
