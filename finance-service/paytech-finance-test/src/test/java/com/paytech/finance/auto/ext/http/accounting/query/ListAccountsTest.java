package com.paytech.finance.auto.ext.http.accounting.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.constant.StandardAccountCodes;
import com.paytech.finance.accounting.core.enums.AccountStatus;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.finance.accounting.core.enums.NormalSide;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.api.accounting.LedgerBookQueryApi;
import com.paytech.finance.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.finance.test.mocker.local.LedgerMocker;

import tools.jackson.databind.JsonNode;

/**
 * 账户列表：过滤、余额与投影一致、排序、账簿隔离与不存在拒绝。
 *
 * @see LedgerBookQueryApi#listAccounts(String, String, String, String, String, Integer, Integer)
 */
class ListAccountsTest extends AbstractManualAdjustmentApiTest {

    @Test
    void test_balancesMatchProjectionAfterPosting() {
        posted(adjustment().transfer(clientAvailableAccountId, bankCashAccountId, "12.5"));

        List<JsonNode> accounts = get(path()).assertSuccess(200).assertPage(1, 10, 3);

        assertEquals(List.of("BANK_CASH", "CAPITAL", StandardAccountCodes.CLIENT_PAYABLE),
            accounts.stream().map(item -> item.path("accountCode").asString()).toList(), "按科目代码排序");
        JsonNode client = accounts.get(2);
        assertAll(() -> assertEquals(String.valueOf(clientAvailableAccountId), client.path("accountId").asString()),
            () -> assertEquals(OwnerType.CLIENT.getCode(), client.path("ownerType").asString()),
            () -> assertEquals("1001", client.path("ownerId").asString()),
            () -> assertEquals(BalanceBucket.AVAILABLE.getCode(), client.path("balanceBucket").asString()),
            () -> assertEquals(NormalSide.CREDIT.getCode(), client.path("normalSide").asString()),
            () -> assertEquals(AccountStatus.ACTIVE.getCode(), client.path("accountStatus").asString()),
            () -> assertEquals("12.50", client.path("debitTotal").asString()),
            () -> assertEquals("0.00", client.path("creditTotal").asString()),
            () -> assertEquals("-12.50", client.path("netBalance").asString()),
            () -> assertEquals("1", client.path("lastJournalSequence").asString()));
        ledgerChecker.assertAccount(clientAvailableAccountId, "12.5", "0", "-12.5", 1L);
    }

    @Test
    void test_filters() {
        ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, "1001",
            BalanceBucket.PENDING);
        ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, "1002",
            BalanceBucket.PENDING);

        get(path() + "?accountCode=CLIENT_PAYABLE").assertSuccess(200).assertPage(1, 10, 3);
        get(path() + "?ownerType=CLIENT&ownerId=1001").assertSuccess(200).assertPage(1, 10, 2);
        get(path() + "?balanceBucket=PENDING").assertSuccess(200).assertPage(1, 10, 2);
        JsonNode bank = get(path() + "?ownerType=CASH_ACCOUNT").assertSuccess(200).assertPage(1, 10, 1).getFirst();
        assertEquals(String.valueOf(bankCashAccountId), bank.path("accountId").asString());
    }

    @Test
    void test_otherBookAccountsExcluded() {
        long otherBook = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE002", "USD",
            LedgerMocker.STANDARD_CHART_ID);
        ledgerMocker.mockAccount(otherBook, "CAPITAL", OwnerType.OPERATING_ENTITY, "AE002", BalanceBucket.NONE);

        get(path()).assertSuccess(200).assertPage(1, 10, 3);
        get("/ledger-books/v1/" + otherBook + "/accounts").assertSuccess(200).assertPage(1, 10, 1);
    }

    @Test
    void test_unknownBookRejected() {
        get("/ledger-books/v1/123456789/accounts").assertBusinessRejected("账簿不存在: 123456789");
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
