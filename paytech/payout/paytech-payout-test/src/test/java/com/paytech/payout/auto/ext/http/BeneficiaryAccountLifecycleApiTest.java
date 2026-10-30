package com.paytech.payout.auto.ext.http;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.payout.api.beneficiary.request.BeneficiaryAccountCommandRequestBody;
import com.paytech.payout.api.beneficiary.request.RevealBeneficiaryAccountRequestBody;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryCloseReason;
import com.paytech.payout.api.enums.BeneficiaryEventAction;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRevealReason;
import com.paytech.payout.api.enums.BeneficiarySuspendReason;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.api.enums.PayoutErrorCode;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.service.handler.BeneficiaryNotificationHandler;
import com.paytech.payout.test.base.AbstractBeneficiaryApiTest;
import com.paytech.payout.test.builder.SubmitBeneficiaryAccountBuilder;
import com.paytech.payout.test.checker.PayoutResponseChecker;
import com.paytech.payout.test.support.PayoutTestConstants;

/**
 * 默认账户、停用 / 恢复、关闭与原值查看（AC-05、AC-06、AC-07、AC-09，B2、B5、B7）
 *
 * @see com.paytech.payout.api.beneficiary.BeneficiaryAccountApi#setDefaultBeneficiaryAccount(String, BeneficiaryAccountCommandRequestBody)
 * @see com.paytech.payout.api.beneficiary.BeneficiaryAccountApi#suspendBeneficiaryAccount(String, BeneficiaryAccountCommandRequestBody)
 * @see com.paytech.payout.api.beneficiary.BeneficiaryAccountApi#resumeBeneficiaryAccount(String, BeneficiaryAccountCommandRequestBody)
 * @see com.paytech.payout.api.beneficiary.BeneficiaryAccountApi#closeBeneficiaryAccount(String, BeneficiaryAccountCommandRequestBody)
 * @see com.paytech.payout.api.beneficiary.BeneficiaryAccountApi#revealBeneficiaryAccount(String, String, RevealBeneficiaryAccountRequestBody)
 */
@ExtendWith(OutputCaptureExtension.class)
class BeneficiaryAccountLifecycleApiTest extends AbstractBeneficiaryApiTest {

    /** 同币种只有一个默认：设 B 时清除 A；只有 VERIFIED 可设；归属不符 404；已是默认原样返回。 */
    @Test
    void test_setDefault() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        BeneficiaryAccount first = accounts.verified(BeneficiaryOwnerType.CLIENT, PayoutTestConstants.CLIENT_ID, "USD", past, false);
        BeneficiaryAccount second = accounts.verified(BeneficiaryOwnerType.CLIENT, PayoutTestConstants.CLIENT_ID, "USD", past, false);
        BeneficiaryAccount pending = accounts.withStatus(BeneficiaryOwnerType.CLIENT, PayoutTestConstants.CLIENT_ID, "USD",
            BeneficiaryAccountStatus.PENDING_REVIEW);

        clientCommand("default", first.getId(), PayoutTestConstants.CLIENT_ID, null, "0").assertSuccess(200)
            .assertText("version", "1");
        clientCommand("default", first.getId(), PayoutTestConstants.CLIENT_ID, null, "1").assertSuccess(200);
        clientCommand("default", second.getId(), PayoutTestConstants.CLIENT_ID, null, "0").assertSuccess(200);
        clientCommand("default", pending.getId(), PayoutTestConstants.CLIENT_ID, null, "0")
            .assertError(409, PayoutErrorCode.BENEFICIARY_STATUS_CONFLICT);
        clientCommand("default", second.getId(), PayoutTestConstants.OTHER_CLIENT_ID, null, "1")
            .assertError(404, PayoutErrorCode.BENEFICIARY_ACCOUNT_NOT_FOUND);

