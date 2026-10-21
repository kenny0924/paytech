package com.paytech.commercial.domainservice.validator.term;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.enums.CommercialTermComponentBandMode;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * DRAFT 价格集合整体替换时的写入校验（字段与归属层面）：OE 覆盖、画像存在、同 OE + 币种内画像与优先级唯一、结算四字段与条款类型
 * 匹配、计费事件已登记、组件类型矩阵与档位取值
 * <li>档位连续、组件币种与价格一致、画像 ACTIVE、本期取值限制等在排期 / 激活时由 {@link TermVersionActivationValidator} 重新校验。</li>
 * <li>纯校验，不访问数据库，失败抛出 BizCheckFailException（400）。</li>
 */
public final class TermPriceSetValidator {
    /** 本期唯一结算时区。 */
    static final String SETTLEMENT_TIMEZONE = "UTC";

    /** 纯静态校验。 */
    private TermPriceSetValidator() {
    }

    /**
     * 校验 Context 中待写入的价格集合
     *
     * @param context 含方案、合同覆盖 OE、引用画像、计费事件与新行
     */
    public static void check(CommercialTermVersionContext context) {
        Set<String> profileKeys = new HashSet<>();
        Set<String> priorityKeys = new HashSet<>();
        Set<String> feeEvents = new HashSet<>();
        context.getFeeEvents().stream().map(ProductFeeEvent::getFeeEventCode).forEach(feeEvents::add);
        for (CommercialTermPrice price : context.getPrices()) {
            String scope = price.getEntityNo() + "/" + price.getCurrency();
            checkOwnership(context, price);
            if (!profileKeys.add(scope + "/" + price.getPricingProfileCode())) {
                throw new BizCheckFailException("同一 OE 与币种下画像重复: " + scope + ", 画像 " + price.getPricingProfileCode());
            }
            if (!priorityKeys.add(scope + "/" + price.getPriorityNo())) {
                throw new BizCheckFailException("同一 OE 与币种下优先级重复: " + scope + ", 优先级 " + price.getPriorityNo());
            }
            checkSettlement(context.clientTerm(), price);
            List<CommercialTermComponent> components = context.getComponents().stream()
                .filter(component -> component.getTermPriceId().equals(price.getTermPriceId())).toList();
            checkComponents(context, price, components, feeEvents);
        }
    }

    /** OE 被合同覆盖且画像存在。 */
    static void checkOwnership(CommercialTermVersionContext context, CommercialTermPrice price) {
        if (!context.covers(price.getEntityNo())) {
            throw new BizCheckFailException(context.template() ? "模板价格 OE 必须等于模板 OE: " + price.getEntityNo()
                : "合同不覆盖 OE: " + price.getEntityNo() + ", 合同 " + context.getAgreement().getAgreementId());
        }
        boolean profileExists = context.getProfiles().stream().map(PricingProfile::getPricingProfileCode)
            .anyMatch(price.getPricingProfileCode()::equals);
        if (!profileExists) {
            throw new BizCheckFailException("定价画像不存在: " + price.getPricingProfileCode());
        }
    }

    /** Client 条款结算四字段全部非空（本期 UTC），Partner 条款全部为空。 */
    static void checkSettlement(boolean clientTerm, CommercialTermPrice price) {
        boolean anyPresent = price.getSettlementBusinessDays() != null || price.getBusinessCalendarCode() != null
            || price.getSettlementTimezone() != null || price.getCutoffSecondOfDay() != null;
        if (!clientTerm) {
            if (anyPresent) {
                throw new BizCheckFailException("Partner 条款价格不得填写结算字段: " + price.getEntityNo() + "/"
                    + price.getCurrency() + "/" + price.getPricingProfileCode());
            }
            return;
        }
        boolean allPresent = price.getSettlementBusinessDays() != null && price.getBusinessCalendarCode() != null
            && price.getSettlementTimezone() != null && price.getCutoffSecondOfDay() != null;
        if (!allPresent || !SETTLEMENT_TIMEZONE.equals(price.getSettlementTimezone())) {
            throw new BizCheckFailException("Client 条款价格结算四字段必须全部填写且时区为 UTC: " + price.getEntityNo() + "/"
                + price.getCurrency() + "/" + price.getPricingProfileCode());
        }
    }

