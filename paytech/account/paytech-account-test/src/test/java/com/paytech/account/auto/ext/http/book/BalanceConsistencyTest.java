package com.paytech.account.auto.ext.http.book;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.response.BalanceConsistencyResponseBody;
import com.paytech.account.api.response.BookBalancesResponseBody;
import com.paytech.account.api.response.OwnerBalanceCountResponseBody;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.fact.EconomicFactPayload;

/**
 * 账簿维度的余额消费进度与余额列表（design/04 §6、§8，ledger 关账步骤 2、3 的 account 侧）
 * <li>未追平：尚未消费任何事实时 maxAppliedFactId 为空，ledger 据此与 billing 已发布的最大 factId 比对后失败关闭（ledger-test 覆盖比对）。</li>
 * <li>已追平：消费后报告已处理事实数、最大 factId 与按 Owner 类型的非零余额数；Finance Entity 账簿按事实头的 financeEntityId 统计事实。</li>
 * <li>账簿身份经 ledger-api 读取：账簿不存在时 400，ledger 不可用时 503（失败关闭）。</li>
 *
 * @see com.paytech.account.api.BalanceBookApi
 */
class BalanceConsistencyTest extends AbstractBalanceTest {

    @Test
    void test_consistencyBeforeAndAfterConsumption() {
        BalanceConsistencyResponseBody before = consistency(OE_BOOK_ID);
        assertAll(() -> assertEquals("0", before.getAppliedFactCount()), () -> assertNull(before.getMaxAppliedFactId()),
            () -> assertEquals(Map.of("CLIENT", "0", "LOCAL_PARTNER", "0", "CHANNEL_ACCOUNT", "0", "CASH_ACCOUNT", "0"),
                counts(before)));

        EconomicFactPayload fact = payment("100").partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, FINANCE_ENTITY_ID)
            .component(AmountSource.CLIENT_PRINCIPAL, "95").component(AmountSource.CHANNEL_COST, "2")
            .component(AmountSource.PARTNER_SHARE, "1").build();
        consumed(fact);

        BalanceConsistencyResponseBody after = consistency(OE_BOOK_ID);
        assertAll(() -> assertEquals(String.valueOf(OE_BOOK_ID), after.getLedgerBookId()),
            () -> assertEquals("OPERATING_ENTITY", after.getBookEntityType()),
            () -> assertEquals(ENTITY_NO, after.getBookEntityId()), () -> assertEquals(CURRENCY, after.getCurrency()),
            () -> assertEquals("1", after.getAppliedFactCount()),
            () -> assertEquals(fact.getFactId().toString(), after.getMaxAppliedFactId()),
            () -> assertEquals(Map.of("CLIENT", "1", "LOCAL_PARTNER", "1", "CHANNEL_ACCOUNT", "2", "CASH_ACCOUNT", "0"),
                counts(after)));
        BalanceConsistencyResponseBody platform = consistency(FE_BOOK_ID);
        assertAll(() -> assertEquals("1", platform.getAppliedFactCount(), "合作方事实同时属于平台账簿主体"),
            () -> assertEquals(fact.getFactId().toString(), platform.getMaxAppliedFactId()),
            () -> assertEquals("0", counts(platform).get("CLIENT"), "Owner 余额只在经营主体账簿"));
    }

    @Test
    void test_listBookBalances() {
        consumed(payment("100").component(AmountSource.CLIENT_PRINCIPAL, "97.5").build());

        var body = get("/balances/v1/books/" + OE_BOOK_ID + "/accounts").assertSuccess(200)
            .as(BookBalancesResponseBody.class);

        assertEquals(List.of(StandardAccountCodes.CHANNEL_UNSETTLED_RECEIVABLE,
            StandardAccountCodes.CLIENT_PAYABLE), body.getItems().stream().map(item -> item.getAccountCode()).toList());
        var client = body.getItems().get(1);
        assertAll(() -> assertEquals(OwnerType.CLIENT.getCode(), client.getOwnerType()),
            () -> assertEquals("PENDING", client.getBalanceBucket()), () -> assertEquals("CREDIT", client.getNormalSide()),
            () -> assertEquals("0.00", client.getDebitTotal()), () -> assertEquals("97.50", client.getCreditTotal()),
            () -> assertEquals("97.50", client.getNetBalance()), () -> assertEquals("1", client.getLastFactVersion()));
    }

    @Test
    void test_unknownBookOrLedgerUnavailableFailsClosed() {
        get("/balances/v1/books/123456789/consistency").assertBusinessRejected("");

        ledger.mockUnavailable();

        get("/balances/v1/books/" + OE_BOOK_ID + "/consistency").assertServiceUnavailable();
        get("/balances/v1/books/" + OE_BOOK_ID + "/accounts").assertServiceUnavailable();
    }

    /** 读取账簿的消费进度。 */
    BalanceConsistencyResponseBody consistency(long bookId) {
        return get("/balances/v1/books/" + bookId + "/consistency").assertSuccess(200)
            .as(BalanceConsistencyResponseBody.class);
    }

    /** Owner 类型 → 非零余额数。 */
    static Map<String, String> counts(BalanceConsistencyResponseBody body) {
        return body.getNonZeroAccountCounts().stream()
            .collect(Collectors.toMap(OwnerBalanceCountResponseBody::getOwnerType, OwnerBalanceCountResponseBody::getCount));
    }
}
