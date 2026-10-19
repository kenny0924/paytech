package com.paytech.account.auto.ext.http.balance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.BalanceQueryApi;
import com.paytech.account.api.enums.BalanceQueryErrorCode;
import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.foundation.money.Money;

/**
 * Client 与 Partner 分类余额：只读本库 Owner 账户，不调用 ledger
 * <li>账户由消费 BillingFactRecorded 开立（扮演 billing 发布到真实本机 RabbitMQ）；有账户时缺少的桶为零，没有任何账户时 400
 * OWNER_ACCOUNT_NOT_FOUND。</li>
 *
 * @see BalanceQueryApi#queryClientBalance(String, String, String)
 * @see BalanceQueryApi#queryPartnerBalance(String, String, String)
 */
class QueryOwnerBalanceTest extends AbstractBalanceTest {

    @Test
    void test_clientBalanceReadsOwnerAccounts() {
        consumed(payment("100")
            .component(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY,
                FactPartyRole.CLIENT, "70.25").build());

        var body = get("/balances/v1/clients/1001?entityNo=AE001&currency=USD").assertSuccess(200)
            .as(ClientBalanceResponseBody.class);

        assertAll(() -> assertEquals("1001", body.getClientId()), () -> assertEquals("AE001", body.getEntityNo()),
            () -> assertEquals(Money.of("USD", "70.25"), body.getPendingAmount()),
            () -> assertEquals(Money.zero("USD"), body.getAvailableAmount()),
            () -> assertEquals(Money.zero("USD"), body.getLockedAmount()),
            () -> assertEquals(Money.zero("USD"), body.getSecurityDepositAmount()),
            () -> assertEquals(Money.zero("USD"), body.getRecoveryReceivableAmount()));
        posted.await(1);
    }

    @Test
    void test_partnerBalanceReturnsZeroForMissingBuckets() {
        consumed(adjustment().partner(PARTNER_ID, FINANCE_ENTITY_ID)
            .line(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.LOCAL_PARTNER,
                PARTNER, FactLineDirection.CHARGE, "10").build());

        var body = get("/balances/v1/partners/2001?entityNo=AE001&currency=USD").assertSuccess(200)
            .as(PartnerBalanceResponseBody.class);

        assertAll(() -> assertEquals(Money.of("USD", "10"), body.getPendingAmount()),
            () -> assertEquals(Money.zero("USD"), body.getAvailableAmount()),
            () -> assertEquals(Money.zero("USD"), body.getLockedAmount()),
            () -> assertEquals(Money.zero("USD"), body.getRecoveryReceivableAmount()));
        posted.await(1);
    }

    /** Owner 在该 OE 与币种下没有任何账户（含其他 Owner 已有账户、其他 OE）：400 OWNER_ACCOUNT_NOT_FOUND。 */
    @Test
    void test_ownerWithoutAccountsRejected() {
        consumed(clientCredit("1").build());

        get("/balances/v1/partners/2001?entityNo=AE001&currency=USD")
            .assertRejected(BalanceQueryErrorCode.OWNER_ACCOUNT_NOT_FOUND);
        get("/balances/v1/clients/1001?entityNo=AE009&currency=USD")
            .assertRejected(BalanceQueryErrorCode.OWNER_ACCOUNT_NOT_FOUND);
        get("/balances/v1/clients/1001?entityNo=AE001&currency=EUR")
            .assertRejected(BalanceQueryErrorCode.OWNER_ACCOUNT_NOT_FOUND);
        posted.await(1);
    }

    @Test
    void test_invalidParameter() {
        get("/balances/v1/clients/1001?entityNo=ae001&currency=USD").assertStatus(400);
        get("/balances/v1/clients/1001?operatingEntityId=2001&currency=USD").assertStatus(400);
    }
}
