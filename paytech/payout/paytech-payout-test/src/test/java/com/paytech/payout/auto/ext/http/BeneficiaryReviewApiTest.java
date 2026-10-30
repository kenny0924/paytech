package com.paytech.payout.auto.ext.http;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.payout.api.beneficiary.request.CheckerDecisionRequestBody;
import com.paytech.payout.api.beneficiary.request.MakerReviewRequestBody;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryCloseReason;
import com.paytech.payout.api.enums.BeneficiaryEventAction;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRejectReason;
import com.paytech.payout.api.enums.BeneficiaryReviewStage;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.api.enums.PayoutErrorCode;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.payout.service.handler.BeneficiaryNotificationHandler;
import com.paytech.payout.test.base.AbstractBeneficiaryApiTest;
import com.paytech.payout.test.builder.SubmitBeneficiaryAccountBuilder;
import com.paytech.payout.test.checker.PayoutResponseChecker;
import com.paytech.payout.test.support.PayoutTestConstants;

/**
 * Maker / Checker 核验与核验队列（AC-03、AC-09、AC-10，B3、B4、B10）
 * <li>Maker 意见不改状态；Checker 必须是另一人，批准 → VERIFIED 并冷却 24 小时、拒绝 → REJECTED；两者都经补偿任务通知 Owner。</li>
 *
 * @see com.paytech.payout.api.beneficiary.BeneficiaryAccountApi#submitMakerReview(String, MakerReviewRequestBody)
 * @see com.paytech.payout.api.beneficiary.BeneficiaryAccountApi#submitCheckerDecision(String, CheckerDecisionRequestBody)
 * @see com.paytech.payout.api.beneficiary.BeneficiaryReviewApi#listBeneficiaryReviews(String, String, String, Integer, Integer)
 */
class BeneficiaryReviewApiTest extends AbstractBeneficiaryApiTest {
    /** Maker 内部备注。 */
    static final String MAKER_NOTE = "户名与法定名一致；共用账号为同一集团的关联 Partner";

    /** Maker 建议拒绝时给 Client 的说明。 */
    static final String MAKER_CLIENT_NOTE = "户名与营业执照名称不一致，请使用公司名下账户重新添加。";

    /** Checker 驳回时给 Client 的说明。 */
    static final String CHECKER_CLIENT_NOTE = "请补充银行出具的账户证明后重新添加。";

    /** 提交 → 待 Maker → Maker 通过 → 待 Checker → 批准：VERIFIED，冷却结束 = 决定时间 + 24h，通知参数不含原值。 */
    @Test
    void test_makerThenCheckerApproves() {
        PayoutResponseChecker submitted = submitTemplate();
        String id = submitted.text("beneficiaryAccountId");
        assertQueue(BeneficiaryReviewStage.MAKER, id);

        PayoutResponseChecker reviewed = makerReview(submitted, MakerReviewDecision.APPROVE, null, PayoutTestConstants.MAKER_ID)
            .assertSuccess(200).assertCode("status", BeneficiaryAccountStatus.PENDING_REVIEW)
            .assertCode("reviewMakerDecision", MakerReviewDecision.APPROVE);
        assertQueue(BeneficiaryReviewStage.CHECKER, id);
        platform.accept(BeneficiaryNotificationHandler.TEMPLATE_VERIFIED, 2);

        PayoutResponseChecker verified = checkerDecision(reviewed, CheckerDecision.APPROVED, null,
            PayoutTestConstants.CHECKER_ID).assertSuccess(200).assertCode("status", BeneficiaryAccountStatus.VERIFIED);
        Instant decided = Instant.parse(verified.text("decisionTime"));
        Instant coolingUntil = Instant.parse(verified.text("coolingUntil"));
        assertAll(() -> assertEquals(Duration.ofHours(24), Duration.between(decided, coolingUntil)),
            () -> assertFalse(verified.json().get("usable").asBoolean()),
            () -> assertEquals(List.of(BeneficiaryEventAction.SUBMIT.getCode(), BeneficiaryEventAction.MAKER_REVIEW.getCode(),
                BeneficiaryEventAction.CHECKER_DECIDE.getCode()), testMapper.selectEventActions(Long.parseLong(id))));
        SubmitNotificationRequestBody notification = awaitNotification();
        assertVerifiedNotification(notification, id, verified.text("coolingUntil"));
        assertQueue(BeneficiaryReviewStage.DONE, id);
    }

