package com.paytech.risk.auto.ext.http.control;

import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode.RISK_UNDERWRITING_DECLINE_REQUIRES_REVIEW;
import static com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode.RISK_UNDERWRITING_NOT_FOUND;
import static com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode.RISK_UNDERWRITING_OPEN_EXISTS;
import static com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode.RISK_UNDERWRITING_SELF_REVIEW;
import static com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode.RISK_UNDERWRITING_STATE_INVALID;
import static com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode.RISK_UNDERWRITING_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.risk.control.core.enums.FulfillmentType;
import com.paytech.risk.control.core.enums.UnderwritingDecisionSource;
import com.paytech.risk.control.core.enums.UnderwritingOutcome;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.core.enums.UnderwritingStatus;
import com.paytech.risk.decision.api.control.ClientUnderwritingApi;
import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;
import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;
import com.paytech.risk.test.base.AbstractUnderwritingApiTest;
import com.paytech.risk.test.builder.control.UnderwritingRequestBuilder;
import com.paytech.risk.test.checker.control.UnderwritingResponseChecker;
import com.paytech.risk.test.mapper.control.UnderwritingTestMapper;

/**
 * 承保版本生命周期：建草稿、保存、提交、四眼复核、修订替换与并发
 * <li>LOW / MEDIUM 提交即决定（AC-03）；HIGH 提交待复核、复核人不能是提交人（AC-04）；修订批准前旧版本有效（AC-05）。</li>
 * <li>提交冻结快照与来源坐标（AC-06）；提交必填与 CRR 缺失按 MEDIUM（AC-07）；同 Client 并发建草稿只有一个成功（AC-09）。</li>
 *
 * @see ClientUnderwritingApi#createUnderwritingDraft(String, String, String, com.paytech.risk.decision.api.control.request.CreateUnderwritingDraftRequestBody)
 * @see ClientUnderwritingApi#saveUnderwritingDraft(String, String, String, com.paytech.risk.decision.api.control.request.SaveUnderwritingDraftRequestBody)
 * @see ClientUnderwritingApi#submitUnderwriting(String, String, String, com.paytech.risk.decision.api.control.request.SubmitUnderwritingRequestBody)
 * @see ClientUnderwritingApi#reviewUnderwriting(String, String, String, com.paytech.risk.decision.api.control.request.ReviewUnderwritingRequestBody)
 * @see ClientUnderwritingApi#reviseUnderwriting(String, String, String, com.paytech.risk.decision.api.control.request.ReviseUnderwritingRequestBody)
 * @see ClientUnderwritingApi#getUnderwriting(String, String, String)
 */
class ClientUnderwritingLifecycleApiAutoTest extends AbstractUnderwritingApiTest {

