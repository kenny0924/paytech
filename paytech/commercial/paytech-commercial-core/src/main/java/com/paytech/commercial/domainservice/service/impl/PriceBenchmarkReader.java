package com.paytech.commercial.domainservice.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.domain.PriceBenchmark;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;

import lombok.AllArgsConstructor;

/**
 * 破标比较的只读组合：价格 → 来源模板价格的同代码组件与档位 → PriceBenchmark
 * <li>只读本库 Repository，不开事务、不写入；不是独立业务 Service，供版本附加信息与固定版本价格全集两个查询复用。</li>
 */
@Component
@AllArgsConstructor
class PriceBenchmarkReader {
    private final CommercialTermComponentRepository commercialTermComponentRepository;
    private final CommercialTermComponentBandRepository commercialTermComponentBandRepository;

    /**
     * 按来源模板价格匹配同代码组件后逐个比较
     *
     * @param prices 价格
     * @param components 这些价格的组件
     * @return 组件 ID → 比较结果；价格没有来源模板时为 NONE
     */
    Map<Long, PriceBenchmark> benchmarks(List<CommercialTermPrice> prices, List<CommercialTermComponent> components) {
        List<Long> sourceIds = prices.stream().map(CommercialTermPrice::getSourceTemplatePriceId).filter(Objects::nonNull)
            .distinct().toList();
        List<CommercialTermComponent> standards = sourceIds.isEmpty() ? List.of()
            : commercialTermComponentRepository.findByPriceIds(sourceIds);
        List<CommercialTermComponentBand> bands = commercialTermComponentBandRepository.findByComponentIds(
            Stream.concat(components.stream(), standards.stream())
                .map(CommercialTermComponent::getTermComponentId).toList());
        Map<Long, Long> sources = new HashMap<>();
        prices.stream().filter(price -> price.getSourceTemplatePriceId() != null)
            .forEach(price -> sources.put(price.getTermPriceId(), price.getSourceTemplatePriceId()));
        Map<Long, PriceBenchmark> result = new HashMap<>();
        for (CommercialTermComponent component : components) {
            Long source = sources.get(component.getTermPriceId());
            CommercialTermComponent standard = source == null ? null : standards.stream()
                .filter(item -> item.getTermPriceId().equals(source) && item.getComponentCode().equals(component.getComponentCode()))
                .findFirst().orElse(null);
            result.put(component.getTermComponentId(), standard == null ? PriceBenchmark.NONE
                : PriceBenchmark.compare(component, bandsOf(bands, component), standard, bandsOf(bands, standard)));
        }
        return result;
    }

    /** 某组件的档位。 */
    static List<CommercialTermComponentBand> bandsOf(List<CommercialTermComponentBand> bands, CommercialTermComponent component) {
        return bands.stream().filter(band -> band.getTermComponentId().equals(component.getTermComponentId())).toList();
    }
}
