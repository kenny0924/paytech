package com.paytech.commercial.domainservice.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domain.CommercialPriceCandidate;
import com.paytech.commercial.domain.CommercialPriceView;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * 价格命中与费率卡共用的只读组合：价格 → 画像与条件 → 组件与档位，批量读取后组合为 CommercialPriceView
 * <li>只读本库 Repository，不开事务、不写入；不是独立业务 Service，仅供本包两个查询 Service 复用。</li>
 */
@Component
@AllArgsConstructor
class CommercialPriceViewReader {
    private final PricingProfileRepository pricingProfileRepository;
    private final PricingProfileCondRepository pricingProfileCondRepository;
    private final CommercialTermComponentRepository commercialTermComponentRepository;
    private final CommercialTermComponentBandRepository commercialTermComponentBandRepository;

    /**
     * 价格与其画像、条件；画像缺失为系统数据不一致。
     *
     * @param prices 价格
     * @return 同顺序的候选
     */
    List<CommercialPriceCandidate> candidates(List<CommercialTermPrice> prices) {
        Map<String, PricingProfile> profiles = pricingProfileRepository.findByCodes(
                prices.stream().map(CommercialTermPrice::getPricingProfileCode).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(PricingProfile::getPricingProfileCode, Function.identity()));
        List<PricingProfileCond> conditions = pricingProfileCondRepository.findByProfileIds(
            profiles.values().stream().map(PricingProfile::getPricingProfileId).toList());
        return prices.stream().map(price -> {
            PricingProfile profile = profiles.get(price.getPricingProfileCode());
            if (profile == null) {
                throw new ErrorException("价格引用的画像不存在: " + price.getTermPriceId());
            }
            return new CommercialPriceCandidate(price, profile, conditions.stream()
                .filter(condition -> Objects.equals(condition.getPricingProfileId(), profile.getPricingProfileId())).toList());
        }).toList();
    }

    /**
     * 批量读取组件与档位并组合视图。
     *
     * @param plan 方案
     * @param version 版本
     * @param candidates 同版本的候选
     * @return 同顺序的视图
     */
    List<CommercialPriceView> views(CommercialTermPlan plan, CommercialTermVersion version,
        List<CommercialPriceCandidate> candidates) {
        List<CommercialTermComponent> components = commercialTermComponentRepository.findByPriceIds(
            candidates.stream().map(candidate -> candidate.price().getTermPriceId()).toList());
        List<CommercialTermComponentBand> bands = commercialTermComponentBandRepository.findByComponentIds(
            components.stream().map(CommercialTermComponent::getTermComponentId).toList());
        return candidates.stream().map(candidate -> {
            List<CommercialTermComponent> own = components.stream()
                .filter(component -> component.getTermPriceId().equals(candidate.price().getTermPriceId())).toList();
            List<Long> ownIds = own.stream().map(CommercialTermComponent::getTermComponentId).toList();
            return new CommercialPriceView(plan, version, candidate, own,
                bands.stream().filter(band -> ownIds.contains(band.getTermComponentId())).toList());
        }).toList();
    }
}
