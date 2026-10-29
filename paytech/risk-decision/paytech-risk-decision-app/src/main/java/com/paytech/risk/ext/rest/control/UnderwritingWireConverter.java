package com.paytech.risk.ext.rest.control;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.control.core.enums.FulfillmentType;
import com.paytech.risk.control.core.enums.UnderwritingClientType;
import com.paytech.risk.control.core.enums.UnderwritingDueDiligenceTier;
import com.paytech.risk.control.core.enums.UnderwritingOutcome;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.domain.underwriting.ExposureEstimate;
import com.paytech.risk.control.domain.underwriting.UnderwritingContent;
import com.paytech.risk.control.domain.underwriting.UnderwritingCurrent;
import com.paytech.risk.control.domain.underwriting.UnderwritingInput;
import com.paytech.risk.control.domain.underwriting.UnderwritingPreview;
import com.paytech.risk.control.domain.underwriting.UnderwritingStatistics;
import com.paytech.risk.control.domain.underwriting.UnderwritingTerms;
import com.paytech.risk.control.domain.underwriting.UnderwritingView;
import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;
import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;
import com.paytech.risk.decision.api.control.response.ClientUnderwritingCurrentResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingCheckBody;
import com.paytech.risk.decision.api.control.response.UnderwritingExposureBody;
import com.paytech.risk.decision.api.control.response.UnderwritingHistoryResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingPreviewResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingSnapshotBody;
import com.paytech.risk.decision.api.control.response.UnderwritingStatisticsResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingSummaryBody;

/**
 * 承保协议模型与 risk-control Owner 模型之间的转换
 * <li>金额输出为主单位十进制字符串，至少 2 位小数（risk-decision 不持有币种精度目录，承保涉及的金额按 2 位展示）。</li>
 */
final class UnderwritingWireConverter {
    /** 纯转换工具。 */
    private UnderwritingWireConverter() { }

    /** 输入快照转换；格式与 Code 已由 Body 注解限定。 */
    static UnderwritingInput input(UnderwritingInputBody body) {
        UnderwritingInput input = new UnderwritingInput();
        input.setClientDisplayName(body.getClientDisplayName());
        input.setClientNo(body.getClientNo());
        input.setClientType(code(UnderwritingClientType.values(), body.getClientType(), "input.clientType"));
        input.setJurisdictionCode(body.getJurisdictionCode());
        input.setCrr(code(UnderwritingRiskLevel.values(), body.getCrr(), "input.crr"));
        input.setDdTier(code(UnderwritingDueDiligenceTier.values(), body.getDdTier(), "input.ddTier"));
        input.setMccCode(body.getMccCode());
        input.setIndustryCode(blankToNull(body.getIndustryCode()));
        input.setNetworkHighRiskMcc(Boolean.TRUE.equals(body.getNetworkHighRiskMcc()));
        input.setExpectedMonthlyVolume(decimal(body.getExpectedMonthlyVolume()));
        input.setVolumeCurrency(body.getVolumeCurrency());
        input.setExpectedTicket(decimal(body.getExpectedTicket()));
        input.setFulfillmentType(code(FulfillmentType.values(), body.getFulfillmentType(), "input.fulfillmentType"));
        input.setFulfillmentDays(body.getFulfillmentDays());
        input.setWebsiteUrl(blankToNull(body.getWebsiteUrl()));
        input.setRefundPolicyUrl(blankToNull(body.getRefundPolicyUrl()));
        input.setExpectedPaymentMethods(blankToNull(body.getExpectedPaymentMethods()));
        input.setSourceCaseId(id(body.getSourceCaseId()));
        input.setSourceCaseVersion(id(body.getSourceCaseVersion()));
        input.setSourceApplicationId(id(body.getSourceApplicationId()));
        input.setSourceDraftRevision(id(body.getSourceDraftRevision()));
        input.setSourceClassificationConfirmedTime(instant(body.getSourceClassificationConfirmedAt()));
        return input;
    }

