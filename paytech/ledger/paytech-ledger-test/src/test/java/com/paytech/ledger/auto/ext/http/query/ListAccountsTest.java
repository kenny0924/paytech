package com.paytech.ledger.auto.ext.http.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.LedgerBookQueryApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.ledger.test.builder.AccountingSetupRequestBuilder;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

import tools.jackson.databind.JsonNode;

/**
 * 账户列表：过滤（含账户号精确筛选）、账户号与科目名称、余额由分录聚合、排序、账簿隔离与不存在拒绝。
 *
 * @see LedgerBookQueryApi#listAccounts(String, String, String, String, String, String, Integer, Integer)
 */
class ListAccountsTest extends AbstractManualAdjustmentApiTest {

    @Test
    void test_balancesAggregateJournalsAfterPosting() {
        posted(adjustment().transfer(liabilityAccountId, bankCashAccountId, "12.5"));

        List<JsonNode> accounts = get(path()).assertSuccess(200).assertPage(1, 10, 3);

        assertEquals(List.of("BANK_CASH", "CAPITAL", LIABILITY_CODE),
            accounts.stream().map(item -> item.path("accountCode").asString()).toList(), "按科目代码排序");
        JsonNode client = accounts.get(2);
        assertAll(() -> assertEquals(String.valueOf(liabilityAccountId), client.path("accountId").asString()),
            () -> assertEquals(LedgerOwnerType.OPERATING_ENTITY.getCode(), client.path("ownerType").asString()),
            () -> assertEquals("AE001", client.path("ownerId").asString()),
            () -> assertEquals(BalanceBucket.NONE.getCode(), client.path("balanceBucket").asString()),
            () -> assertEquals(NormalSide.CREDIT.getCode(), client.path("normalSide").asString()),
            () -> assertEquals(AccountStatus.ACTIVE.getCode(), client.path("accountStatus").asString()),
            () -> assertEquals("12.50", client.path("debitTotal").asString()),
            () -> assertEquals("0.00", client.path("creditTotal").asString()),
            () -> assertEquals("-12.50", client.path("netBalance").asString()),
            () -> assertEquals("1", client.path("lastJournalSequence").asString()));
        ledgerChecker.assertAccount(liabilityAccountId, "12.5", "0", "-12.5", 1L);
    }

    /** 过滤：科目、核算对象类型 + 标识、余额桶（一律 NONE）。总账只有平台类 Owner，Client 应付是以账簿主体为 Owner 的控制账户。 */
    @Test
    void test_filters() {
        ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.CLIENT_PAYABLE,
            LedgerOwnerType.OPERATING_ENTITY, ledger.getEntityNo(), BalanceBucket.NONE);
        ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.CHANNEL_FEE_ACCRUAL,
            LedgerOwnerType.CHANNEL_ACCOUNT, "77", BalanceBucket.NONE);
        ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.CHANNEL_FEE_ACCRUAL,
            LedgerOwnerType.CHANNEL_ACCOUNT, "78", BalanceBucket.NONE);

        get(path() + "?accountCode=CLIENT_PAYABLE").assertSuccess(200).assertPage(1, 10, 1);
        get(path() + "?ownerType=CHANNEL_ACCOUNT&ownerId=77").assertSuccess(200).assertPage(1, 10, 1);
        get(path() + "?ownerType=CHANNEL_ACCOUNT").assertSuccess(200).assertPage(1, 10, 2);
        get(path() + "?balanceBucket=NONE").assertSuccess(200).assertPage(1, 10, 6);
        JsonNode bank = get(path() + "?ownerType=CASH_ACCOUNT").assertSuccess(200).assertPage(1, 10, 1).getFirst();
        assertEquals(String.valueOf(bankCashAccountId), bank.path("accountId").asString());
    }

    /** 账户号与科目名称随列表返回；按账户号精确筛选。 */
    @Test
    void test_accountNoReturnedAndFiltered() {
        long created = postAsOperator(path(), AccountingSetupRequestBuilder.account(StandardAccountCodes.BANK_CASH,
            LedgerOwnerType.CASH_ACCOUNT.getCode(), "6001", BalanceBucket.NONE.getCode())).assertSuccess(201)
            .id("accountId");

        List<JsonNode> matched = get(path() + "?accountNo=AE001-USD-10010101-0001").assertSuccess(200).assertPage(1, 10, 1);
        JsonNode account = matched.getFirst();
        assertAll(() -> assertEquals(String.valueOf(created), account.path("accountId").asString()),
            () -> assertEquals("AE001-USD-10010101-0001", account.path("accountNo").asString()),
            () -> assertEquals(StandardAccountCodes.BANK_CASH, account.path("accountCode").asString()),
            () -> assertEquals("银行现金", account.path("accountName").asString()));
        get(path() + "?accountNo=AE001-USD-10010101-0001&accountCode=" + StandardAccountCodes.CLIENT_PAYABLE)
            .assertSuccess(200).assertPage(1, 10, 0);
        get(path() + "?accountNo=AE001-USD-10010101-9999").assertSuccess(200).assertPage(1, 10, 0);
        JsonNode mocked = get(path() + "?accountNo=" + LedgerMocker.MOCK_ACCOUNT_NO_PREFIX + bankCashAccountId)
            .assertSuccess(200).assertPage(1, 10, 1).getFirst();
        assertEquals(String.valueOf(bankCashAccountId), mocked.path("accountId").asString());
    }

    /** Client / Local Partner 不再是总账核算对象类型：过滤值非法。 */
    @Test
    void test_removedOwnerTypeInvalid() {
        get(path() + "?ownerType=" + OwnerType.CLIENT.getCode()).assertInvalidRequest();
    }

    @Test
    void test_otherBookAccountsExcluded() {
        long otherBook = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE002", "USD",
            LedgerMocker.STANDARD_CHART_ID);
        ledgerMocker.mockAccount(otherBook, "CAPITAL", LedgerOwnerType.OPERATING_ENTITY, "AE002", BalanceBucket.NONE);

        get(path()).assertSuccess(200).assertPage(1, 10, 3);
        get("/ledger-books/v1/" + otherBook + "/accounts").assertSuccess(200).assertPage(1, 10, 1);
    }

    @Test
    void test_unknownBookRejected() {
        get("/ledger-books/v1/123456789/accounts").assertRejected(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND);
    }

    @Test
    void test_invalidParameter() {
        get(path() + "?balanceBucket=OTHER").assertStatus(400);
        get(path() + "?size=1000").assertInvalidRequest();
    }

    /** 本类账簿的账户列表路径。 */
    String path() {
        return "/ledger-books/v1/" + ledger.getLedgerBookId() + "/accounts";
    }
}