    /** Checker 与 Maker 同一人、或与运营代提交人同一人：403，状态不变。 */
    @Test
    void test_fourEyesRequired() {
        PayoutResponseChecker submitted = submitTemplate();
        PayoutResponseChecker reviewed = makerReview(submitted, MakerReviewDecision.APPROVE, null,
            PayoutTestConstants.MAKER_ID).assertSuccess(200);
        checkerDecision(reviewed, CheckerDecision.APPROVED, null, PayoutTestConstants.MAKER_ID)
            .assertError(403, PayoutErrorCode.FOUR_EYES_REQUIRED);

        PayoutResponseChecker byOperations = submit(SubmitBeneficiaryAccountBuilder.template().iban(PayoutTestConstants.AE_IBAN_2)
            .byOperations(PayoutTestConstants.OPS_SUBMITTER_ID).build()).assertSuccess(201);
        PayoutResponseChecker opsReviewed = makerReview(byOperations, MakerReviewDecision.APPROVE, null,
            PayoutTestConstants.MAKER_ID).assertSuccess(200);
        checkerDecision(opsReviewed, CheckerDecision.APPROVED, null, PayoutTestConstants.OPS_SUBMITTER_ID)
            .assertError(403, PayoutErrorCode.FOUR_EYES_REQUIRED);
        assertEquals(List.of(BeneficiaryAccountStatus.PENDING_REVIEW.getCode(), BeneficiaryAccountStatus.PENDING_REVIEW.getCode()),
            testMapper.selectAccountRows().stream().map(row -> row.get("status").toString()).toList());
    }

    /** Maker 建议拒绝时不能批准；拒绝必须带原因；拒绝 → REJECTED 并通知原因。 */
    @Test
    void test_rejectFlow() {
        PayoutResponseChecker submitted = submitTemplate();
        makerReview(submitted, MakerReviewDecision.REJECT, null, PayoutTestConstants.MAKER_ID).assertInvalidRequest();
        PayoutResponseChecker reviewed = makerReview(submitted, MakerReviewDecision.REJECT,
            BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode(), PayoutTestConstants.MAKER_ID).assertSuccess(200);
        checkerDecision(reviewed, CheckerDecision.APPROVED, null, PayoutTestConstants.CHECKER_ID)
            .assertError(409, PayoutErrorCode.BENEFICIARY_STATUS_CONFLICT);
        checkerDecision(reviewed, CheckerDecision.REJECTED, BeneficiaryCloseReason.REPLACED.getCode(),
            PayoutTestConstants.CHECKER_ID).assertInvalidRequest();
        platform.accept(BeneficiaryNotificationHandler.TEMPLATE_REJECTED, 1);

        checkerDecision(reviewed, CheckerDecision.REJECTED, BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode(),
            PayoutTestConstants.CHECKER_ID).assertSuccess(200).assertCode("status", BeneficiaryAccountStatus.REJECTED)
            .assertText("reasonCode", BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode());
        SubmitNotificationRequestBody notification = awaitNotification();
        assertAll(() -> assertEquals(BeneficiaryNotificationHandler.TEMPLATE_REJECTED, notification.getTemplateCode()),
            () -> assertEquals(BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode(),
                notification.getDefaultParameters().get("reasonText")),
            () -> assertNull(notification.getDefaultParameters().get("availableAt")));
    }