    /** 结论字段转换；缺省时为空结论。 */
    static UnderwritingTerms terms(UnderwritingDecisionBody body) {
        UnderwritingTerms terms = UnderwritingTerms.empty();
        if (body == null) {
            return terms;
        }
        terms.setObservationDays(body.getObservationDays());
        terms.setObservationTransactions(body.getObservationTransactions());
        terms.setRequireDeposit(body.getRequireDeposit());
        terms.setDepositAmount(decimal(body.getDepositAmount()));
        terms.setDepositCurrency(body.getDepositCurrency());
        terms.setDepositBlocksCollection(body.getDepositBlocksCollection());
        terms.setSuggestedSettlementDays(body.getSuggestedSettlementDays());
        terms.setHighRiskMcc(body.getHighRiskMcc());
        terms.setHighRiskMccReason(blankToNull(body.getHighRiskMccReason()));
        terms.setLimitNote(blankToNull(body.getLimitNote()));
        terms.setUnderwritingNote(blankToNull(body.getUnderwritingNote()));
        terms.setHandoverNote(blankToNull(body.getHandoverNote()));
        return terms;
    }

    /** 提交或复核结论转换。 */
    static UnderwritingOutcome outcome(String value, String field) {
        return code(UnderwritingOutcome.values(), value, field);
    }

    /** 版本详情。 */
    static UnderwritingResponseBody response(UnderwritingView view) {
        if (view == null) {
            return null;
        }
        UnderwritingContent content = view.getContent();
        UnderwritingResponseBody body = new UnderwritingResponseBody();
        body.setUnderwritingId(Long.toString(view.getUnderwritingId()));
        body.setClientId(Long.toString(view.getClientId()));
        body.setVersionNo(view.getVersionNo());
        body.setStatus(view.getStatus().getCode());
        body.setDecisionSource(codeOf(view.getDecisionSource()));
        body.setRuleVersionNo(view.getRuleVersionNo());
        body.setReviewLevel(view.getReviewLevel().getCode());
        body.setInput(snapshot(content.getInput()));
        body.setDecision(decision(content));
        body.setEstimatedExposure(exposure(content.getExposure()));
        body.setExposureUnavailableReason(codeOf(content.getExposure().getUnavailableReason()));
        body.setDeclineReason(view.getDeclineReason());
        body.setReviewComment(view.getReviewComment());
        body.setRevisionReason(view.getRevisionReason());
        body.setSupersedesUnderwritingId(text(view.getSupersedesUnderwritingId()));
        body.setCreatedBy(Long.toString(view.getCreatedBy()));
        body.setSubmittedBy(text(view.getSubmittedBy()));
        body.setSubmittedAt(text(view.getSubmittedTime()));
        body.setDecidedBy(text(view.getDecidedBy()));
        body.setDecidedAt(text(view.getDecidedTime()));
        body.setSupersededAt(text(view.getSupersededTime()));
        body.setCreatedAt(text(view.getCreateTime()));
        body.setUpdatedAt(text(view.getUpdateTime()));
        body.setVersion(view.getVersion());
        return body;
    }

