package com.paytech.ledger.auto.ext.http.query;

import static com.paytech.ledger.test.builder.EconomicComponentBuilder.channelCost;
import static com.paytech.ledger.test.builder.EconomicComponentBuilder.clientFee;
import static com.paytech.ledger.test.builder.EconomicComponentBuilder.clientPrincipal;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.api.response.ChartAccountListResponseBody;
import com.paytech.ledger.api.response.LedgerAccountTotalResponseBody;
import com.paytech.ledger.api.response.LedgerAccountTotalsResponseBody;
import com.paytech.ledger.api.response.LedgerBookDetailResponseBody;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

/**
 * 供 account 读取的内部接口（design/04 §8）：账簿身份、按主键批量读科目、Owner 账户按分录聚合的余额
 * <li>account-totals 的数据经真实入口形成：扮演 billing 发布 PAYMENT 经济事实，由 ledger 消费过账（三服务链路的 ledger 段，design/10 §2.5）；
 * account 段在 account-test 以同一 Payload 消费并与这里的分录聚合值核对。</li>
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

    /** PAYMENT 事实过账后，account-totals 只返回 Owner 账户（Client 应付、渠道应收与预提），经营主体收入与费用账户不返回。 */
    @Test
    void test_accountTotalsAggregateOwnerJournals() {
        postFact(fact(payment("100"), clientPrincipal("95", CLIENT_ID), clientFee("5", ENTITY_NO), channelCost("2")));

        var totals = get("/ledger/v1/books/" + oeLedger.getLedgerBookId() + "/account-totals").assertSuccess(200)
            .as(LedgerAccountTotalsResponseBody.class);

        assertEquals(List.of(StandardAccountCodes.CHANNEL_FEE_ACCRUAL, StandardAccountCodes.CHANNEL_UNSETTLED_RECEIVABLE,
                StandardAccountCodes.CLIENT_PAYABLE),
            totals.getItems().stream().map(LedgerAccountTotalResponseBody::getAccountCode).toList(), "按科目代码排序");
        assertTotal(totals.getItems().get(0), OwnerType.CHANNEL_ACCOUNT, String.valueOf(CHANNEL_ACCOUNT_ID),
            BalanceBucket.NONE, "0.00", "2.00", "2.00");
        assertTotal(totals.getItems().get(1), OwnerType.CHANNEL_ACCOUNT, String.valueOf(CHANNEL_ACCOUNT_ID),
            BalanceBucket.NONE, "100.00", "0.00", "100.00");
        assertTotal(totals.getItems().get(2), OwnerType.CLIENT, String.valueOf(CLIENT_ID), BalanceBucket.PENDING,
            "0.00", "95.00", "95.00");
        get("/ledger/v1/books/123456789/account-totals").assertBusinessRejected("账簿不存在: 123456789");
    }

    /** 断言一个 Owner 账户的聚合余额。 */
    void assertTotal(LedgerAccountTotalResponseBody item, OwnerType ownerType, String ownerId, BalanceBucket bucket,
        String debit, String credit, String net) {
        assertAll(item.getAccountCode(), () -> assertEquals(ownerType.getCode(), item.getOwnerType()),
            () -> assertEquals(ownerId, item.getOwnerId()), () -> assertEquals(bucket.getCode(), item.getBalanceBucket()),
            () -> assertEquals(debit, item.getDebitTotal()), () -> assertEquals(credit, item.getCreditTotal()),
            () -> assertEquals(net, item.getNetBalance()), () -> assertEquals("1", item.getLastJournalSequence()));
    }
}
