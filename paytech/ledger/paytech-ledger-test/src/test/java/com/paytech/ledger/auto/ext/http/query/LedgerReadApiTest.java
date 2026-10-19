package com.paytech.ledger.auto.ext.http.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.ledger.api.response.ChartAccountListResponseBody;
import com.paytech.ledger.api.response.LedgerBookDetailResponseBody;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

/**
 * 内部读取接口（design/04 §8）：账簿身份、按主键批量读科目
 * <li>原 account-totals（供 account 核对 Owner 余额）已随 account 明细账重构删除。</li>
 *
 * @see com.paytech.ledger.api.LedgerReadApi
 */
class LedgerReadApiTest extends AbstractTransactionPostingTest {
    /** 初始化 DML 中 CLIENT_PAYABLE 与 CHANNEL_UNSETTLED_RECEIVABLE 科目的固定主键。 */
    private static final long CLIENT_PAYABLE_ID = 3009L;

    private static final long CHANNEL_UNSETTLED_RECEIVABLE_ID = 3001L;

    @Test
    void test_getLedgerBook() {
        var book = get("/ledger/v1/books/" + oeLedger.getLedgerBookId()).assertSuccess(200)
            .as(LedgerBookDetailResponseBody.class);

        assertAll(() -> assertEquals(String.valueOf(oeLedger.getLedgerBookId()), book.getLedgerBookId()),
            () -> assertEquals(LedgerEntityType.OPERATING_ENTITY.getCode(), book.getEntityType()),
            () -> assertEquals(ENTITY_NO, book.getEntityId()), () -> assertEquals(CURRENCY, book.getCurrency()),
            () -> assertEquals(String.valueOf(LedgerMocker.STANDARD_CHART_ID), book.getChartId()),
            () -> assertEquals("STANDARD", book.getChartCode()), () -> assertEquals("OPEN", book.getBookStatus()));
        get("/ledger/v1/books/123456789").assertBusinessRejected("账簿不存在: 123456789");
    }

    @Test
    void test_listChartAccountsByIds() {
        var list = get("/ledger/v1/chart-accounts?ids=" + CLIENT_PAYABLE_ID + "," + CHANNEL_UNSETTLED_RECEIVABLE_ID
            + ",999999").assertSuccess(200).as(ChartAccountListResponseBody.class);

        assertEquals(List.of(String.valueOf(CHANNEL_UNSETTLED_RECEIVABLE_ID), String.valueOf(CLIENT_PAYABLE_ID)),
            list.getItems().stream().map(item -> item.getChartAccountId()).toList(), "按主键升序，不存在的主键不返回");
        var clientPayable = list.getItems().get(1);
        assertAll(() -> assertEquals(StandardAccountCodes.CLIENT_PAYABLE, clientPayable.getAccountCode()),
            () -> assertEquals("LIABILITY", clientPayable.getAccountClass()),
            () -> assertEquals("CREDIT", clientPayable.getNormalSide()),
            () -> assertEquals(Boolean.TRUE, clientPayable.getPostingAllowed()));
        get("/ledger/v1/chart-accounts?ids=1,abc").assertStatus(400);
    }
}