    /** 列表行。 */
    static UnderwritingSummaryBody summary(UnderwritingView view) {
        UnderwritingInput input = view.getContent().getInput();
        UnderwritingTerms terms = view.getContent().getTerms();
        UnderwritingSummaryBody body = new UnderwritingSummaryBody();
        body.setUnderwritingId(Long.toString(view.getUnderwritingId()));
        body.setClientId(Long.toString(view.getClientId()));
        body.setClientDisplayName(input.getClientDisplayName());
        body.setClientNo(input.getClientNo());
        body.setJurisdictionCode(input.getJurisdictionCode());
        body.setClientType(input.getClientType().getCode());
        body.setCrr(codeOf(input.getCrr()));
        body.setMccCode(input.getMccCode());
        body.setNetworkHighRiskMcc(input.isNetworkHighRiskMcc());
        body.setVersionNo(view.getVersionNo());
        body.setStatus(view.getStatus().getCode());
        body.setReviewLevel(view.getReviewLevel().getCode());
        body.setDecisionSource(codeOf(view.getDecisionSource()));
        body.setRuleVersionNo(view.getRuleVersionNo());
        body.setEstimatedExposure(exposure(view.getContent().getExposure()));
        body.setFulfillmentType(codeOf(input.getFulfillmentType()));
        body.setSuggestedSettlementDays(terms.getSuggestedSettlementDays());
        body.setRequireDeposit(Boolean.TRUE.equals(terms.getRequireDeposit()));
        body.setSubmittedBy(text(view.getSubmittedBy()));
        body.setDecidedBy(text(view.getDecidedBy()));
        body.setUpdatedAt(text(view.getUpdateTime()));
        return body;
    }

    /** Client 当前承保。 */
    static ClientUnderwritingCurrentResponseBody current(UnderwritingCurrent current) {
        ClientUnderwritingCurrentResponseBody body = new ClientUnderwritingCurrentResponseBody();
        body.setEffective(response(current.getEffective()));
        body.setOpen(response(current.getOpen()));
        body.setLatest(response(current.getLatest()));
        return body;
    }

    /** 版本历史。 */
    static UnderwritingHistoryResponseBody history(List<UnderwritingView> views) {
        UnderwritingHistoryResponseBody body = new UnderwritingHistoryResponseBody();
        body.setItems(views.stream().map(UnderwritingWireConverter::response).toList());
        return body;
    }

    /** 列表统计。 */
    static UnderwritingStatisticsResponseBody statistics(UnderwritingStatistics statistics) {
        UnderwritingStatisticsResponseBody body = new UnderwritingStatisticsResponseBody();
        body.setEffective(statistics.getEffective());
        body.setDraft(statistics.getDraft());
        body.setSubmitted(statistics.getSubmitted());
        body.setApprovedThisMonth(statistics.getApprovedThisMonth());
        body.setDeclinedThisMonth(statistics.getDeclinedThisMonth());
        return body;
    }

    /** 自动承保判定与估算敞口预览。 */
    static UnderwritingPreviewResponseBody preview(UnderwritingPreview preview) {
        UnderwritingPreviewResponseBody body = new UnderwritingPreviewResponseBody();
        var rule = preview.getAutoRule().getRule();
        body.setRuleVersionNo(rule == null ? null : rule.getVersionNo());
        body.setAutoEligible(preview.getAutoRule().eligible());
        body.setThresholdAmount(money(preview.getAutoRule().getThresholdAmount()));
        body.setThresholdCurrency(preview.getAutoRule().getThresholdCurrency());
        body.setChecks(preview.getAutoRule().getChecks().stream().map(check -> {
            UnderwritingCheckBody item = new UnderwritingCheckBody();
            item.setCode(check.getCode().getCode());
            item.setPassed(check.isPassed());
            return item;
        }).toList());
        body.setEstimatedExposure(exposure(preview.getExposure()));
        body.setExposureUnavailableReason(codeOf(preview.getExposure().getUnavailableReason()));
        body.setReviewLevel(preview.getReviewLevel().getCode());
        return body;
    }