    /** LOW：建草稿 → 保存 → 提交即 APPROVED；冻结快照、来源坐标与决定人落库。 */
    @Test
    void test_normalFlow_lowApprovedOnSubmit() {
        long clientId = newClientId();
        UnderwritingResponseChecker draft = createDraft(clientId, UnderwritingRequestBuilder.input(), null)
            .assertUnderwriting(201, UnderwritingStatus.DRAFT.getCode(), UnderwritingRiskLevel.LOW.getCode())
            .assertText("/versionNo", "1").assertText("/createdBy", Long.toString(UNDERWRITER));
        UnderwritingDecisionBody decision = UnderwritingRequestBuilder.decision();
        UnderwritingResponseChecker saved = put(UNDERWRITER, ROOT + "/underwritings/" + draft.id(),
            UnderwritingRequestBuilder.save(draft.version(), UnderwritingRequestBuilder.input(), decision))
            .assertUnderwriting(200, UnderwritingStatus.DRAFT.getCode(), UnderwritingRiskLevel.LOW.getCode())
            .assertText("/decision/limitNote", decision.getLimitNote());

        submit(draft.id(), saved.version(), UnderwritingRequestBuilder.input(), decision, UnderwritingOutcome.APPROVE, null)
            .assertUnderwriting(200, UnderwritingStatus.APPROVED.getCode(), UnderwritingRiskLevel.LOW.getCode())
            .assertText("/decisionSource", UnderwritingDecisionSource.MANUAL.getCode())
            .assertText("/submittedBy", Long.toString(UNDERWRITER)).assertText("/decidedBy", Long.toString(UNDERWRITER))
            .assertText("/input/sourceCaseId", "7001").assertText("/input/sourceDraftRevision", "5")
            .assertText("/input/sourceClassificationConfirmedAt", "2026-10-20T08:00:00Z")
            .assertExposure("/estimatedExposure", "15000.00", "USD", 15, false)
            .assertFieldNames("", Set.of("underwritingId", "clientId", "versionNo", "status", "decisionSource", "ruleVersionNo",
                "reviewLevel", "input", "decision", "estimatedExposure", "exposureUnavailableReason", "declineReason",
                "reviewComment", "revisionReason", "supersedesUnderwritingId", "createdBy", "submittedBy", "submittedAt",
                "decidedBy", "decidedAt", "supersededAt", "createdAt", "updatedAt", "version"))
            .assertFieldNames("/input", Set.of("clientDisplayName", "clientNo", "clientType", "jurisdictionCode", "crr",
                "crrMissing", "ddTier", "mccCode", "industryCode", "networkHighRiskMcc", "expectedMonthlyVolume", "volumeCurrency",
                "expectedTicket", "fulfillmentType", "fulfillmentDays", "websiteUrl", "refundPolicyUrl", "expectedPaymentMethods",
                "sourceCaseId", "sourceCaseVersion", "sourceApplicationId", "sourceDraftRevision", "sourceClassificationConfirmedAt"))
            .assertFieldNames("/decision", Set.of("observationDays", "observationTransactions", "requireDeposit", "depositAmount",
                "depositCurrency", "depositBlocksCollection", "suggestedSettlementDays", "highRiskMcc", "highRiskMccReason",
                "limitNote", "underwritingNote", "handoverNote"))
            .assertFieldNames("/estimatedExposure", Set.of("amount", "currency", "riskDays", "settlementDaysAssumed", "formula"));

        Map<String, Object> row = store.underwriting(draft.id());
        assertAll("落库版本",
            () -> assertEquals(UnderwritingStatus.APPROVED.getCode(), row.get("status")),
            () -> assertNull(row.get("open_slot")),
            () -> assertTrue(UnderwritingTestMapper.flag(row.get("effective_slot"))),
            () -> assertEquals(UNDERWRITER, ((Number) row.get("decided_by")).longValue()),
            () -> assertEquals(0, new java.math.BigDecimal("15000").compareTo((java.math.BigDecimal) row.get("estimated_exposure"))),
            () -> assertEquals(15, ((Number) row.get("exposure_risk_days")).intValue()),
            () -> assertEquals(3L, ((Number) row.get("version")).longValue()));
    }

    /** MEDIUM 拒绝须写原因：无原因 422，有原因 DECLINED 且不占有效槽位。 */
    @Test
    void test_normalFlow_mediumDeclined() {
        long clientId = newClientId();
        UnderwritingInputBody input = UnderwritingRequestBuilder.input(UnderwritingRiskLevel.MEDIUM);
        UnderwritingResponseChecker draft = createDraft(clientId, input, UnderwritingRequestBuilder.decision());

        submit(draft.id(), draft.version(), input, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.DECLINE, null)
            .assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("declineReason");
        submit(draft.id(), draft.version(), input, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.DECLINE, "网站无法访问")
            .assertUnderwriting(200, UnderwritingStatus.DECLINED.getCode(), UnderwritingRiskLevel.MEDIUM.getCode())
            .assertText("/declineReason", "网站无法访问");

        Map<String, Object> row = store.underwriting(draft.id());
        assertAll("拒绝版本",
            () -> assertNull(row.get("open_slot")),
            () -> assertNull(row.get("effective_slot")));
        createDraft(clientId, input, null).assertUnderwriting(201, UnderwritingStatus.DRAFT.getCode(),
            UnderwritingRiskLevel.MEDIUM.getCode()).assertText("/versionNo", "2");
    }

