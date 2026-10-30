package com.paytech.account.auto.ext.http.balance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.BalanceQueryApi;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.foundation.money.Money;

/**
 * Owner 全部经营主体与币种的余额列表：一次读取本库 Owner 账户后按 entityNo × 币种聚合
 * <li>账户由本地 Mocker 直接插入（不经 MQ）；按 entityNo、币种升序，缺少的桶为零，其他 Owner、Finance Entity 账簿与非本 Owner
 * 类型的账户不计入；没有任何账户时为空数组。</li>
 *
 * @see BalanceQueryApi#listClientPositions(String)
 * @see BalanceQueryApi#listPartnerPositions(String)
 */
class ListOwnerPositionsTest extends AbstractBalanceTest {
    private static final String SA = "SA001";
    private static final String AED = "AED";
    private static final String OTHER_CLIENT = "1002";

    /** 两个 OE × 三个币种组合：各桶归到所属组合，其他 Owner 与 Finance Entity 账簿不混入。 */
    @Test
    void test_clientPositionsGroupedByEntityAndCurrency() {
        accounts.mockAccount(SA, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "30");
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "70.25");
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "12");
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.LOCKED, "3");
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_SECURITY_DEPOSIT, CLIENT, BalanceBucket.NONE, "500");
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_RECOVERY_RECEIVABLE, CLIENT, BalanceBucket.NONE, "4.5");
        accounts.mockAccount(ENTITY_NO, AED, OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "9");
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, OTHER_CLIENT, BalanceBucket.AVAILABLE, "999");
        accounts.mockAccount(AccountEntityType.FINANCE_ENTITY, String.valueOf(FINANCE_ENTITY_ID), CURRENCY,
            OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "888");

        List<ClientBalanceResponseBody> positions = Arrays.asList(get("/balances/v1/clients/1001/positions")
            .assertSuccess(200).as(ClientBalanceResponseBody[].class));

        assertEquals(List.of("AE001/AED", "AE001/USD", "SA001/USD"),
            positions.stream().map(p -> p.getEntityNo() + "/" + p.getCurrency()).toList());
        var aed = positions.get(0);
        var usd = positions.get(1);
        var sa = positions.get(2);
        assertAll(() -> assertEquals(CLIENT, usd.getClientId()),
            () -> assertEquals(Money.of(CURRENCY, "70.25"), usd.getPendingAmount()),
            () -> assertEquals(Money.of(CURRENCY, "12"), usd.getAvailableAmount()),
            () -> assertEquals(Money.of(CURRENCY, "3"), usd.getLockedAmount()),
            () -> assertEquals(Money.of(CURRENCY, "500"), usd.getSecurityDepositAmount()),
            () -> assertEquals(Money.of(CURRENCY, "4.5"), usd.getRecoveryReceivableAmount()),
            () -> assertEquals(Money.of(AED, "9"), aed.getPendingAmount()),
            () -> assertEquals(Money.zero(AED), aed.getAvailableAmount()),
            () -> assertEquals(Money.zero(AED), aed.getSecurityDepositAmount()),
            () -> assertEquals(Money.of(CURRENCY, "30"), sa.getAvailableAmount()),
            () -> assertEquals(Money.zero(CURRENCY), sa.getPendingAmount()),
            () -> assertEquals(Money.zero(CURRENCY), sa.getRecoveryReceivableAmount()));
    }

    /** Partner 列表只含 Partner 应付三桶与追偿应收；同一编号的 Client 账户不计入。 */
    @Test
    void test_partnerPositions() {
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.PARTNER_PAYABLE, PARTNER, BalanceBucket.PENDING, "10");
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.PARTNER_RECOVERY_RECEIVABLE, PARTNER, BalanceBucket.NONE, "2");
        accounts.mockAccount(SA, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, PARTNER, BalanceBucket.AVAILABLE, "77");

        List<PartnerBalanceResponseBody> positions = Arrays.asList(get("/balances/v1/partners/2001/positions")
            .assertSuccess(200).as(PartnerBalanceResponseBody[].class));

        assertEquals(1, positions.size());
        var position = positions.getFirst();
        assertAll(() -> assertEquals(PARTNER, position.getPartnerId()),
            () -> assertEquals(ENTITY_NO, position.getEntityNo()),
            () -> assertEquals(Money.of(CURRENCY, "10"), position.getPendingAmount()),
            () -> assertEquals(Money.zero(CURRENCY), position.getAvailableAmount()),
            () -> assertEquals(Money.zero(CURRENCY), position.getLockedAmount()),
            () -> assertEquals(Money.of(CURRENCY, "2"), position.getRecoveryReceivableAmount()));
    }

    /** Owner 没有任何账户（其他 Owner 有账户）：200 空数组，不再 400 OWNER_ACCOUNT_NOT_FOUND。 */
    @Test
    void test_noDataFound() {
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, OTHER_CLIENT, BalanceBucket.AVAILABLE, "1");

        assertEquals(0, get("/balances/v1/clients/1001/positions").assertSuccess(200).json().size());
        assertEquals(0, get("/balances/v1/partners/2001/positions").assertSuccess(200).json().size());
    }

    /** 非规范十进制 Owner 标识：400。 */
    @Test
    void test_invalidParameter() {
        get("/balances/v1/clients/abc/positions").assertStatus(400);
        get("/balances/v1/partners/0123/positions").assertStatus(400);
    }
}