    /** 输入快照的响应形态，带 CRR 缺失标记。 */
    static UnderwritingSnapshotBody snapshot(UnderwritingInput input) {
        UnderwritingSnapshotBody body = new UnderwritingSnapshotBody();
        body.setClientDisplayName(input.getClientDisplayName());
        body.setClientNo(input.getClientNo());
        body.setClientType(input.getClientType().getCode());
        body.setJurisdictionCode(input.getJurisdictionCode());
        body.setCrr(codeOf(input.getCrr()));
        body.setCrrMissing(input.getCrr() == null);
        body.setDdTier(codeOf(input.getDdTier()));
        body.setMccCode(input.getMccCode());
        body.setIndustryCode(input.getIndustryCode());
        body.setNetworkHighRiskMcc(input.isNetworkHighRiskMcc());
        body.setExpectedMonthlyVolume(money(input.getExpectedMonthlyVolume()));
        body.setVolumeCurrency(input.getVolumeCurrency());
        body.setExpectedTicket(money(input.getExpectedTicket()));
        body.setFulfillmentType(codeOf(input.getFulfillmentType()));
        body.setFulfillmentDays(input.getFulfillmentDays());
        body.setWebsiteUrl(input.getWebsiteUrl());
        body.setRefundPolicyUrl(input.getRefundPolicyUrl());
        body.setExpectedPaymentMethods(input.getExpectedPaymentMethods());
        body.setSourceCaseId(text(input.getSourceCaseId()));
        body.setSourceCaseVersion(text(input.getSourceCaseVersion()));
        body.setSourceApplicationId(text(input.getSourceApplicationId()));
        body.setSourceDraftRevision(text(input.getSourceDraftRevision()));
        body.setSourceClassificationConfirmedAt(text(input.getSourceClassificationConfirmedTime()));
        return body;
    }

    /** 结论字段的响应形态；高风险登记标记取实际生效值。 */
    static UnderwritingDecisionBody decision(UnderwritingContent content) {
        UnderwritingTerms terms = content.getTerms();
        UnderwritingDecisionBody body = new UnderwritingDecisionBody();
        body.setObservationDays(terms.getObservationDays());
        body.setObservationTransactions(terms.getObservationTransactions());
        body.setRequireDeposit(Boolean.TRUE.equals(terms.getRequireDeposit()));
        body.setDepositAmount(money(terms.getDepositAmount()));
        body.setDepositCurrency(terms.getDepositCurrency());
        body.setDepositBlocksCollection(Boolean.TRUE.equals(terms.getDepositBlocksCollection()));
        body.setSuggestedSettlementDays(terms.getSuggestedSettlementDays());
        body.setHighRiskMcc(content.highRiskMcc());
        body.setHighRiskMccReason(terms.getHighRiskMccReason());
        body.setLimitNote(terms.getLimitNote());
        body.setUnderwritingNote(terms.getUnderwritingNote());
        body.setHandoverNote(terms.getHandoverNote());
        return body;
    }

    /** 估算敞口；不可算为空。 */
    static UnderwritingExposureBody exposure(ExposureEstimate estimate) {
        if (!estimate.available()) {
            return null;
        }
        UnderwritingExposureBody body = new UnderwritingExposureBody();
        body.setAmount(money(estimate.getAmount()));
        body.setCurrency(estimate.getCurrency());
        body.setRiskDays(estimate.getRiskDays());
        body.setSettlementDaysAssumed(estimate.isSettlementDaysAssumed());
        body.setFormula(money(estimate.getMonthlyVolume()) + " × " + estimate.getRiskDays() + " ÷ 30");
        return body;
    }

    /** 金额：去无意义尾零但至少 2 位小数，普通十进制。 */
    static String money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 2 ? stripped.setScale(2) : stripped).toPlainString();
    }

    /** 十进制金额解析；格式已由 Body 注解限定。 */
    static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    /** 十进制 ID 或版本解析；格式已由 Body 注解限定。 */
    static Long id(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    /** UTC 时间解析；格式已由 Body 注解限定。 */
    static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    /** 空白文本规范为空。 */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 可空值的文本形式。 */
    static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** 可空枚举的 Code。 */
    static String codeOf(CodeEnum value) {
        return value == null ? null : value.getCode();
    }

    /** 按稳定 Code 解析可空闭合枚举；未知 Code 为参数错误。 */
    static <E extends Enum<E> & CodeEnum> E code(E[] values, String code, String field) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values).filter(value -> value.getCode().equals(code)).findFirst()
            .orElseThrow(() -> new InvalidParameterException(field + " 取值无效"));
    }
}