    /** HIGH：提交为 SUBMITTED；提交人复核 409；他人批准，复核意见落库。 */
    @Test
    void test_normalFlow_highFourEyesApprove() {
        long clientId = newClientId();
        UnderwritingInputBody input = UnderwritingRequestBuilder.input(UnderwritingRiskLevel.HIGH);
        UnderwritingResponseChecker draft = createDraft(clientId, input, UnderwritingRequestBuilder.decision());
        UnderwritingResponseChecker submitted = submit(draft.id(), draft.version(), input, UnderwritingRequestBuilder.decision(),
            UnderwritingOutcome.APPROVE, null)
            .assertUnderwriting(200, UnderwritingStatus.SUBMITTED.getCode(), UnderwritingRiskLevel.HIGH.getCode())
            .assertText("/submittedBy", Long.toString(UNDERWRITER)).assertText("/decidedBy", null);

        post(UNDERWRITER, ROOT + "/underwritings/" + draft.id() + "/review",
            UnderwritingRequestBuilder.review(submitted.version(), UnderwritingOutcome.APPROVE, null))
            .assertProblem(409, RISK_UNDERWRITING_SELF_REVIEW.getCode());
        post(RISK_LEAD, ROOT + "/underwritings/" + draft.id() + "/review",
            UnderwritingRequestBuilder.review(submitted.version(), UnderwritingOutcome.APPROVE, "同意，观察期内复看"))
            .assertUnderwriting(200, UnderwritingStatus.APPROVED.getCode(), UnderwritingRiskLevel.HIGH.getCode())
            .assertText("/decidedBy", Long.toString(RISK_LEAD)).assertText("/submittedBy", Long.toString(UNDERWRITER))
            .assertText("/reviewComment", "同意，观察期内复看");
        assertTrue(UnderwritingTestMapper.flag(store.underwriting(draft.id()).get("effective_slot")));
    }

    /** HIGH：承保员提交拒绝 409；复核拒绝无意见 422，有意见 DECLINED 且意见作为拒绝原因。 */
    @Test
    void test_businessRuleViolation_highDecline() {
        long clientId = newClientId();
        UnderwritingInputBody input = UnderwritingRequestBuilder.input(UnderwritingRiskLevel.HIGH);
        UnderwritingResponseChecker draft = createDraft(clientId, input, UnderwritingRequestBuilder.decision());
        submit(draft.id(), draft.version(), input, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.DECLINE, "风险过高")
            .assertProblem(409, RISK_UNDERWRITING_DECLINE_REQUIRES_REVIEW.getCode());
        UnderwritingResponseChecker submitted = submit(draft.id(), draft.version(), input,
            UnderwritingRequestBuilder.decision(), UnderwritingOutcome.APPROVE, null);

        post(RISK_LEAD, ROOT + "/underwritings/" + draft.id() + "/review",
            UnderwritingRequestBuilder.review(submitted.version(), UnderwritingOutcome.DECLINE, null))
            .assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("comment");
        post(RISK_LEAD, ROOT + "/underwritings/" + draft.id() + "/review",
            UnderwritingRequestBuilder.review(submitted.version(), UnderwritingOutcome.DECLINE, "交付周期过长"))
            .assertUnderwriting(200, UnderwritingStatus.DECLINED.getCode(), UnderwritingRiskLevel.HIGH.getCode())
            .assertText("/declineReason", "交付周期过长").assertText("/reviewComment", null);
    }

