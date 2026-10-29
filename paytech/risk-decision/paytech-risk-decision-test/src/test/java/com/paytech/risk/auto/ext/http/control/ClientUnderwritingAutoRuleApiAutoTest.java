package com.paytech.risk.auto.ext.http.control;

import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode.RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE;
import static com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode.RISK_UNDERWRITING_OPEN_EXISTS;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
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
import com.paytech.risk.control.domain.underwriting.ExposureUnavailableReason;
import com.paytech.risk.control.domain.underwriting.UnderwritingCheckCode;
import com.paytech.risk.decision.api.control.ClientUnderwritingApi;
import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;
import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;
import com.paytech.risk.test.base.AbstractUnderwritingApiTest;
import com.paytech.risk.test.builder.control.UnderwritingRequestBuilder;
import com.paytech.risk.test.checker.control.UnderwritingResponseChecker;
import com.paytech.risk.test.mapper.control.UnderwritingTestMapper;
import com.paytech.risk.test.mocker.control.UnderwritingRuleMocker;

/**
 * 自动承保判定、一键自动承保与估算敞口
 * <li>预览全部通过可自动承保，逐项不满足给出对应检查（AC-02）；一键自动承保记 AUTO_RULE、规则版本与触发人，不满足返回 409。</li>
 * <li>估算敞口按四种交付方式与 T+N 计算，T+N 为空按 0 并标注，缺业务信息给出原因；保存与提交时冻结（AC-12）。</li>
 *
 * @see ClientUnderwritingApi#previewUnderwriting(String, String, com.paytech.risk.decision.api.control.request.UnderwritingPreviewRequestBody)
 * @see ClientUnderwritingApi#autoUnderwrite(String, String, String, com.paytech.risk.decision.api.control.request.AutoUnderwritingRequestBody)
 */
class ClientUnderwritingAutoRuleApiAutoTest extends AbstractUnderwritingApiTest {

    /** 满足规则：预览可自动承保，规则 v1，复核级别 LOW。 */
    @Test
    void test_normalFlow_previewEligible() {
        post(UNDERWRITER, ROOT + "/previews", UnderwritingRequestBuilder.preview(UnderwritingRequestBuilder.input(), 5))
            .assertChecks(true, List.of()).assertText("/ruleVersionNo", "1")
            .assertText("/thresholdAmount", "50000.00").assertText("/thresholdCurrency", "USD")
            .assertText("/reviewLevel", UnderwritingRiskLevel.LOW.getCode())
            .assertExposure("/estimatedExposure", "15000.00", "USD", 15, false);
    }

    /** 逐项不满足的输入给出对应未通过检查。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("ineligibleInputs")
    void test_businessRuleViolation_previewChecks(String scenario, Consumer<UnderwritingInputBody> change,
        List<UnderwritingCheckCode> failed) {
        UnderwritingInputBody input = UnderwritingRequestBuilder.input();
        change.accept(input);
        post(UNDERWRITER, ROOT + "/previews", UnderwritingRequestBuilder.preview(input, 5))
            .assertChecks(false, failed.stream().map(UnderwritingCheckCode::getCode).toList());
    }

    /** 该币种无阈值时预览不返回阈值金额与币种。 */
    @Test
    void test_edgeCase_noThresholdForCurrency() {
        UnderwritingInputBody input = UnderwritingRequestBuilder.input();
        input.setVolumeCurrency("EUR");
        post(UNDERWRITER, ROOT + "/previews", UnderwritingRequestBuilder.preview(input, 5))
            .assertText("/thresholdAmount", null).assertText("/thresholdCurrency", null);
    }

