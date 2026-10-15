package com.paytech.finance.auto.ext.http.balance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.constant.StandardAccountCodes;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.api.balance.BalanceQueryApi;
import com.paytech.finance.api.balance.response.ClientBalanceResponseBody;
import com.paytech.finance.api.balance.response.PartnerBalanceResponseBody;
import com.paytech.finance.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.finance.test.builder.SubmitManualAdjustmentRequestBuilder;
import com.paytech.foundation.money.Money;

/**
 * Client 与 Partner 分类余额：以 entityNo 定位账簿，经人工调整过账后余额与 Journal 一致，未建账户返回零。
 *
 * @see BalanceQueryApi#queryClientBalance(String, String, String)
 * @see BalanceQueryApi#queryPartnerBalance(String, String, String)
 */
class QueryOwnerBalanceTest extends AbstractManualAdjustmentApiTest {

    @Test
    void test_clientBalanceMatchesPostedJournals() {
        long deposit = ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.CLIENT_SECURITY_DEPOSIT,
            OwnerType.CLIENT, "1001", BalanceBucket.NONE);
        long recovery = ledgerMocker.mockAccount(ledger.getLedgerBookId(),
            StandardAccountCodes.CLIENT_RECOVERY_RECEIVABLE, OwnerType.CLIENT, "1001", BalanceBucket.NONE);
        posted(adjustment().transfer(bankCashAccountId, clientAvailableAccountId, "70.25"));
        posted(adjustment().transfer(bankCashAccountId, deposit, "500"));
        posted(adjustment().transfer(recovery, bankCashAccountId, "1.5"));

        var body = get("/balances/v1/clients/1001?entityNo=AE001&currency=USD").assertSuccess(200)
            .as(ClientBalanceResponseBody.class);

        assertAll(() -> assertEquals("1001", body.getClientId()), () -> assertEquals("AE001", body.getEntityNo()),
            () -> assertEquals(Money.zero("USD"), body.getPendingAmount()),
            () -> assertEquals(Money.of("USD", "70.25"), body.getAvailableAmount()),
            () -> assertEquals(Money.zero("USD"), body.getLockedAmount()),
            () -> assertEquals(Money.of("USD", "500"), body.getSecurityDepositAmount()),
            () -> assertEquals(Money.of("USD", "1.5"), body.getRecoveryReceivableAmount()));
        ledgerChecker.assertProjectionMatchesJournal(clientAvailableAccountId).assertProjectionMatchesJournal(deposit)
            .assertProjectionMatchesJournal(recovery);
    }

    @Test
    void test_partnerBalanceReturnsZeroForMissingAccounts() {
        long partnerPending = ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.PARTNER_PAYABLE,
            OwnerType.LOCAL_PARTNER, "2001", BalanceBucket.PENDING);
        posted(adjustment().transfer(bankCashAccountId, partnerPending, "10"));

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

    /** 提交并批准一笔调整。 */
    void posted(SubmitManualAdjustmentRequestBuilder builder) {
        var submit = submitted(builder);
        approve(submit.id("manualAdjustmentId"), CHECKER_ID, submit.text("contentHash")).assertSuccess(200);
    }
}