    /** 修订：v2 批准前 v1 仍有效；v2 被拒绝 v1 不变；再修订 v3 批准后 v1 SUPERSEDED、有效为 v3。 */
    @Test
    void test_normalFlow_revisionSupersedes() {
        long clientId = newClientId();
        UnderwritingResponseChecker v1 = approved(clientId, UnderwritingRequestBuilder.input());
        UnderwritingInputBody latest = UnderwritingRequestBuilder.input();
        latest.setExpectedMonthlyVolume("45000.00");
        latest.setSourceDraftRevision("6");

        post(UNDERWRITER, ROOT + "/underwritings/" + v1.id() + "/revision",
            UnderwritingRequestBuilder.revise(v1.version(), " ", latest))
            .assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("revisionReason");
        UnderwritingResponseChecker v2 = post(UNDERWRITER, ROOT + "/underwritings/" + v1.id() + "/revision",
            UnderwritingRequestBuilder.revise(v1.version(), "流水上调", latest))
            .assertUnderwriting(201, UnderwritingStatus.DRAFT.getCode(), UnderwritingRiskLevel.LOW.getCode())
            .assertText("/versionNo", "2").assertText("/revisionReason", "流水上调")
            .assertText("/input/expectedMonthlyVolume", "45000.00").assertText("/input/sourceDraftRevision", "6")
            .assertText("/decision/limitNote", UnderwritingRequestBuilder.decision().getLimitNote());
        get(UNDERWRITER, ROOT + "/clients/" + clientId + "/underwriting").assertCurrent(1, 2, 2);

        submit(v2.id(), v2.version(), latest, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.DECLINE, "暂不放量")
            .assertUnderwriting(200, UnderwritingStatus.DECLINED.getCode(), UnderwritingRiskLevel.LOW.getCode());
        get(UNDERWRITER, ROOT + "/clients/" + clientId + "/underwriting").assertCurrent(1, null, 2);

        UnderwritingResponseChecker v3 = post(UNDERWRITER, ROOT + "/underwritings/" + v1.id() + "/revision",
            UnderwritingRequestBuilder.revise(v1.version(), "补充材料后重提", latest));
        submit(v3.id(), v3.version(), latest, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.APPROVE, null)
            .assertUnderwriting(200, UnderwritingStatus.APPROVED.getCode(), UnderwritingRiskLevel.LOW.getCode())
            .assertText("/supersedesUnderwritingId", Long.toString(v1.id()));
        get(UNDERWRITER, ROOT + "/underwritings/" + v1.id())
            .assertUnderwriting(200, UnderwritingStatus.SUPERSEDED.getCode(), UnderwritingRiskLevel.LOW.getCode());
        assertTrue(get(UNDERWRITER, ROOT + "/underwritings/" + v1.id()).text("/supersededAt") != null, "supersededAt");
        get(UNDERWRITER, ROOT + "/clients/" + clientId + "/underwriting").assertCurrent(3, null, 3);
    }

    /** 已决定版本不能再保存、提交或修订（非有效版本）；过期期望版本 409；ID 不存在 404。 */
    @Test
    void test_businessRuleViolation_stateAndVersion() {
        long clientId = newClientId();
        UnderwritingResponseChecker draft = createDraft(clientId, UnderwritingRequestBuilder.input(), null);
        put(UNDERWRITER, ROOT + "/underwritings/" + draft.id(),
            UnderwritingRequestBuilder.save(draft.version() + 1, UnderwritingRequestBuilder.input(), null))
            .assertProblem(409, RISK_UNDERWRITING_VERSION_CONFLICT.getCode());
        createDraft(clientId, UnderwritingRequestBuilder.input(), null).assertProblem(409, RISK_UNDERWRITING_OPEN_EXISTS.getCode());

        UnderwritingResponseChecker approved = submit(draft.id(), draft.version(), UnderwritingRequestBuilder.input(),
            UnderwritingRequestBuilder.decision(), UnderwritingOutcome.APPROVE, null);
        put(UNDERWRITER, ROOT + "/underwritings/" + draft.id(),
            UnderwritingRequestBuilder.save(approved.version(), UnderwritingRequestBuilder.input(), null))
            .assertProblem(409, RISK_UNDERWRITING_STATE_INVALID.getCode());
        post(RISK_LEAD, ROOT + "/underwritings/" + draft.id() + "/review",
            UnderwritingRequestBuilder.review(approved.version(), UnderwritingOutcome.APPROVE, null))
            .assertProblem(409, RISK_UNDERWRITING_STATE_INVALID.getCode());
        post(UNDERWRITER, ROOT + "/underwritings/" + draft.id() + "/revision",
            UnderwritingRequestBuilder.revise(approved.version() - 1, "过期版本", UnderwritingRequestBuilder.input()))
            .assertProblem(409, RISK_UNDERWRITING_VERSION_CONFLICT.getCode());

        get(UNDERWRITER, ROOT + "/underwritings/9876543210").assertProblem(404, RISK_UNDERWRITING_NOT_FOUND.getCode());
        put(UNDERWRITER, ROOT + "/underwritings/9876543210",
            UnderwritingRequestBuilder.save(1, UnderwritingRequestBuilder.input(), null))
            .assertProblem(404, RISK_UNDERWRITING_NOT_FOUND.getCode());
    }