    /** 没有 ACTIVE 规则：规则、币种、阈值与排除清单检查均不通过，规则版本为空。 */
    @Test
    void test_noDataFound_noActiveRule() {
        rules.mockNoActiveRule();
        post(UNDERWRITER, ROOT + "/previews", UnderwritingRequestBuilder.preview(UnderwritingRequestBuilder.input(), 5))
            .assertChecks(false, Stream.of(UnderwritingCheckCode.RULE_CONFIGURED, UnderwritingCheckCode.CURRENCY_CONFIGURED,
                UnderwritingCheckCode.VOLUME_WITHIN_THRESHOLD, UnderwritingCheckCode.MCC_NOT_EXCLUDED)
                .map(UnderwritingCheckCode::getCode).toList())
            .assertText("/ruleVersionNo", null);
        post(UNDERWRITER, ROOT + "/clients/" + newClientId() + "/auto-underwritings",
            UnderwritingRequestBuilder.auto(UnderwritingRequestBuilder.input()))
            .assertProblem(409, RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE.getCode());
    }

    /** 规则排除清单命中 MCC：不可自动承保；未命中时按新规则版本 v2 生成。 */
    @Test
    void test_businessRuleViolation_excludedMcc() {
        rules.mockRuleExcluding("5732,5967");
        post(UNDERWRITER, ROOT + "/previews", UnderwritingRequestBuilder.preview(UnderwritingRequestBuilder.input(), 5))
            .assertChecks(false, List.of(UnderwritingCheckCode.MCC_NOT_EXCLUDED.getCode()))
            .assertText("/ruleVersionNo", Integer.toString(UnderwritingRuleMocker.TEST_RULE_VERSION_NO));
        UnderwritingInputBody allowed = UnderwritingRequestBuilder.input();
        allowed.setMccCode("5045");
        post(UNDERWRITER, ROOT + "/clients/" + newClientId() + "/auto-underwritings", UnderwritingRequestBuilder.auto(allowed))
            .assertUnderwriting(201, UnderwritingStatus.APPROVED.getCode(), UnderwritingRiskLevel.LOW.getCode())
            .assertText("/ruleVersionNo", Integer.toString(UnderwritingRuleMocker.TEST_RULE_VERSION_NO));
    }

    /** 一键自动承保：AUTO_RULE、规则版本、触发人为创建人、提交人与决定人为空、标准模板结论。 */
    @Test
    void test_normalFlow_autoUnderwrite() {
        long clientId = newClientId();
        UnderwritingResponseChecker created = post(UNDERWRITER, ROOT + "/clients/" + clientId + "/auto-underwritings",
            UnderwritingRequestBuilder.auto(UnderwritingRequestBuilder.input()))
            .assertUnderwriting(201, UnderwritingStatus.APPROVED.getCode(), UnderwritingRiskLevel.LOW.getCode())
            .assertText("/decisionSource", UnderwritingDecisionSource.AUTO_RULE.getCode()).assertText("/ruleVersionNo", "1")
            .assertText("/createdBy", Long.toString(UNDERWRITER)).assertText("/submittedBy", null)
            .assertText("/decidedBy", null).assertText("/decision/observationDays", "30")
            .assertText("/decision/observationTransactions", "100").assertText("/decision/requireDeposit", "false")
            .assertText("/decision/highRiskMcc", "false").assertText("/decision/suggestedSettlementDays", null)
            .assertExposure("/estimatedExposure", "10000.00", "USD", 10, true);

        Map<String, Object> row = store.underwriting(created.id());
        assertAll("自动承保版本",
            () -> assertEquals(UnderwritingTestMapper.INITIAL_RULE_VERSION_ID, ((Number) row.get("rule_version_id")).longValue()),
            () -> assertNull(row.get("submitted_by")),
            () -> assertNull(row.get("decided_by")),
            () -> assertTrue(row.get("submitted_time") != null && row.get("decided_time") != null),
            () -> assertTrue(UnderwritingTestMapper.flag(row.get("effective_slot"))));
    }

