package com.paytech.commercial.ext.rest;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.paytech.commercial.api.ratecard.response.RateCardResponseBody;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialPriceView;
import com.paytech.commercial.domain.RateCardView;
import com.paytech.foundation.common.enums.CodeEnum;

/** 费率卡 Wire 投影：分块与排序已由 RateCardQueryService 完成，这里只转换字段格式。 */
final class RateCardConverter {
    /** 纯静态转换。 */
    private RateCardConverter() {
    }

    /** 费率卡。 */
    static RateCardResponseBody toResponseBody(RateCardView view) {
        RateCardResponseBody body = new RateCardResponseBody();
        body.setClientId(Long.toString(view.getClientId()));
        body.setAsOf(view.getAsOf().toString());
        body.setCurrency(view.getCurrency());
        body.setAgreements(view.getAgreements().stream().map(RateCardConverter::agreement).toList());
        return body;
    }

    /** 一份合同。 */
    static RateCardResponseBody.AgreementRateCard agreement(RateCardView.AgreementCard card) {
        Agreement agreement = card.getAgreement();
        RateCardResponseBody.AgreementRateCard body = new RateCardResponseBody.AgreementRateCard();
        body.setAgreementId(Long.toString(agreement.getAgreementId()));
        body.setAgreementNo(agreement.getAgreementNo());
        body.setEntityNo(agreement.getEntityNo());
        body.setBusinessMode(CodeEnum.defaultCode(agreement.getBusinessMode()));
        body.setFundMode(CodeEnum.defaultCode(agreement.getFundMode()));
        body.setPricingAuthority(CodeEnum.defaultCode(agreement.getPricingAuthority()));
        body.setEffectiveFrom(CommercialTermConverter.text(agreement.getEffectiveFrom()));
        body.setEffectiveTo(CommercialTermConverter.text(agreement.getEffectiveTo()));
        body.setProducts(card.getProducts().stream().map(CodeEnum::getCode).toList());
        body.setCapabilities(card.getCapabilities().stream().map(RateCardConverter::capability).toList());
        body.setTermVersions(card.getVersions().stream().map(RateCardConverter::version).toList());
        return body;
    }

    /** 一个开通项。 */
    static RateCardResponseBody.CapabilityRateCard capability(RateCardView.CapabilityCard card) {
        AgreementCapability capability = card.getCapability();
        RateCardResponseBody.CapabilityRateCard body = new RateCardResponseBody.CapabilityRateCard();
        body.setAgreementCapabilityId(Long.toString(capability.getAgreementCapabilityId()));
        body.setCapabilityCode(capability.getCapabilityCode());
        if (card.getCatalog() != null) {
            body.setProductCode(CodeEnum.defaultCode(card.getCatalog().getProductCode()));
            body.setCapabilityKind(CodeEnum.defaultCode(card.getCatalog().getCapabilityKind()));
            body.setPaymentMethodCode(card.getCatalog().getPaymentMethodCode());
            body.setDimensionCode(CodeEnum.defaultCode(card.getCatalog().getDimensionCode()));
            body.setDimensionValue(card.getCatalog().getDimensionValue());
        }
        body.setEntityNo(capability.getEntityNo());
        body.setStatus(CodeEnum.defaultCode(capability.getStatus()));
        body.setEffectiveFrom(CommercialTermConverter.text(capability.getEffectiveFrom()));
        body.setEffectiveTo(CommercialTermConverter.text(capability.getEffectiveTo()));
        body.setRollingReserveRate(CommercialPriceConverter.rate(capability.getRollingReserveRate()));
        body.setRollingReserveDays(capability.getRollingReserveDays());
        body.setLimits(card.getLimits().stream().map(limit -> {
            RateCardResponseBody.CapabilityLimit item = new RateCardResponseBody.CapabilityLimit();
            item.setLimitCode(limit.getLimitCode().getCode());
            item.setLimitValue(limit.getLimitValue());
            return item;
        }).toList());
        body.setRequire3ds(Boolean.TRUE.equals(capability.getRequire3ds()));
        return body;
    }

    /** 一个版本。 */
    static RateCardResponseBody.TermVersionRateCard version(RateCardView.VersionCard card) {
        CommercialTermVersion version = card.getVersion();
        RateCardResponseBody.TermVersionRateCard body = new RateCardResponseBody.TermVersionRateCard();
        body.setPlanId(Long.toString(card.getPlan().getTermPlanId()));
        body.setTermType(card.getPlan().getTermType());
        body.setVersionId(Long.toString(version.getTermVersionId()));
        body.setVersionNo(Long.toString(version.getVersionNo()));
        body.setStatus(CodeEnum.defaultCode(version.getStatus()));
        body.setTiming(card.getTiming());
        body.setEffectiveFrom(CommercialTermConverter.text(version.getEffectiveFrom()));
        body.setEffectiveTo(CommercialTermConverter.text(version.getEffectiveTo()));
        body.setPricingModel(CodeEnum.defaultCode(version.getPricingModel()));
        body.setFeeTaxTreatment(CodeEnum.defaultCode(version.getFeeTaxTreatment()));
        body.setCapabilities(card.getCapabilities().stream().map(RateCardConverter::capabilityPrices).toList());
        return body;
    }

    /** 一个开通项的币种块。 */
    static RateCardResponseBody.CapabilityPrices capabilityPrices(RateCardView.CapabilityPrices prices) {
        RateCardResponseBody.CapabilityPrices body = new RateCardResponseBody.CapabilityPrices();
        body.setAgreementCapabilityId(Long.toString(prices.getCapability().getCapability().getAgreementCapabilityId()));
        body.setCapabilityCode(prices.getCapability().getCapability().getCapabilityCode());
        body.setCurrencies(prices.getCurrencies().stream().map(block -> {
            RateCardResponseBody.CurrencyPrices item = new RateCardResponseBody.CurrencyPrices();
            item.setCurrency(block.getCurrency());
            item.setProfiles(block.getPrices().stream().map(RateCardConverter::profile).toList());
            return item;
        }).toList());
        return body;
    }

    /** 一个画像的价格，组件按计费事件分组。 */
    static RateCardResponseBody.ProfilePrice profile(CommercialPriceView view) {
        RateCardResponseBody.ProfilePrice body = new RateCardResponseBody.ProfilePrice();
        body.setPriceId(Long.toString(view.candidate().price().getTermPriceId()));
        body.setPricingProfileCode(view.candidate().price().getPricingProfileCode());
        body.setProfileName(view.candidate().profile().getProfileName());
        body.setSpecificity(view.candidate().specificity());
        body.setPriorityNo(view.candidate().price().getPriorityNo());
        body.setConditions(CommercialPriceConverter.conditions(view.candidate().conditions()));
        body.setSettlement(CommercialPriceConverter.settlement(view.candidate().price()));
        Map<String, List<CommercialTermComponent>> byEvent = new TreeMap<>();
        view.components().forEach(component -> byEvent.computeIfAbsent(component.getTriggerCode(),
            key -> new java.util.ArrayList<>()).add(component));
        body.setFeeEvents(byEvent.entrySet().stream().map(entry -> {
            RateCardResponseBody.FeeEventComponents item = new RateCardResponseBody.FeeEventComponents();
            item.setFeeEventCode(entry.getKey());
            item.setComponents(entry.getValue().stream().sorted(Comparator.comparing(CommercialTermComponent::getComponentNo))
                .map(component -> CommercialPriceConverter.component(view, component)).toList());
            return item;
        }).toList());
        return body;
    }
}