        Map<Long, Boolean> defaults = defaultFlags();
        assertAll(() -> assertFalse(defaults.get(first.getId())), () -> assertTrue(defaults.get(second.getId())),
            () -> assertEquals(List.of(BeneficiaryEventAction.SET_DEFAULT.getCode(), BeneficiaryEventAction.UNSET_DEFAULT.getCode()),
                testMapper.selectEventActions(first.getId())));
    }

    /** 停用：VERIFIED → SUSPENDED、清除默认、通知；恢复后重走核验与冷却；Client 成员不能停用。 */
    @Test
    void test_suspendAndResume() {
        BeneficiaryAccount verified = accounts.verified(BeneficiaryOwnerType.CLIENT, PayoutTestConstants.CLIENT_ID, "USD",
            Instant.now().minus(1, ChronoUnit.HOURS), true);
        post(ACCOUNTS + "/" + verified.getId() + "/suspend", command(null, null, BeneficiarySuspendReason.RISK_REVIEW.getCode(),
            "0", BeneficiaryActorType.CLIENT_MEMBER, PayoutTestConstants.CLIENT_MEMBER_ID)).assertBusinessRejected();
        platform.accept(BeneficiaryNotificationHandler.TEMPLATE_SUSPENDED, 1);

        PayoutResponseChecker suspended = operationsCommand("suspend", detail(verified.getId()),
            BeneficiarySuspendReason.RISK_REVIEW.getCode()).assertSuccess(200)
            .assertCode("status", BeneficiaryAccountStatus.SUSPENDED)
            .assertText("reasonCode", BeneficiarySuspendReason.RISK_REVIEW.getCode());
        assertFalse(suspended.json().get("defaultAccount").asBoolean());
        await().atMost(ASYNC_WAIT).until(() -> platform.calls().size() == 1);

        PayoutResponseChecker resumed = operationsCommand("resume", suspended, null).assertSuccess(200)
            .assertCode("status", BeneficiaryAccountStatus.PENDING_REVIEW);
        assertAll(() -> assertNull(resumed.text("coolingUntil")), () -> assertNull(resumed.text("reviewMakerId")),
            () -> assertNull(resumed.text("checkerDecision")));
        operationsCommand("resume", resumed, null).assertError(409, PayoutErrorCode.BENEFICIARY_STATUS_CONFLICT);
        platform.accept(BeneficiaryNotificationHandler.TEMPLATE_VERIFIED, 1);
        PayoutResponseChecker reviewed = makerReview(resumed, MakerReviewDecision.APPROVE, null, PayoutTestConstants.MAKER_ID)
            .assertSuccess(200);
        checkerDecision(reviewed, CheckerDecision.APPROVED, null, PayoutTestConstants.CHECKER_ID).assertSuccess(200)
            .assertCode("status", BeneficiaryAccountStatus.VERIFIED);
        await().atMost(ASYNC_WAIT).until(() -> platform.calls().size() == 2);
    }

    /** 关闭：Client 关闭自己的账户 → CLOSED 并通知；终态不能再关；缺原因 400；关闭后同账号可重新提交为新账户。 */
    @Test
    void test_close() {
        PayoutResponseChecker created = submitTemplate();
        long id = created.id("beneficiaryAccountId");
        clientCommand("close", id, PayoutTestConstants.CLIENT_ID, null, "0").assertInvalidRequest();
        post(ACCOUNTS + "/" + id + "/close", command(null, null, BeneficiaryCloseReason.OWNER_REQUEST.getCode(), "0",
            BeneficiaryActorType.CLIENT_MEMBER, PayoutTestConstants.CLIENT_MEMBER_ID)).assertInvalidRequest();
        clientCommand("close", id, PayoutTestConstants.OTHER_CLIENT_ID, BeneficiaryCloseReason.OWNER_REQUEST.getCode(), "0")
            .assertError(404, PayoutErrorCode.BENEFICIARY_ACCOUNT_NOT_FOUND);
        platform.accept(BeneficiaryNotificationHandler.TEMPLATE_CLOSED, 1);

        clientCommand("close", id, PayoutTestConstants.CLIENT_ID, BeneficiaryCloseReason.OWNER_REQUEST.getCode(), "0")
            .assertSuccess(200).assertCode("status", BeneficiaryAccountStatus.CLOSED);
        clientCommand("close", id, PayoutTestConstants.CLIENT_ID, BeneficiaryCloseReason.OWNER_REQUEST.getCode(), "1")
            .assertError(409, PayoutErrorCode.BENEFICIARY_STATUS_CONFLICT);
        await().atMost(ASYNC_WAIT).until(() -> platform.calls().size() == 1);
        assertEquals(BeneficiaryOwnerType.CLIENT.getCode(), platform.calls().getFirst().getTenantRecipients().getTenantType());

        submit(SubmitBeneficiaryAccountBuilder.template().build()).assertSuccess(201).assertText("accountCode", "BA-0002");
    }

    /** Reveal：只对 Operations BFF 开放，返回原值与 no-store，记 REVEAL 事件；其他调用方 403 且不调用 Vault；日志无原值。 */
    @Test
    void test_reveal(CapturedOutput output) {
        PayoutResponseChecker created = submitTemplate();
        String path = ACCOUNTS + "/" + created.text("beneficiaryAccountId") + "/reveal";
        Map<String, String> body = Map.of("actorId", String.valueOf(PayoutTestConstants.MAKER_ID), "reasonCode",
            BeneficiaryRevealReason.MAKER_REVIEW.getCode());

        post(path, body).assertError(403, PayoutErrorCode.BENEFICIARY_REVEAL_FORBIDDEN);
        assertEquals(0, vault.revealCount());
        vault.acceptReveal();
        PayoutResponseChecker revealed = post(path, caller(PayoutTestConstants.OPERATIONS_BFF), body).assertSuccess(200)
            .assertHeader("Cache-Control", "no-store").assertText("iban", PayoutTestConstants.AE_IBAN)
            .assertText("holderName", PayoutTestConstants.HOLDER_NAME);
        assertAll(() -> assertNull(revealed.text("accountNumber")), () -> assertEquals(1, vault.revealCount()),
            () -> assertEquals(List.of(BeneficiaryEventAction.SUBMIT.getCode(), BeneficiaryEventAction.REVEAL.getCode()),
                testMapper.selectEventActions(created.id("beneficiaryAccountId"))),
            () -> assertFalse(testMapper.selectEventRows().toString().contains(PayoutTestConstants.AE_IBAN)),
            () -> assertFalse(output.getAll().contains(PayoutTestConstants.AE_IBAN), "原值进入日志"));
    }

    /** Client 成员以自己的 Owner 执行状态命令。 */
    PayoutResponseChecker clientCommand(String action, long accountId, long ownerId, String reasonCode,
        String expectedVersion) {
        return post(ACCOUNTS + "/" + accountId + "/" + action, command(BeneficiaryOwnerType.CLIENT, ownerId, reasonCode,
            expectedVersion, BeneficiaryActorType.CLIENT_MEMBER, PayoutTestConstants.CLIENT_MEMBER_ID));
    }

    /** 运营读取详情（取当前版本与摘要）。 */
    PayoutResponseChecker detail(long accountId) {
        return get(ACCOUNTS + "/" + accountId, caller(PayoutTestConstants.OPERATIONS_BFF)).assertSuccess(200);
    }

    /** 各账户的默认标记。 */
    Map<Long, Boolean> defaultFlags() {
        return testMapper.selectAccountRows().stream().collect(java.util.stream.Collectors.toMap(
            row -> ((Number)row.get("id")).longValue(), row -> isTrue(row.get("is_default"))));
    }

    /** TINYINT 标记为 1（驱动可能返回 Integer 或 Boolean）。 */
    static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() == 1;
    }
}
