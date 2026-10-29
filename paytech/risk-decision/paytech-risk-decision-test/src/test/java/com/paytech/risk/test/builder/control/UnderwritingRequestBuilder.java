package com.paytech.risk.test.builder.control;

import com.paytech.risk.control.core.enums.FulfillmentType;
import com.paytech.risk.control.core.enums.UnderwritingClientType;
import com.paytech.risk.control.core.enums.UnderwritingDueDiligenceTier;
import com.paytech.risk.control.core.enums.UnderwritingOutcome;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.decision.api.control.request.AutoUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.CreateUnderwritingDraftRequestBody;
import com.paytech.risk.decision.api.control.request.ReviewUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.ReviseUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.SaveUnderwritingDraftRequestBody;
import com.paytech.risk.decision.api.control.request.SubmitUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;
import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;
import com.paytech.risk.decision.api.control.request.UnderwritingPreviewRequestBody;

/**
 * 承保请求 Body 的测试构造
 * <li>{@link #input()} 是满足自动承保的 LOW 输入：USD 月流水 30,000（阈值 50,000）、MCC 5732 非高风险、分类已确认、预售 10 天。</li>
 * <li>{@link #decision()} 是完整的人工结论：观察期 30 天 / 100 笔、不要求保证金、T+5。</li>
 * <li>只构造对象，不访问数据库或网络；负例由用例显式覆盖字段，不修复非法值。</li>
 *
 * @author Claude
 */
public final class UnderwritingRequestBuilder {
    /** 纯构造工具。 */
    private UnderwritingRequestBuilder() { }

    /** 满足自动承保的 LOW 输入快照。 */
    public static UnderwritingInputBody input() {
        UnderwritingInputBody body = new UnderwritingInputBody();
        body.setClientDisplayName("Acme Trading LLC");
        body.setClientNo("CL-UW-0001");
        body.setClientType(UnderwritingClientType.ORGANIZATION.getCode());
        body.setJurisdictionCode("AE");
        body.setCrr(UnderwritingRiskLevel.LOW.getCode());
        body.setDdTier(UnderwritingDueDiligenceTier.STANDARD.getCode());
        body.setMccCode("5732");
        body.setIndustryCode("RETAIL_ELECTRONICS");
        body.setNetworkHighRiskMcc(Boolean.FALSE);
        body.setExpectedMonthlyVolume("30000.00");
        body.setVolumeCurrency("USD");
        body.setExpectedTicket("120.00");
        body.setFulfillmentType(FulfillmentType.PRE_ORDER.getCode());
        body.setFulfillmentDays(10);
        body.setWebsiteUrl("https://acme.example.test");
        body.setRefundPolicyUrl("https://acme.example.test/refunds");
        body.setExpectedPaymentMethods("APPLE_PAY,CARD");
        body.setSourceCaseId("7001");
        body.setSourceCaseVersion("3");
        body.setSourceApplicationId("6001");
        body.setSourceDraftRevision("5");
        body.setSourceClassificationConfirmedAt("2026-10-20T08:00:00Z");
        return body;
    }

    /** 指定 CRR 的输入快照；null 表示尽调未评 CRR。 */
    public static UnderwritingInputBody input(UnderwritingRiskLevel crr) {
        UnderwritingInputBody body = input();
        body.setCrr(crr == null ? null : crr.getCode());
        return body;
    }

    /** 完整的人工结论。 */
    public static UnderwritingDecisionBody decision() {
        UnderwritingDecisionBody body = new UnderwritingDecisionBody();
        body.setObservationDays(30);
        body.setObservationTransactions(100);
        body.setRequireDeposit(Boolean.FALSE);
        body.setDepositBlocksCollection(Boolean.FALSE);
        body.setSuggestedSettlementDays(5);
        body.setHighRiskMcc(Boolean.FALSE);
        body.setLimitNote("按验证档起步");
        body.setUnderwritingNote("预售 10 天，拒付暴露可控");
        body.setHandoverNote("可按标准 T+5 签约");
        return body;
    }

    /** 建草稿请求。 */
    public static CreateUnderwritingDraftRequestBody create(UnderwritingInputBody input, UnderwritingDecisionBody decision) {
        CreateUnderwritingDraftRequestBody body = new CreateUnderwritingDraftRequestBody();
        body.setInput(input);
        body.setDecision(decision);
        return body;
    }

    /** 保存草稿请求。 */
    public static SaveUnderwritingDraftRequestBody save(int expectedVersion, UnderwritingInputBody input,
        UnderwritingDecisionBody decision) {
        SaveUnderwritingDraftRequestBody body = new SaveUnderwritingDraftRequestBody();
        body.setExpectedVersion(expectedVersion);
        body.setInput(input);
        body.setDecision(decision);
        return body;
    }

    /** 提交请求。 */
    public static SubmitUnderwritingRequestBody submit(int expectedVersion, UnderwritingInputBody input,
        UnderwritingDecisionBody decision, UnderwritingOutcome outcome, String declineReason) {
        SubmitUnderwritingRequestBody body = new SubmitUnderwritingRequestBody();
        body.setExpectedVersion(expectedVersion);
        body.setInput(input);
        body.setDecision(decision);
        body.setOutcome(outcome.getCode());
        body.setDeclineReason(declineReason);
        return body;
    }

    /** 复核请求。 */
    public static ReviewUnderwritingRequestBody review(int expectedVersion, UnderwritingOutcome decision, String comment) {
        ReviewUnderwritingRequestBody body = new ReviewUnderwritingRequestBody();
        body.setExpectedVersion(expectedVersion);
        body.setDecision(decision.getCode());
        body.setComment(comment);
        return body;
    }

    /** 自动承保请求。 */
    public static AutoUnderwritingRequestBody auto(UnderwritingInputBody input) {
        AutoUnderwritingRequestBody body = new AutoUnderwritingRequestBody();
        body.setInput(input);
        return body;
    }

    /** 修订请求。 */
    public static ReviseUnderwritingRequestBody revise(int expectedVersion, String reason, UnderwritingInputBody input) {
        ReviseUnderwritingRequestBody body = new ReviseUnderwritingRequestBody();
        body.setExpectedVersion(expectedVersion);
        body.setRevisionReason(reason);
        body.setInput(input);
        return body;
    }

    /** 预览请求。 */
    public static UnderwritingPreviewRequestBody preview(UnderwritingInputBody input, Integer settlementDays) {
        UnderwritingPreviewRequestBody body = new UnderwritingPreviewRequestBody();
        body.setInput(input);
        body.setSuggestedSettlementDays(settlementDays);
        return body;
    }
}