    /**
     * 核验备注与给 Client 的说明（原型 O2 / O5）：Maker 备注只在运营视图；说明只在拒绝时可填、超长 422；
     * Checker 未填时沿用 Maker 的说明，填写时覆盖；拒绝通知的 reasonText = 原因代码 + 说明。
     */
    @Test
    void test_reviewNotes() {
        PayoutResponseChecker first = submitTemplate();
        makerReview(first, MakerReviewDecision.APPROVE, null, PayoutTestConstants.MAKER_ID, null, MAKER_CLIENT_NOTE)
            .assertInvalidRequest();
        makerReview(first, MakerReviewDecision.REJECT, BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode(),
            PayoutTestConstants.MAKER_ID, "x".repeat(501), null).assertValidationError();
        PayoutResponseChecker reviewed = makerReview(first, MakerReviewDecision.REJECT,
            BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode(), PayoutTestConstants.MAKER_ID, MAKER_NOTE,
            "  " + MAKER_CLIENT_NOTE + "  ").assertSuccess(200).assertText("reasonNote", MAKER_CLIENT_NOTE);
        String path = ACCOUNTS + "/" + first.text("beneficiaryAccountId");
        get(path, caller(PayoutTestConstants.OPERATIONS_BFF)).assertSuccess(200).assertText("reviewMakerNote", MAKER_NOTE);
        String clientView = path + "?ownerType=" + BeneficiaryOwnerType.CLIENT.getCode() + "&ownerId="
            + PayoutTestConstants.CLIENT_ID;
        assertNull(get(clientView).assertSuccess(200).text("reviewMakerNote"));
        platform.accept(BeneficiaryNotificationHandler.TEMPLATE_REJECTED, 1);
        checkerDecision(reviewed, CheckerDecision.REJECTED, BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode(),
            PayoutTestConstants.CHECKER_ID, " ").assertSuccess(200).assertText("reasonNote", MAKER_CLIENT_NOTE);
        assertEquals(BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode() + ": " + MAKER_CLIENT_NOTE,
            awaitNotification().getDefaultParameters().get("reasonText"));

        platform.clear();
        PayoutResponseChecker second = submit(SubmitBeneficiaryAccountBuilder.template().iban(PayoutTestConstants.AE_IBAN_2)
            .build()).assertSuccess(201);
        PayoutResponseChecker secondReviewed = makerReview(second, MakerReviewDecision.REJECT,
            BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode(), PayoutTestConstants.MAKER_ID).assertSuccess(200);
        platform.accept(BeneficiaryNotificationHandler.TEMPLATE_REJECTED, 1);
        checkerDecision(secondReviewed, CheckerDecision.REJECTED, BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode(),
            PayoutTestConstants.CHECKER_ID, CHECKER_CLIENT_NOTE).assertSuccess(200)
            .assertText("reasonNote", CHECKER_CLIENT_NOTE);
        assertEquals(BeneficiaryRejectReason.HOLDER_NAME_MISMATCH.getCode() + ": " + CHECKER_CLIENT_NOTE,
            awaitNotification().getDefaultParameters().get("reasonText"));
    }

