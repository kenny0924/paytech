package com.paytech.risk.control.domain.underwriting;

import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;

import lombok.Getter;

/**
 * 一个承保版本的可编辑内容：输入快照、结论字段，以及按二者计算的估算敞口
 * <li>草稿保存与提交时整体写入版本行；APPROVED 之后不再改写。</li>
 */
@Getter
public final class UnderwritingContent {
    /** 输入快照。 */
    private final UnderwritingInput input;

    /** 结论字段。 */
    private final UnderwritingTerms terms;

    /** 按输入与结算周期建议计算的估算敞口。 */
    private final ExposureEstimate exposure;

    /** 由已校验的输入与结论构造，同时计算估算敞口。 */
    public UnderwritingContent(UnderwritingInput input, UnderwritingTerms terms) {
        this(input, terms, ExposureEstimate.estimate(input, terms.getSuggestedSettlementDays()));
    }

    /** 由各部分构造。 */
    private UnderwritingContent(UnderwritingInput input, UnderwritingTerms terms, ExposureEstimate exposure) {
        this.input = input;
        this.terms = terms;
        this.exposure = exposure;
    }

    /**
     * 还原版本行保存的内容，估算敞口取冻结列
     *
     * @param row 版本行
     * @return 版本内容
     */
    public static UnderwritingContent of(ClientUnderwriting row) {
        UnderwritingInput input = inputOf(row);
        UnderwritingTerms terms = termsOf(row);
        return new UnderwritingContent(input, terms, ExposureEstimate.frozen(input, row.getEstimatedExposure(),
            row.getExposureRiskDays(), row.getSuggestedSettlementDays()));
    }

    /** 高风险登记标记：未填时取输入的卡组织高风险标记。 */
    public boolean highRiskMcc() {
        return terms.getHighRiskMcc() == null ? input.isNetworkHighRiskMcc() : terms.getHighRiskMcc();
    }

    /**
     * 把内容写入版本行（插入前准备或条件更新成功后同步）
     *
     * @param row 目标版本行
     */
    public void applyTo(ClientUnderwriting row) {
        applyInput(row);
        applyTerms(row);
        row.setReviewLevel(input.reviewLevel());
        row.setEstimatedExposure(exposure.getAmount());
        row.setExposureRiskDays(exposure.getRiskDays());
    }

    /** 写入输入快照列。 */
    void applyInput(ClientUnderwriting row) {
        row.setClientDisplayName(input.getClientDisplayName());
        row.setClientNo(input.getClientNo());
        row.setClientType(input.getClientType());
        row.setJurisdictionCode(input.getJurisdictionCode());
        row.setCrr(input.getCrr());
        row.setDdTier(input.getDdTier());
        row.setMccCode(input.getMccCode());
        row.setIndustryCode(input.getIndustryCode());
        row.setNetworkHighRiskMcc(input.isNetworkHighRiskMcc());
        row.setExpectedMonthlyVolume(input.getExpectedMonthlyVolume());
        row.setVolumeCurrency(input.getVolumeCurrency());
        row.setExpectedTicket(input.getExpectedTicket());
        row.setFulfillmentType(input.getFulfillmentType());
        row.setFulfillmentDays(input.getFulfillmentDays());
        row.setWebsiteUrl(input.getWebsiteUrl());
        row.setRefundPolicyUrl(input.getRefundPolicyUrl());
        row.setExpectedPaymentMethods(input.getExpectedPaymentMethods());
        row.setSourceCaseId(input.getSourceCaseId());
        row.setSourceCaseVersion(input.getSourceCaseVersion());
        row.setSourceApplicationId(input.getSourceApplicationId());
        row.setSourceDraftRevision(input.getSourceDraftRevision());
        row.setSourceClassificationConfirmedTime(input.getSourceClassificationConfirmedTime());
    }

    /** 写入结论列；数据库非空的布尔列未填时为 false，高风险登记标记未填时取默认。 */
    void applyTerms(ClientUnderwriting row) {
        row.setObservationDays(terms.getObservationDays());
        row.setObservationTransactions(terms.getObservationTransactions());
        row.setRequireDeposit(Boolean.TRUE.equals(terms.getRequireDeposit()));
        row.setDepositAmount(terms.getDepositAmount());
        row.setDepositCurrency(terms.getDepositCurrency());
        row.setDepositBlocksCollection(Boolean.TRUE.equals(terms.getDepositBlocksCollection()));
        row.setSuggestedSettlementDays(terms.getSuggestedSettlementDays());
        row.setHighRiskMcc(highRiskMcc());
        row.setHighRiskMccReason(terms.getHighRiskMccReason());
        row.setLimitNote(terms.getLimitNote());
        row.setUnderwritingNote(terms.getUnderwritingNote());
        row.setHandoverNote(terms.getHandoverNote());
    }

    /** 从版本行还原输入快照。 */
    static UnderwritingInput inputOf(ClientUnderwriting row) {
        UnderwritingInput input = new UnderwritingInput();
        input.setClientDisplayName(row.getClientDisplayName());
        input.setClientNo(row.getClientNo());
        input.setClientType(row.getClientType());
        input.setJurisdictionCode(row.getJurisdictionCode());
        input.setCrr(row.getCrr());
        input.setDdTier(row.getDdTier());
        input.setMccCode(row.getMccCode());
        input.setIndustryCode(row.getIndustryCode());
        input.setNetworkHighRiskMcc(row.isNetworkHighRiskMcc());
        input.setExpectedMonthlyVolume(row.getExpectedMonthlyVolume());
        input.setVolumeCurrency(row.getVolumeCurrency());
        input.setExpectedTicket(row.getExpectedTicket());
        input.setFulfillmentType(row.getFulfillmentType());
        input.setFulfillmentDays(row.getFulfillmentDays());
        input.setWebsiteUrl(row.getWebsiteUrl());
        input.setRefundPolicyUrl(row.getRefundPolicyUrl());
        input.setExpectedPaymentMethods(row.getExpectedPaymentMethods());
        input.setSourceCaseId(row.getSourceCaseId());
        input.setSourceCaseVersion(row.getSourceCaseVersion());
        input.setSourceApplicationId(row.getSourceApplicationId());
        input.setSourceDraftRevision(row.getSourceDraftRevision());
        input.setSourceClassificationConfirmedTime(row.getSourceClassificationConfirmedTime());
        return input;
    }

    /** 从版本行还原结论字段。 */
    static UnderwritingTerms termsOf(ClientUnderwriting row) {
        UnderwritingTerms terms = new UnderwritingTerms();
        terms.setObservationDays(row.getObservationDays());
        terms.setObservationTransactions(row.getObservationTransactions());
        terms.setRequireDeposit(row.isRequireDeposit());
        terms.setDepositAmount(row.getDepositAmount());
        terms.setDepositCurrency(row.getDepositCurrency());
        terms.setDepositBlocksCollection(row.isDepositBlocksCollection());
        terms.setSuggestedSettlementDays(row.getSuggestedSettlementDays());
        terms.setHighRiskMcc(row.isHighRiskMcc());
        terms.setHighRiskMccReason(row.getHighRiskMccReason());
        terms.setLimitNote(row.getLimitNote());
        terms.setUnderwritingNote(row.getUnderwritingNote());
        terms.setHandoverNote(row.getHandoverNote());
        return terms;
    }
}