    /** 已提交的快照不随来源变化：重新取得的新输入不能再写入已决定版本，详情仍是提交时的值。 */
    @Test
    void test_edgeCase_snapshotFrozen() {
        long clientId = newClientId();
        UnderwritingResponseChecker approved = approved(clientId, UnderwritingRequestBuilder.input());
        UnderwritingInputBody changed = UnderwritingRequestBuilder.input();
        changed.setMccCode("5999");
        changed.setSourceCaseVersion("9");
        submit(approved.id(), approved.version(), changed, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.APPROVE,
            null).assertProblem(409, RISK_UNDERWRITING_STATE_INVALID.getCode());
        get(UNDERWRITER, ROOT + "/underwritings/" + approved.id()).assertText("/input/mccCode", "5732")
            .assertText("/input/sourceCaseVersion", "3").assertText("/input/sourceApplicationId", "6001");
    }

    /** 存量尽调无 CRR：复核级别 MEDIUM、承保员提交即决定。 */
    @Test
    void test_edgeCase_missingCrrTreatedAsMedium() {
        long clientId = newClientId();
        UnderwritingInputBody input = UnderwritingRequestBuilder.input(null);
        UnderwritingResponseChecker draft = createDraft(clientId, input, UnderwritingRequestBuilder.decision())
            .assertUnderwriting(201, UnderwritingStatus.DRAFT.getCode(), UnderwritingRiskLevel.MEDIUM.getCode())
            .assertText("/input/crrMissing", "true").assertText("/input/crr", null);
        submit(draft.id(), draft.version(), input, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.APPROVE, null)
            .assertUnderwriting(200, UnderwritingStatus.APPROVED.getCode(), UnderwritingRiskLevel.MEDIUM.getCode());
        assertNull(store.underwriting(draft.id()).get("crr"));
    }