    /** 摘要或版本不符 409；重复 Maker 意见与无 Maker 意见的复核为状态冲突。 */
    @Test
    void test_conflicts() {
        PayoutResponseChecker submitted = submitTemplate();
        String path = ACCOUNTS + "/" + submitted.text("beneficiaryAccountId");
        checkerDecision(submitted, CheckerDecision.APPROVED, null, PayoutTestConstants.CHECKER_ID)
            .assertError(409, PayoutErrorCode.BENEFICIARY_STATUS_CONFLICT);
        post(path + "/maker-reviews", caller(PayoutTestConstants.OPERATIONS_BFF), Map.of("decision",
            MakerReviewDecision.APPROVE.getCode(), "contentHash", "0".repeat(64), "expectedVersion", submitted.text("version"),
            "actorId", String.valueOf(PayoutTestConstants.MAKER_ID))).assertConcurrentRequest();
        post(path + "/maker-reviews", caller(PayoutTestConstants.OPERATIONS_BFF), Map.of("decision",
            MakerReviewDecision.APPROVE.getCode(), "contentHash", submitted.text("contentHash"), "expectedVersion", "7",
            "actorId", String.valueOf(PayoutTestConstants.MAKER_ID))).assertConcurrentRequest();

        PayoutResponseChecker reviewed = makerReview(submitted, MakerReviewDecision.APPROVE, null,
            PayoutTestConstants.MAKER_ID).assertSuccess(200);
        makerReview(reviewed, MakerReviewDecision.APPROVE, null, PayoutTestConstants.CHECKER_ID)
            .assertError(409, PayoutErrorCode.BENEFICIARY_STATUS_CONFLICT);
        checkerDecision(submitted, CheckerDecision.APPROVED, null, PayoutTestConstants.CHECKER_ID).assertConcurrentRequest();
    }

    /** 队列每页最多 50；阶段非法 400。 */
    @Test
    void test_queueParameters() {
        get(REVIEWS + "?stage=MAKER&size=100", caller(PayoutTestConstants.OPERATIONS_BFF)).assertInvalidRequest();
        get(REVIEWS + "?stage=OTHER", caller(PayoutTestConstants.OPERATIONS_BFF)).assertInvalidRequest();
        get(REVIEWS + "?stage=CHECKER&size=50", caller(PayoutTestConstants.OPERATIONS_BFF)).assertSuccess(200)
            .assertText("total", "0");
    }

    /** 阶段队列恰好包含该账户。 */
    void assertQueue(BeneficiaryReviewStage stage, String id) {
        PayoutResponseChecker queue = get(REVIEWS + "?stage=" + stage.getCode(), caller(PayoutTestConstants.OPERATIONS_BFF))
            .assertSuccess(200);
        assertAll(() -> assertEquals(1, queue.json().get("total").asLong()),
            () -> assertEquals(id, queue.json().get("items").get(0).get("beneficiaryAccountId").asString()),
            () -> assertEquals(0, queue.json().get("items").get(0).get("sharedOwners").size()));
    }

    /** 等待补偿任务调用 Platform 并删除任务。 */
    SubmitNotificationRequestBody awaitNotification() {
        await().atMost(ASYNC_WAIT).until(() -> platform.calls().size() == 1 && testMapper.countCompensationEvents() == 0);
        return platform.calls().getFirst();
    }

    /** 核验通过通知：去重键、模板、租户收件人与非敏感参数。 */
    static void assertVerifiedNotification(SubmitNotificationRequestBody notification, String id, String coolingUntil) {
        Map<String, String> parameters = notification.getDefaultParameters();
        assertAll(() -> assertEquals("BENEFICIARY:" + id + ":3", notification.getSourceEventKey()),
            () -> assertEquals(BeneficiaryNotificationHandler.TEMPLATE_VERIFIED, notification.getTemplateCode()),
            () -> assertEquals("CLIENT", notification.getTenantRecipients().getTenantType()),
            () -> assertEquals(String.valueOf(PayoutTestConstants.CLIENT_ID), notification.getTenantRecipients().getOwnerRefId()),
            () -> assertEquals("CLIENT_PAYOUT_ACCOUNT_MANAGE", notification.getTenantRecipients().getPermissionCode()),
            () -> assertEquals(PayoutTestConstants.BANK_NAME, parameters.get("bankName")),
            () -> assertEquals("3456", parameters.get("accountLast4")),
            () -> assertEquals("USD", parameters.get("currency")),
            () -> assertEquals(Instant.parse(coolingUntil), Instant.parse(parameters.get("availableAt"))),
            () -> assertFalse(parameters.toString().contains(PayoutTestConstants.AE_IBAN)),
            () -> assertFalse(parameters.toString().contains(PayoutTestConstants.HOLDER_NAME)));
    }

}