    /** 自动承保替代上一有效版本；已有进行中承保时拒绝。 */
    @Test
    void test_edgeCase_autoSupersedesAndOpenBlocks() {
        long clientId = newClientId();
        UnderwritingResponseChecker manual = approved(clientId, UnderwritingRequestBuilder.input());
        UnderwritingResponseChecker auto = post(UNDERWRITER, ROOT + "/clients/" + clientId + "/auto-underwritings",
            UnderwritingRequestBuilder.auto(UnderwritingRequestBuilder.input()))
            .assertUnderwriting(201, UnderwritingStatus.APPROVED.getCode(), UnderwritingRiskLevel.LOW.getCode())
            .assertText("/versionNo", "2").assertText("/supersedesUnderwritingId", Long.toString(manual.id()));
        assertEquals(UnderwritingStatus.SUPERSEDED.getCode(), store.underwriting(manual.id()).get("status"));

        createDraft(clientId, UnderwritingRequestBuilder.input(), null);
        post(UNDERWRITER, ROOT + "/clients/" + clientId + "/auto-underwritings",
            UnderwritingRequestBuilder.auto(UnderwritingRequestBuilder.input()))
            .assertProblem(409, RISK_UNDERWRITING_OPEN_EXISTS.getCode());
        assertEquals(UnderwritingStatus.APPROVED.getCode(), store.underwriting(auto.id()).get("status"));
    }

    /** 不满足规则时一键自动承保 409 并列出未通过检查；满足规则但缺来源坐标 422。 */
    @Test
    void test_businessRuleViolation_autoNotEligible() {
        UnderwritingInputBody medium = UnderwritingRequestBuilder.input(UnderwritingRiskLevel.MEDIUM);
        UnderwritingResponseChecker rejected = post(UNDERWRITER, ROOT + "/clients/" + newClientId() + "/auto-underwritings",
            UnderwritingRequestBuilder.auto(medium)).assertProblem(409, RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE.getCode());
        assertTrue(rejected.text("/detail").contains(UnderwritingCheckCode.CRR_LOW.getCode()), rejected.text("/detail"));

        UnderwritingInputBody noSource = UnderwritingRequestBuilder.input();
        noSource.setSourceCaseId(null);
        post(UNDERWRITER, ROOT + "/clients/" + newClientId() + "/auto-underwritings", UnderwritingRequestBuilder.auto(noSource))
            .assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError("input.sourceCaseId");
    }

    /** 四种交付方式与 T+N 的估算敞口。 */
    @ParameterizedTest(name = "{0} T+{1}")
    @MethodSource("exposures")
    void test_normalFlow_exposure(FulfillmentType type, Integer settlementDays, String amount, int riskDays) {
        UnderwritingInputBody input = UnderwritingRequestBuilder.input();
        input.setFulfillmentType(type.getCode());
        input.setFulfillmentDays(type == FulfillmentType.PRE_ORDER ? 10 : null);
        post(UNDERWRITER, ROOT + "/previews", UnderwritingRequestBuilder.preview(input, settlementDays))
            .assertExposure("/estimatedExposure", amount, "USD", riskDays, settlementDays == null)
            .assertText("/exposureUnavailableReason", null);
    }

    /** 缺业务信息时无法估算并给出原因，不阻止提交。 */
    @ParameterizedTest(name = "{1}")
    @MethodSource("unavailableExposures")
    void test_edgeCase_exposureUnavailable(Consumer<UnderwritingInputBody> change, ExposureUnavailableReason reason) {
        UnderwritingInputBody input = UnderwritingRequestBuilder.input();
        change.accept(input);
        post(UNDERWRITER, ROOT + "/previews", UnderwritingRequestBuilder.preview(input, 5))
            .assertExposureUnavailable("/estimatedExposure", "/exposureUnavailableReason", reason.getCode());
        UnderwritingResponseChecker draft = createDraft(newClientId(), input, UnderwritingRequestBuilder.decision());
        submit(draft.id(), draft.version(), input, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.APPROVE, null)
            .assertUnderwriting(200, UnderwritingStatus.APPROVED.getCode(), UnderwritingRiskLevel.LOW.getCode())
            .assertExposureUnavailable("/estimatedExposure", "/exposureUnavailableReason", reason.getCode());
        assertNull(store.underwriting(draft.id()).get("estimated_exposure"));
    }

