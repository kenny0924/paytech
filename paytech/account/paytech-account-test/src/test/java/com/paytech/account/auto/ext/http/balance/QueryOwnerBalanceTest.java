package com.paytech.account.auto.ext.http.balance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.BalanceQueryApi;
import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;
import com.paytech.account.test.base.MockTestBase;
import com.paytech.foundation.money.Money;

/**
 * Client 与 Partner 分类余额：以 entityNo 定位账簿，读取账户余额投影，未建账户返回零
 * <li>由 Finance 迁入 account（任务卡 07 第 4 节）：前置数据由 BalanceAccountMocker 直接写共库 t_ledger_book / t_account，
 * 不再经 ledger 人工调整过账；投影与分录一致性由 ledger 测试覆盖，第 11b 步改为 account 自有余额表与事实消费。</li>
 *
 * @see BalanceQueryApi#queryClientBalance(String, String, String)
 * @see BalanceQueryApi#queryPartnerBalance(String, String, String)
 */
class QueryOwnerBalanceTest extends MockTestBase {

    @Test
    void test_clientBalanceReadsAccountProjection() {
        long book = balanceAccountMocker.mockOperatingEntityBook("AE001", "USD");
        balanceAccountMocker.mockAccount(book, StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, "1001",
            BalanceBucket.AVAILABLE, "70.25");
        balanceAccountMocker.mockAccount(book, StandardAccountCodes.CLIENT_SECURITY_DEPOSIT, OwnerType.CLIENT, "1001",
            BalanceBucket.NONE, "500");
        balanceAccountMocker.mockAccount(book, StandardAccountCodes.CLIENT_RECOVERY_RECEIVABLE, OwnerType.CLIENT,
            "1001", BalanceBucket.NONE, "1.5");

        var body = get("/balances/v1/clients/1001?entityNo=AE001&currency=USD").assertSuccess(200)
            .as(ClientBalanceResponseBody.class);

        assertAll(() -> assertEquals("1001", body.getClientId()), () -> assertEquals("AE001", body.getEntityNo()),
            () -> assertEquals(Money.zero("USD"), body.getPendingAmount()),
            () -> assertEquals(Money.of("USD", "70.25"), body.getAvailableAmount()),
            () -> assertEquals(Money.zero("USD"), body.getLockedAmount()),
            () -> assertEquals(Money.of("USD", "500"), body.getSecurityDepositAmount()),
            () -> assertEquals(Money.of("USD", "1.5"), body.getRecoveryReceivableAmount()));
    }

    @Test
    void test_partnerBalanceReturnsZeroForMissingAccounts() {
        long book = balanceAccountMocker.mockOperatingEntityBook("AE001", "USD");
        balanceAccountMocker.mockAccount(book, StandardAccountCodes.PARTNER_PAYABLE, OwnerType.LOCAL_PARTNER, "2001",
            BalanceBucket.PENDING, "10");

        var body = get("/balances/v1/partners/2001?entityNo=AE001&currency=USD").assertSuccess(200)
            .as(PartnerBalanceResponseBody.class);

        assertAll(() -> assertEquals(Money.of("USD", "10"), body.getPendingAmount()),
            () -> assertEquals(Money.zero("USD"), body.getAvailableAmount()),
            () -> assertEquals(Money.zero("USD"), body.getLockedAmount()),
            () -> assertEquals(Money.zero("USD"), body.getRecoveryReceivableAmount()));
    }

    @Test
    void test_missingBookRejected() {
        get("/balances/v1/clients/1001?entityNo=AE009&currency=USD").assertBusinessRejected("未找到经营主体币种账簿");
    }

    @Test
    void test_invalidParameter() {
        get("/balances/v1/clients/1001?entityNo=ae001&currency=USD").assertStatus(400);
        get("/balances/v1/clients/1001?operatingEntityId=2001&currency=USD").assertStatus(400);
    }
}
