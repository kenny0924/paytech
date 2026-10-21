package com.paytech.commercial.service.processor;

import java.time.Instant;

import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.service.processor.request.ReplaceCommercialTermPricesRequest;

/** 价格集合行的构造：复制版本时以新 ID 与新父 ID 重建（业务字段逐列沿用来源），整体替换时由请求项构造。 */
final class TermPriceRows {
    /** 纯静态构造。 */
    private TermPriceRows() {
    }

    /** 复制价格（含来源模板价格）。 */
    static CommercialTermPrice copy(CommercialTermPrice source, long priceId, long versionId, Instant now) {
        CommercialTermPrice price = new CommercialTermPrice();
        price.setTermPriceId(priceId);
        price.setTermVersionId(versionId);
        price.setEntityNo(source.getEntityNo());
        price.setCurrency(source.getCurrency());
        price.setPricingProfileCode(source.getPricingProfileCode());
        price.setPriorityNo(source.getPriorityNo());
        price.setSettlementBusinessDays(source.getSettlementBusinessDays());
        price.setBusinessCalendarCode(source.getBusinessCalendarCode());
        price.setSettlementTimezone(source.getSettlementTimezone());
        price.setCutoffSecondOfDay(source.getCutoffSecondOfDay());
        price.setSourceTemplatePriceId(source.getSourceTemplatePriceId());
        price.setCreateTime(now);
        price.setUpdateTime(now);
        return price;
    }

    /** 复制组件。 */
    static CommercialTermComponent copy(CommercialTermComponent source, long componentId, long priceId, Instant now) {
        CommercialTermComponent component = new CommercialTermComponent();
        component.setTermComponentId(componentId);
        component.setTermPriceId(priceId);
        component.setComponentNo(source.getComponentNo());
        component.setComponentCode(source.getComponentCode());
        component.setComponentType(source.getComponentType());
        component.setTriggerCode(source.getTriggerCode());
        component.setCalculationBase(source.getCalculationBase());
        component.setRefundBehavior(source.getRefundBehavior());
        component.setRate(source.getRate());
        component.setFixedAmount(source.getFixedAmount());
        component.setCurrency(source.getCurrency());
        component.setMinAmount(source.getMinAmount());
        component.setMaxAmount(source.getMaxAmount());
        component.setBandBasis(source.getBandBasis());
        component.setBandMode(source.getBandMode());
        component.setCreateTime(now);
        component.setUpdateTime(now);
        return component;
    }

    /** 复制档位。 */
    static CommercialTermComponentBand copy(CommercialTermComponentBand source, long bandId, long componentId, Instant now) {
        CommercialTermComponentBand band = new CommercialTermComponentBand();
        band.setTermBandId(bandId);
        band.setTermComponentId(componentId);
        band.setBandNo(source.getBandNo());
        band.setLowerBound(source.getLowerBound());
        band.setUpperBound(source.getUpperBound());
        band.setRate(source.getRate());
        band.setFixedAmount(source.getFixedAmount());
        band.setCreateTime(now);
        band.setUpdateTime(now);
        return band;
    }

    /** 由请求项构造价格。 */
    static CommercialTermPrice price(ReplaceCommercialTermPricesRequest.PriceItem item, long priceId, long versionId,
        Instant now) {
        CommercialTermPrice price = new CommercialTermPrice();
        price.setTermPriceId(priceId);
        price.setTermVersionId(versionId);
        price.setEntityNo(item.getEntityNo());
        price.setCurrency(item.getCurrency());
        price.setPricingProfileCode(item.getPricingProfileCode());
        price.setPriorityNo(item.getPriorityNo());
        price.setSettlementBusinessDays(item.getSettlementBusinessDays());
        price.setBusinessCalendarCode(item.getBusinessCalendarCode());
        price.setSettlementTimezone(item.getSettlementTimezone());
        price.setCutoffSecondOfDay(item.getCutoffSecondOfDay());
        price.setSourceTemplatePriceId(item.getSourceTemplatePriceId());
        price.setCreateTime(now);
        price.setUpdateTime(now);
        return price;
    }

    /** 由请求项构造组件，序号为列表位置 + 1。 */
    static CommercialTermComponent component(ReplaceCommercialTermPricesRequest.ComponentItem item, int componentNo,
        long componentId, long priceId, Instant now) {
        CommercialTermComponent component = new CommercialTermComponent();
        component.setTermComponentId(componentId);
        component.setTermPriceId(priceId);
        component.setComponentNo(componentNo);
        component.setComponentCode(item.getComponentCode());
        component.setComponentType(item.getComponentType());
        component.setTriggerCode(item.getTriggerCode());
        component.setCalculationBase(item.getCalculationBase());
        component.setRefundBehavior(item.getRefundBehavior());
        component.setRate(item.getRate());
        component.setFixedAmount(item.getFixedAmount());
        component.setCurrency(item.getCurrency());
        component.setMinAmount(item.getMinAmount());
        component.setMaxAmount(item.getMaxAmount());
        component.setBandBasis(item.getBandBasis());
        component.setBandMode(item.getBandMode());
        component.setCreateTime(now);
        component.setUpdateTime(now);
        return component;
    }

    /** 由请求项构造档位，序号为列表位置 + 1。 */
    static CommercialTermComponentBand band(ReplaceCommercialTermPricesRequest.BandItem item, int bandNo, long bandId,
        long componentId, Instant now) {
        CommercialTermComponentBand band = new CommercialTermComponentBand();
        band.setTermBandId(bandId);
        band.setTermComponentId(componentId);
        band.setBandNo(bandNo);
        band.setLowerBound(item.getLowerBound());
        band.setUpperBound(item.getUpperBound());
        band.setRate(item.getRate());
        band.setFixedAmount(item.getFixedAmount());
        band.setCreateTime(now);
        band.setUpdateTime(now);
        return band;
    }
}
