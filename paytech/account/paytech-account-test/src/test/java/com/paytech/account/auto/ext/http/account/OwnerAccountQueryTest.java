package com.paytech.account.auto.ext.http.account;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.OwnerAccountQueryApi;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.BalanceMovementErrorCode;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.enums.PostingSourceType;
import com.paytech.account.core.enums.AccountStatus;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.test.builder.BalanceMovementRequestBuilder;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.fact.EconomicFactPayload;

import tools.jackson.databind.JsonNode;

/**
 * Owner 账户与明细流水分页查询：经真实 HTTP 入口读取本地 MySQL
 * <li>账户按科目代码、核算对象排序，可按 Owner 类型、Owner、科目、桶筛选，分页走公共 page/size；流水按批次序号、行号升序，事实流水带事实类型，
 * 桶移动流水带移动来源类型；账户不存在为 ACCOUNT_NOT_FOUND。</li>
 *
 * @see OwnerAccountQueryApi#listOwnerAccounts(String, String, String, String, String, String, String, Integer, Integer)
 * @see OwnerAccountQueryApi#listOwnerAccountEntries(String, Integer, Integer)
 */
class OwnerAccountQueryTest extends AbstractBalanceTest {
    private static final String ACCOUNTS = "/owner-accounts/v1?entityType=" + AccountEntityType.OPERATING_ENTITY.getCode()
        + "&entityId=AE001&currency=USD";

    /** 全部账户、按 Owner 类型与按科目 + 桶筛选、越界页与非法 size。 */
    @Test
    void test_listAccountsWithFilters() {
        consumed(payment("100").partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, FINANCE_ENTITY_ID)
            .component(AmountSource.CLIENT_PRINCIPAL, "94").component(AmountSource.PARTNER_SHARE, "1").build());
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.BANK_CASH, "770001", BalanceBucket.AVAILABLE, "500");

        List<JsonNode> all = get(ACCOUNTS).assertSuccess(200).assertPage(1, 10, 3);
        List<JsonNode> clients = get(ACCOUNTS + "&ownerType=" + OwnerType.CLIENT.getCode()).assertSuccess(200).assertPage(1, 10, 1);
        List<JsonNode> cash = get(ACCOUNTS + "&accountCode=" + OwnerAccountType.BANK_CASH.getCode() + "&balanceBucket="
            + BalanceBucket.AVAILABLE.getCode()).assertSuccess(200)
            .assertPage(1, 10, 1);
        List<JsonNode> beyond = get(ACCOUNTS + "&page=2&size=10").assertSuccess(200).assertPage(2, 10, 3);

        JsonNode client = clients.getFirst();
        assertAll(() -> assertEquals(List.of(OwnerAccountType.BANK_CASH.getCode(), OwnerAccountType.CLIENT_PAYABLE.getCode(),
            OwnerAccountType.PARTNER_PAYABLE.getCode()), all.stream().map(node -> node.path("accountCode").asString()).toList()),
            () -> assertEquals(OwnerType.CLIENT.getCode(), client.path("ownerType").asString()),
            () -> assertEquals(CLIENT, client.path("ownerId").asString()),
            () -> assertEquals(BalanceBucket.PENDING.getCode(), client.path("balanceBucket").asString()),
            () -> assertEquals(EntrySide.CREDIT.getCode(), client.path("normalSide").asString()),
            () -> assertEquals(AccountStatus.ACTIVE.getCode(), client.path("accountStatus").asString()),
            () -> assertEquals("94.00", client.path("netBalance").path("amount").asString()),
            () -> assertEquals("1", client.path("balanceVersion").asString()),
            () -> assertEquals("500.00", cash.getFirst().path("netBalance").path("amount").asString()),
            () -> assertTrue(beyond.isEmpty()));
        get(ACCOUNTS + "&size=7").assertInvalidRequest();
        get(ACCOUNTS.replace(AccountEntityType.OPERATING_ENTITY.getCode(), AccountEntityType.FINANCE_ENTITY.getCode())).assertInvalidRequest();
        posted.await(1);
    }

    /** 账户流水按入账顺序分页，带批次序号与来源；账户不存在为 ACCOUNT_NOT_FOUND。 */
    @Test
    void test_listEntriesPaged() {
        EconomicFactPayload first = clientCredit("10").build();
        consumed(first);
        consumed(clientCredit("2").build());
        String accountId = account(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING).get("id").toString();

        List<JsonNode> entries = get("/owner-accounts/v1/" + accountId + "/entries").assertSuccess(200).assertPage(1, 10, 2);

        JsonNode head = entries.getFirst();
        assertAll(() -> assertEquals(List.of("1", "2"), entries.stream().map(node -> node.path("postingSequence").asString())
            .toList()), () -> assertEquals(PostingSourceType.ECONOMIC_FACT.getCode(), head.path("sourceType").asString()),
            () -> assertEquals(Long.toString(first.getFactId()), head.path("factId").asString()),
            () -> assertEquals("1", head.path("factVersion").asString()),
            () -> assertEquals(EconomicsType.MANUAL_ADJUSTMENT.getCode(), head.path("factType").asString()),
            () -> assertEquals(EntrySide.CREDIT.getCode(), head.path("entrySide").asString()),
            () -> assertEquals("10.00", head.path("amount").path("amount").asString()),
            () -> assertTrue(head.path("movementSourceType").isNull()));
        get("/owner-accounts/v1/9999/entries").assertRejected(BalanceMovementErrorCode.ACCOUNT_NOT_FOUND);
        posted.await(2);
    }

    /** 桶移动流水：带移动来源类型，事实类型与事实主键为空。 */
    @Test
    void test_movementEntryHasNoFactType() {
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "100");
        post("/balance-movements/v1", BalanceMovementRequestBuilder.template().amount("30").build()).assertSuccess(201);
        String lockedId = account(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.LOCKED).get("id").toString();

        JsonNode entry = get("/owner-accounts/v1/" + lockedId + "/entries").assertSuccess(200).assertPage(1, 10, 1)
            .getFirst();

        assertAll(() -> assertEquals(PostingSourceType.BALANCE_MOVEMENT.getCode(), entry.path("sourceType").asString()),
            () -> assertEquals(MovementSourceType.PAYOUT_LOCK.getCode(), entry.path("movementSourceType").asString()),
            () -> assertTrue(entry.path("factType").isNull()), () -> assertTrue(entry.path("factId").isNull()));
        posted.await(1);
    }
}