    /** 组件代码唯一、计费事件已登记、计算基数与条款类型相容、类型矩阵与档位取值合法。 */
    static void checkComponents(CommercialTermVersionContext context, CommercialTermPrice price,
        List<CommercialTermComponent> components, Set<String> feeEvents) {
        Set<String> codes = new HashSet<>();
        for (CommercialTermComponent component : components) {
            String label = price.getPricingProfileCode() + "/" + component.getComponentCode();
            if (!codes.add(component.getComponentCode())) {
                throw new BizCheckFailException("同一价格下组件代码重复: " + label);
            }
            if (!feeEvents.contains(component.getTriggerCode())) {
                throw new BizCheckFailException("计费事件未在条款类型 " + context.getPlan().getTermType() + " 下登记: "
                    + component.getTriggerCode());
            }
            if (component.getCalculationBase() == CalculationBase.BUY_RATE_DIFFERENCE
                && !CommercialCodes.TermType.PARTNER_SHARE.equals(context.getPlan().getTermType())) {
                throw new BizCheckFailException("BUY_RATE_DIFFERENCE 仅 PARTNER_SHARE 可用: " + label);
            }
            List<CommercialTermComponentBand> bands = context.getBands().stream()
                .filter(band -> band.getTermComponentId().equals(component.getTermComponentId())).toList();
            checkMatrix(component, bands, label);
        }
    }

    /** PERCENTAGE / FIXED / TIERED 字段矩阵。 */
    static void checkMatrix(CommercialTermComponent component, List<CommercialTermComponentBand> bands, String label) {
        checkRate(component.getRate(), label);
        switch (component.getComponentType()) {
            case PERCENTAGE -> require(component.getRate() != null && component.getFixedAmount() == null && bands.isEmpty()
                && component.getBandBasis() == null && component.getBandMode() == null
                && (component.getCurrency() != null || (component.getMinAmount() == null && component.getMaxAmount() == null))
                && notInverted(component.getMinAmount(), component.getMaxAmount()), "PERCENTAGE 组件字段不符合矩阵: " + label);
            case FIXED -> require(component.getFixedAmount() != null && component.getCurrency() != null
                && component.getRate() == null && component.getMinAmount() == null && component.getMaxAmount() == null
                && bands.isEmpty() && component.getBandBasis() == null && component.getBandMode() == null,
                "FIXED 组件字段不符合矩阵: " + label);
            case TIERED -> checkTiered(component, bands, label);
        }
    }

    /** TIERED：头部无费率与固定额，基数、模式、币种与至少一档必填，每档费率与固定额恰好一个。 */
    static void checkTiered(CommercialTermComponent component, List<CommercialTermComponentBand> bands, String label) {
        require(component.getRate() == null && component.getFixedAmount() == null && component.getMinAmount() == null
            && component.getMaxAmount() == null && component.getBandBasis() != null && component.getBandMode() != null
            && component.getCurrency() != null && !bands.isEmpty(), "TIERED 组件字段不符合矩阵: " + label);
        require(component.getBandMode() != CommercialTermComponentBandMode.RETROACTIVE
            || component.getBandBasis() == BandBasis.PERIOD_VOLUME, "RETROACTIVE 只允许与 PERIOD_VOLUME 组合: " + label);
        for (CommercialTermComponentBand band : bands) {
            checkRate(band.getRate(), label);
            require((band.getRate() == null) != (band.getFixedAmount() == null), "档位费率与固定金额必须恰好一个: " + label);
            require(component.getBandMode() != CommercialTermComponentBandMode.PROGRESSIVE || band.getRate() != null,
                "PROGRESSIVE 档位只允许费率: " + label);
        }
    }

    /** 费率不超过 1。 */
    static void checkRate(BigDecimal rate, String label) {
        require(rate == null || rate.compareTo(BigDecimal.ONE) <= 0, "费率不能超过 1: " + label);
    }

    /** 最低金额不高于最高金额。 */
    static boolean notInverted(BigDecimal min, BigDecimal max) {
        return min == null || max == null || min.compareTo(max) <= 0;
    }

    /** 条件不满足时抛出业务检查失败。 */
    static void require(boolean condition, String message) {
        if (!condition) {
            throw new BizCheckFailException(message);
        }
    }
}