    /** 保存时随 T+N 重算并冻结敞口，详情返回冻结值。 */
    @Test
    void test_edgeCase_exposureFrozenOnSave() {
        UnderwritingResponseChecker draft = createDraft(newClientId(), UnderwritingRequestBuilder.input(), null)
            .assertExposure("/estimatedExposure", "10000.00", "USD", 10, true);
        UnderwritingDecisionBody decision = UnderwritingRequestBuilder.decision();
        decision.setSuggestedSettlementDays(20);
        put(UNDERWRITER, ROOT + "/underwritings/" + draft.id(),
            UnderwritingRequestBuilder.save(draft.version(), UnderwritingRequestBuilder.input(), decision))
            .assertExposure("/estimatedExposure", "30000.00", "USD", 30, false);
        get(UNDERWRITER, ROOT + "/underwritings/" + draft.id()).assertExposure("/estimatedExposure", "30000.00", "USD", 30, false);
        assertEquals(30, ((Number) store.underwriting(draft.id()).get("exposure_risk_days")).intValue());
    }

    static Stream<Arguments> ineligibleInputs() {
        return Stream.of(
            Arguments.of("CRR MEDIUM", change(input -> input.setCrr(UnderwritingRiskLevel.MEDIUM.getCode())),
                List.of(UnderwritingCheckCode.CRR_LOW)),
            Arguments.of("CRR 缺失", change(input -> input.setCrr(null)), List.of(UnderwritingCheckCode.CRR_LOW)),
            Arguments.of("未申报流水", change(input -> {
                input.setExpectedMonthlyVolume(null);
                input.setExpectedTicket(null);
                input.setVolumeCurrency(null);
            }), List.of(UnderwritingCheckCode.VOLUME_PROVIDED, UnderwritingCheckCode.CURRENCY_CONFIGURED,
                UnderwritingCheckCode.VOLUME_WITHIN_THRESHOLD)),
            Arguments.of("EUR 无阈值", change(input -> input.setVolumeCurrency("EUR")),
                List.of(UnderwritingCheckCode.CURRENCY_CONFIGURED, UnderwritingCheckCode.VOLUME_WITHIN_THRESHOLD)),
            Arguments.of("超过阈值", change(input -> input.setExpectedMonthlyVolume("50000.01")),
                List.of(UnderwritingCheckCode.VOLUME_WITHIN_THRESHOLD)),
            Arguments.of("卡组织高风险", change(input -> input.setNetworkHighRiskMcc(Boolean.TRUE)),
                List.of(UnderwritingCheckCode.MCC_NOT_NETWORK_HIGH_RISK)),
            Arguments.of("MCC 未确认", change(input -> input.setMccCode(null)),
                List.of(UnderwritingCheckCode.MCC_NOT_NETWORK_HIGH_RISK, UnderwritingCheckCode.MCC_NOT_EXCLUDED)),
            Arguments.of("分类未确认", change(input -> input.setSourceClassificationConfirmedAt(null)),
                List.of(UnderwritingCheckCode.CLASSIFICATION_CONFIRMED)));
    }

    static Stream<Arguments> exposures() {
        return Stream.of(Arguments.of(FulfillmentType.PRE_ORDER, 5, "15000.00", 15),
            Arguments.of(FulfillmentType.INSTANT, 5, "5000.00", 5),
            Arguments.of(FulfillmentType.SUBSCRIPTION, 5, "35000.00", 35),
            Arguments.of(FulfillmentType.SERVICE, 7, "7000.00", 7),
            Arguments.of(FulfillmentType.INSTANT, null, "0.00", 0),
            Arguments.of(FulfillmentType.PRE_ORDER, 0, "10000.00", 10));
    }

    static Stream<Arguments> unavailableExposures() {
        return Stream.of(
            Arguments.of(change(input -> {
                input.setExpectedMonthlyVolume(null);
                input.setExpectedTicket(null);
                input.setVolumeCurrency(null);
            }), ExposureUnavailableReason.VOLUME_MISSING),
            Arguments.of(change(input -> {
                input.setFulfillmentType(null);
                input.setFulfillmentDays(null);
            }), ExposureUnavailableReason.FULFILLMENT_TYPE_MISSING),
            Arguments.of(change(input -> input.setFulfillmentDays(null)), ExposureUnavailableReason.FULFILLMENT_DAYS_MISSING));
    }

    /** 参数化用例对输入的修改。 */
    static Consumer<UnderwritingInputBody> change(Consumer<UnderwritingInputBody> change) {
        return change;
    }
}