    /** 提交必填与字段组合（B2 / B5）返回 422 并定位字段；草稿保存只校验组合。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("submissionViolations")
    void test_invalidParameter_submission(String field, Consumer<UnderwritingInputBody> inputChange,
        Consumer<UnderwritingDecisionBody> decisionChange) {
        long clientId = newClientId();
        UnderwritingResponseChecker draft = createDraft(clientId, UnderwritingRequestBuilder.input(), null);
        UnderwritingInputBody input = UnderwritingRequestBuilder.input();
        UnderwritingDecisionBody decision = UnderwritingRequestBuilder.decision();
        inputChange.accept(input);
        decisionChange.accept(decision);

        submit(draft.id(), draft.version(), input, decision, UnderwritingOutcome.APPROVE, null)
            .assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);
        assertEquals(UnderwritingStatus.DRAFT.getCode(), store.underwriting(draft.id()).get("status"));
    }

    /** 草稿也拒绝的字段组合；请求格式与范围由 Body 注解返回 422。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("draftViolations")
    void test_invalidParameter_draft(String field, Consumer<UnderwritingInputBody> inputChange,
        Consumer<UnderwritingDecisionBody> decisionChange) {
        UnderwritingInputBody input = UnderwritingRequestBuilder.input();
        UnderwritingDecisionBody decision = UnderwritingRequestBuilder.decision();
        inputChange.accept(input);
        decisionChange.accept(decision);
        createDraft(newClientId(), input, decision).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);
    }

    /** 同一 Client 并发建草稿只有一个成功，另一个为已有进行中或并发冲突。 */
    @Test
    void test_edgeCase_concurrentCreate() throws Exception {
        long clientId = newClientId();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    UnderwritingResponseChecker checker = createDraft(clientId, UnderwritingRequestBuilder.input(), null);
                    if (checker.status() == 409) {
                        String code = checker.text("/code");
                        assertTrue(RISK_UNDERWRITING_OPEN_EXISTS.getCode().equals(code)
                            || CONCURRENT_REQUEST.getCode().equals(code), code);
                    }
                    return checker.status();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            statuses.sort(Integer::compareTo);
            assertEquals(List.of(201, 409), statuses);
        }
        assertEquals(1, store.underwritingsOf(clientId).size());
    }

    static Stream<Arguments> submissionViolations() {
        return Stream.of(
            violation("input.mccCode", input -> input.setMccCode(null), decision -> { }),
            violation("input.sourceCaseId", input -> input.setSourceCaseId(null), decision -> { }),
            violation("input.sourceApplicationId", input -> input.setSourceApplicationId(null), decision -> { }),
            violation("input.sourceClassificationConfirmedAt", input -> input.setSourceClassificationConfirmedAt(null),
                decision -> { }),
            violation("decision.observationDays", input -> { }, decision -> decision.setObservationDays(null)),
            violation("decision.requireDeposit", input -> { }, decision -> decision.setRequireDeposit(null)),
            violation("decision.depositAmount", input -> { }, decision -> {
                decision.setRequireDeposit(Boolean.TRUE);
                decision.setDepositCurrency("USD");
            }),
            violation("decision.depositCurrency", input -> { }, decision -> {
                decision.setRequireDeposit(Boolean.TRUE);
                decision.setDepositAmount("5000");
            }),
            violation("decision.highRiskMcc", input -> { }, decision -> decision.setHighRiskMcc(null)));
    }

    static Stream<Arguments> draftViolations() {
        return Stream.of(
            violation("decision.depositBlocksCollection", input -> { },
                decision -> decision.setDepositBlocksCollection(Boolean.TRUE)),
            violation("decision.depositAmount", input -> { }, decision -> decision.setDepositAmount("100")),
            violation("decision.depositAmount", input -> { }, decision -> {
                decision.setRequireDeposit(Boolean.TRUE);
                decision.setDepositAmount("0");
                decision.setDepositCurrency("USD");
            }),
            violation("decision.highRiskMccReason", input -> { }, decision -> decision.setHighRiskMcc(Boolean.TRUE)),
            violation("decision.suggestedSettlementDays", input -> { }, decision -> decision.setSuggestedSettlementDays(91)),
            violation("decision.observationDays", input -> { }, decision -> decision.setObservationDays(366)),
            violation("input.volumeCurrency", input -> input.setVolumeCurrency(null), decision -> { }),
            violation("input.fulfillmentDays", input -> input.setFulfillmentType(FulfillmentType.INSTANT.getCode()),
                decision -> { }),
            violation("input.mccCode", input -> input.setMccCode("57A2"), decision -> { }));
    }

    /** 参数化用例：期望字段与对输入 / 结论的修改。 */
    static Arguments violation(String field, Consumer<UnderwritingInputBody> input, Consumer<UnderwritingDecisionBody> decision) {
        return Arguments.of(field, input, decision);
    }
}
