package com.paytech.payout.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryBlockReason;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.test.base.MockTestBase;
import com.paytech.payout.test.builder.AccountPositionBuilder;
import com.paytech.payout.test.checker.PayoutResponseChecker;
import com.paytech.payout.test.support.PayoutTestConstants;

import tools.jackson.databind.JsonNode;

/**
 * 余额解释（AC-04、AC-08，B9）：各 OE × 币种余额、保证金、追偿与四种阻断原因；冷却按 cooling_until 实时判定
 *
 * @see com.paytech.payout.api.balance.BalanceExplanationApi#explainBalance(String, String)
 */
class BalanceExplanationApiTest extends MockTestBase {
    /** 余额解释路径。 */
    static final String PATH = "/payout/v1/balance-explanations";

    /** 没有任何收款账户：NO_USABLE_ACCOUNT；金额按币种精度输出，Client 有保证金；account 调用在事务外。 */
    @Test
    void test_noUsableAccount() {
        account.mockClientPositions(PayoutTestConstants.CLIENT_ID, List.of(AccountPositionBuilder.template().build()));

        JsonNode position = explainClient().json().get("positions").get(0);
        assertAll(() -> assertEquals(PayoutTestConstants.ENTITY_NO, position.get("entityNo").asString()),
            () -> assertEquals("100.00", position.get("available").asString()),
            () -> assertEquals("20.00", position.get("pending").asString()),
            () -> assertEquals("0.00", position.get("locked").asString()),
            () -> assertEquals("50.00", position.get("securityDeposit").asString()),
            () -> assertEquals(List.of(BeneficiaryBlockReason.NO_USABLE_ACCOUNT.getCode()), reasons(position)),
            () -> assertEquals(List.of("account-service"), transactions.outbound()));
    }

    /** 冷却中：ACCOUNT_COOLING；cooling_until 过去后同一账户实时变为可用，无阻断原因（无需任务）。 */
    @Test
    void test_coolingThenUsable() {
        account.mockClientPositions(PayoutTestConstants.CLIENT_ID, List.of(AccountPositionBuilder.template().build()));
        BeneficiaryAccount cooling = accounts.verified(BeneficiaryOwnerType.CLIENT, PayoutTestConstants.CLIENT_ID, "USD",
            Instant.now().plus(1, ChronoUnit.HOURS), true);

        PayoutResponseChecker before = explainClient();
        assertAll(() -> assertEquals(List.of(BeneficiaryBlockReason.ACCOUNT_COOLING.getCode()),
            reasons(before.json().get("positions").get(0))),
            () -> assertEquals(0, before.json().get("usableAccounts").get(0).get("count").asInt()));

        testMapper.expireCooling(cooling.getId(), 1);
        PayoutResponseChecker after = explainClient();
        JsonNode usable = after.json().get("usableAccounts").get(0);
        assertAll(() -> assertTrue(reasons(after.json().get("positions").get(0)).isEmpty()),
            () -> assertEquals(1, usable.get("count").asInt()),
            () -> assertEquals(String.valueOf(cooling.getId()), usable.get("defaultAccountId").asString()));
    }

    /** 无可用余额与追偿未清单独触发；另一 OE × 币种无该币种账户；按 entityNo、币种排序。 */
    @Test
    void test_noBalanceAndRecovery() {
        account.mockClientPositions(PayoutTestConstants.CLIENT_ID, List.of(
            AccountPositionBuilder.template().position(PayoutTestConstants.ENTITY_NO_2, "SAR").build(),
            AccountPositionBuilder.template().available("0").recovery("10.50").build()));
        accounts.verified(BeneficiaryOwnerType.CLIENT, PayoutTestConstants.CLIENT_ID, "USD",
            Instant.now().minus(1, ChronoUnit.HOURS), false);
        accounts.withStatus(BeneficiaryOwnerType.CLIENT, PayoutTestConstants.CLIENT_ID, "SAR", BeneficiaryAccountStatus.REJECTED);

        JsonNode positions = explainClient().json().get("positions");
        assertAll(() -> assertEquals(PayoutTestConstants.ENTITY_NO, positions.get(0).get("entityNo").asString()),
            () -> assertEquals(List.of(BeneficiaryBlockReason.NO_AVAILABLE_BALANCE.getCode(),
                BeneficiaryBlockReason.RECOVERY_OUTSTANDING.getCode()), reasons(positions.get(0))),
            () -> assertEquals("10.50", positions.get(0).get("recoveryReceivable").asString()),
            () -> assertEquals(List.of(BeneficiaryBlockReason.NO_USABLE_ACCOUNT.getCode()), reasons(positions.get(1))));
    }

    /** Local Partner：调用 Partner 列表接口，没有保证金字段。 */
    @Test
    void test_partnerWithoutDeposit() {
        account.mockPartnerPositions(PayoutTestConstants.PARTNER_ID,
            List.of(AccountPositionBuilder.template().buildPartner()));

        PayoutResponseChecker response = get(PATH + "?ownerType=" + BeneficiaryOwnerType.LOCAL_PARTNER.getCode() + "&ownerId="
            + PayoutTestConstants.PARTNER_ID).assertSuccess(200);
        assertAll(() -> assertTrue(response.json().get("positions").get(0).get("securityDeposit").isNull()),
            () -> assertEquals(List.of("partners:" + PayoutTestConstants.PARTNER_ID), account.calls()));
    }

    /** Owner 在 account 没有余额：positions 为空。 */
    @Test
    void test_emptyPositions() {
        account.mockClientPositions(PayoutTestConstants.CLIENT_ID, List.of());

        assertEquals(0, explainClient().json().get("positions").size());
    }

    /** account 不可用：503。 */
    @Test
    void test_accountUnavailable() {
        account.failClientPositions(PayoutTestConstants.CLIENT_ID, MockHttpFault.disconnect());

        get(PATH + "?ownerType=CLIENT&ownerId=" + PayoutTestConstants.CLIENT_ID).assertStatus(503);
    }

    /** 查询 Client 的余额解释。 */
    PayoutResponseChecker explainClient() {
        return get(PATH + "?ownerType=" + BeneficiaryOwnerType.CLIENT.getCode() + "&ownerId=" + PayoutTestConstants.CLIENT_ID)
            .assertSuccess(200);
    }

    /** 阻断原因列表。 */
    static List<String> reasons(JsonNode position) {
        List<String> reasons = new ArrayList<>();
        position.get("blockReasons").forEach(reason -> reasons.add(reason.asString()));
        return reasons;
    }
}
