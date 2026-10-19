package com.paytech.account.auto.ext.http.balance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.BalanceQueryApi;
import com.paytech.account.api.enums.BalanceQueryErrorCode;
import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.foundation.money.Money;

/**
 * Client 与 Partner 分类余额：经 ledger-api 确认 entityNo 的币种账簿存在，读取本服务自有余额，未建立的余额返回零
 * <li>第 11b 步：余额由消费 EconomicFactRecorded 建立（扮演 billing 发布到真实本机 RabbitMQ），不再读 ledger 的 t_account；
 * ledger 不可用时失败关闭（design/04 §6）。</li>
 *
 * @see BalanceQueryApi#queryClientBalance(String, String, String)
 * @see BalanceQueryApi#queryPartnerBalance(String, String, String)
 */
class QueryOwnerBalanceTest extends AbstractBalanceTest {

    @Test
    void test_clientBalanceReadsAccountProjection() {
        consumed(payment("100").component(AmountSource.CLIENT_PRINCIPAL, "70.25").build());

        var body = get("/balances/v1/clients/1001?entityNo=AE001&currency=USD").assertSuccess(200)
            .as(ClientBalanceResponseBody.class);

        assertAll(() -> assertEquals("1001", body.getClientId()), () -> assertEquals("AE001", body.getEntityNo()),
            () -> assertEquals(Money.of("USD", "70.25"), body.getPendingAmount()),
            () -> assertEquals(Money.zero("USD"), body.getAvailableAmount()),
            () -> assertEquals(Money.zero("USD"), body.getLockedAmount()),
            () -> assertEquals(Money.zero("USD"), body.getSecurityDepositAmount()),
            () -> assertEquals(Money.zero("USD"), body.getRecoveryReceivableAmount()));
    }

    @Test
    void test_partnerBalanceReturnsZeroForMissingAccounts() {
        consumed(adjustment().partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, FINANCE_ENTITY_ID)
            .line(AmountSource.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.LOCAL_PARTNER,
                "2001", FactLineDirection.CHARGE, "10").build());

        var body = get("/balances/v1/partners/2001?entityNo=AE001&currency=USD").assertSuccess(200)
            .as(PartnerBalanceResponseBody.class);

        assertAll(() -> assertEquals(Money.of("USD", "10"), body.getPendingAmount()),
            () -> assertEquals(Money.zero("USD"), body.getAvailableAmount()),
            () -> assertEquals(Money.zero("USD"), body.getLockedAmount()),
            () -> assertEquals(Money.zero("USD"), body.getRecoveryReceivableAmount()));
    }

    @Test
    void test_missingBookRejected() {
        get("/balances/v1/clients/1001?entityNo=AE009&currency=USD")
            .assertRejected(BalanceQueryErrorCode.LEDGER_BOOK_NOT_FOUND);
        get("/balances/v1/partners/2001?entityNo=AE009&currency=USD")
            .assertRejected(BalanceQueryErrorCode.LEDGER_BOOK_NOT_FOUND);
    }

    @Test
    void test_invalidParameter() {
        get("/balances/v1/clients/1001?entityNo=ae001&currency=USD").assertStatus(400);
        get("/balances/v1/clients/1001?operatingEntityId=2001&currency=USD").assertStatus(400);
    }

    /** ledger-api 不可用：不猜测账簿是否存在，以 503 失败关闭。 */
    @Test
    void test_ledgerUnavailableFailsClosed() {
        consumed(payment("100").component(AmountSource.CLIENT_PRINCIPAL, "100").build());
        ledger.mockUnavailable();

        get("/balances/v1/clients/1001?entityNo=AE001&currency=USD").assertServiceUnavailable();
        get("/balances/v1/partners/2001?entityNo=AE001&currency=USD").assertServiceUnavailable();
    }
}
