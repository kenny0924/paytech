package com.paytech.billing.domain.view;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.dal.dataobject.BillingFactLine;
import com.paytech.billing.core.dal.dataobject.BillingFactLineLink;
import com.paytech.billing.core.dal.dataobject.BillingFactPricing;
import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domain.BillingFactPayloads;

import lombok.Getter;
import lombok.Setter;

/**
 * 计费事实详情（多表组合）：事件载荷（头、引用、明细行），加登记状态、编制 / 复核人、发布时间、定价输入、行计算证据与行链接
 * <li>金额已按币种精度补齐；载荷与 BillingFactRecorded 事件 Payload 同源。</li>
 */
@Getter
@Setter
public class BillingFactView {
    /** 头、外部引用与全部明细行。 */
    private BillingFactPayload payload;

    /** 登记状态。 */
    private BillingFactStatus status;

    /**
     * 编制人
     * <li>AUTO 事实为空。</li>
     */
    private Long makerId;

    /**
     * 复核人
     * <li>未批准或 AUTO 事实为空。</li>
     */
    private Long checkerId;

    /**
     * 批准时间
     * <li>DRAFT 为空。</li>
     */
    private Instant approvedTime;

    /**
     * 事件发布确认时间
     * <li>未发布为空。</li>
     */
    private Instant publishedAt;

    /**
     * 定价与结算输入
     * <li>仅 PAYMENT 有行；本期人工登记为空。</li>
     */
    private BillingFactPricingView pricing;

    /** 明细行计算证据，与 payload.lines 按下标对应。 */
    private List<BillingFactLineEvidenceView> lineEvidence;

    /** 以本事实明细行为新行的行链接；无链接为空列表。 */
    private List<BillingFactLineLinkView> links;

    /** 规则计算的八位小数精度：基数与原始金额统一按它输出。 */
    static final int RAW_SCALE = 8;

    /**
     * 由事实上下文、定价输入与行链接组装详情
     *
     * @param context 事实头、引用与明细行
     * @param pricing 定价输入；非 PAYMENT 为空
     * @param links 以本事实明细行为新行的链接
     * @return 详情
     */
    public static BillingFactView of(BillingFactContext context, BillingFactPricing pricing,
        List<BillingFactLineLink> links) {
        BillingFact fact = context.getFact();
        BillingFactView view = new BillingFactView();
        view.setPayload(context.toPayload());
        view.setStatus(fact.getStatus());
        view.setMakerId(fact.getMakerId());
        view.setCheckerId(fact.getCheckerId());
        view.setApprovedTime(fact.getApprovedTime());
        view.setPublishedAt(fact.getPublishedAt());
        view.setPricing(pricing == null ? null : pricing(pricing));
        view.setLineEvidence(context.getLines().stream().map(BillingFactView::evidence).toList());
        view.setLinks(links.stream().map(link -> link(link, context.getMinorUnit())).toList());
        return view;
    }

    /** 定价输入（不含主键与技术列）。 */
    static BillingFactPricingView pricing(BillingFactPricing pricing) {
        BillingFactPricingView view = new BillingFactPricingView();
        view.setClientAgreementId(pricing.getClientAgreementId());
        view.setPartnerAgreementOeId(pricing.getPartnerAgreementOeId());
        view.setCommercialContextTime(pricing.getCommercialContextTime());
        view.setConsumerCountryCode(pricing.getConsumerCountryCode());
        view.setPaymentMethod(pricing.getPaymentMethod());
        view.setIndustryCode(pricing.getIndustryCode());
        view.setMccCode(pricing.getMccCode());
        view.setCardNetwork(pricing.getCardNetwork());
        view.setFundingType(pricing.getFundingType());
        view.setCardLevel(pricing.getCardLevel());
        view.setAuthenticationClass(pricing.getAuthenticationClass());
        view.setCommercialPriceId(pricing.getCommercialPriceId());
        view.setSettlementBusinessDays(pricing.getSettlementBusinessDays());
        view.setBusinessCalendarCode(pricing.getBusinessCalendarCode());
        view.setSettlementTimezone(pricing.getSettlementTimezone());
        view.setCutoffSecondOfDay(pricing.getCutoffSecondOfDay());
        return view;
    }

    /** 明细行计算证据：基数与原始金额统一为八位小数，费率去掉末尾零。 */
    static BillingFactLineEvidenceView evidence(BillingFactLine line) {
        BillingFactLineEvidenceView view = new BillingFactLineEvidenceView();
        view.setBasisAmount(line.getBasisAmount().setScale(RAW_SCALE, RoundingMode.UNNECESSARY));
        view.setRateValue(line.getRateValue() == null ? null : line.getRateValue().stripTrailingZeros());
        view.setRawAmount(line.getRawAmount().setScale(RAW_SCALE, RoundingMode.UNNECESSARY));
        view.setSourceRuleType(line.getSourceRuleType());
        view.setSourceRuleVersionId(line.getSourceRuleVersionId());
        view.setSourcePriceId(line.getSourcePriceId());
        view.setSourceRuleComponentId(line.getSourceRuleComponentId());
        view.setSourceEvidence(line.getSourceEvidence());
        return view;
    }

    /** 行链接；金额按币种精度补齐。 */
    static BillingFactLineLinkView link(BillingFactLineLink link, int minorUnit) {
        BillingFactLineLinkView view = new BillingFactLineLinkView();
        view.setSourceLineId(link.getSourceLineId());
        view.setTargetLineId(link.getTargetLineId());
        view.setLinkType(link.getLinkType());
        view.setLinkedAmount(BillingFactPayloads.scaled(link.getLinkedAmount(), minorUnit));
        return view;
    }
}
