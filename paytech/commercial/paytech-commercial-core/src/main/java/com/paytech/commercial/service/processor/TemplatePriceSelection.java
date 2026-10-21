package com.paytech.commercial.service.processor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.domain.CommercialPriceCandidate;
import com.paytech.commercial.domainservice.validator.term.TermPriceRelevance;

import lombok.Getter;

/**
 * 基于标准价目模板生成 / 补齐合同价格（technique.md §4.4）：纯计算，不访问数据库
 * <li>只取模板中 OE 等于合同 OE 的价格；先按价格行本身筛选（支付方式类画像须对应一个已开通的支付方式类开通项：支付方式相等、币种在
 * CURRENCY 条件内、画像条件与接受条件相容），不相关的整行不生成；再在保留的行内逐组件判断（功能事件须开通对应功能等），剔除不相关组件，
 * 剔除后没有组件的价格不生成。</li>
 * <li>每条新价格记录来源模板价格 ID；结算四字段、组件与档位沿用模板值，组件按保留顺序重新编号。</li>
 * <li>补齐时跳过目标版本已有的（OE × 币种 × 画像），同 OE + 币种内优先级冲突时顺延到当前最大值之后。</li>
 */
@Getter
final class TemplatePriceSelection {
    /** 新价格。 */
    private final List<CommercialTermPrice> prices = new ArrayList<>();

    /** 新组件。 */
    private final List<CommercialTermComponent> components = new ArrayList<>();

    /** 新档位。 */
    private final List<CommercialTermComponentBand> bands = new ArrayList<>();

    /**
     * 从模板价格选出并复制相关的价格行
     *
     * @param template 模板版本的全部价格候选（含画像与条件）
     * @param templateComponents 模板组件
     * @param templateBands 模板档位
     * @param relevance 目标合同的相关性判断
     * @param entityNo 合同 OE
     * @param existing 目标版本已有价格（补齐时），新建为空
     * @param versionId 目标版本 ID
     * @param ids ID 生成
     * @param now 业务时刻
     * @return 选择结果
     */
    static TemplatePriceSelection select(List<CommercialPriceCandidate> template, List<CommercialTermComponent> templateComponents,
        List<CommercialTermComponentBand> templateBands, TermPriceRelevance relevance, String entityNo,
        List<CommercialTermPrice> existing, long versionId, LongSupplier ids, Instant now) {
        TemplatePriceSelection selection = new TemplatePriceSelection();
        Set<String> present = new HashSet<>();
        Map<String, Integer> maxPriority = new HashMap<>();
        Map<String, Set<Integer>> usedPriority = new HashMap<>();
        existing.forEach(price -> {
            present.add(price.getCurrency() + "/" + price.getPricingProfileCode());
            usedPriority.computeIfAbsent(price.getCurrency(), key -> new HashSet<>()).add(price.getPriorityNo());
            maxPriority.merge(price.getCurrency(), price.getPriorityNo(), Math::max);
        });
        template.stream().filter(candidate -> candidate.price().getEntityNo().equals(entityNo))
            .filter(candidate -> !present.contains(candidate.price().getCurrency() + "/"
                + candidate.price().getPricingProfileCode()))
            .sorted(Comparator.comparing((CommercialPriceCandidate candidate) -> candidate.price().getCurrency())
                .thenComparing(candidate -> candidate.price().getPriorityNo()))
            .forEach(candidate -> selection.add(candidate, templateComponents, templateBands, relevance, versionId, ids, now,
                maxPriority, usedPriority));
        return selection;
    }

    /** 价格行与合同相关时复制其中相关的组件；行不相关或没有相关组件时不生成。 */
    void add(CommercialPriceCandidate candidate, List<CommercialTermComponent> templateComponents,
        List<CommercialTermComponentBand> templateBands, TermPriceRelevance relevance, long versionId, LongSupplier ids,
        Instant now, Map<String, Integer> maxPriority, Map<String, Set<Integer>> usedPriority) {
        if (relevance.rowIssue(candidate) != null) {
            return;
        }
        List<CommercialTermComponent> kept = templateComponents.stream()
            .filter(component -> component.getTermPriceId().equals(candidate.price().getTermPriceId()))
            .sorted(Comparator.comparing(CommercialTermComponent::getComponentNo))
            .filter(component -> relevance.issue(candidate, List.of(component)) == null).toList();
        if (kept.isEmpty()) {
            return;
        }
        CommercialTermPrice source = candidate.price();
        CommercialTermPrice price = TermPriceRows.copy(source, ids.getAsLong(), versionId, now);
        price.setSourceTemplatePriceId(source.getTermPriceId());
        price.setPriorityNo(priority(source, maxPriority, usedPriority));
        prices.add(price);
        for (int index = 0; index < kept.size(); index++) {
            CommercialTermComponent component = TermPriceRows.copy(kept.get(index), ids.getAsLong(), price.getTermPriceId(), now);
            component.setComponentNo(index + 1);
            components.add(component);
            long sourceComponentId = kept.get(index).getTermComponentId();
            templateBands.stream().filter(band -> band.getTermComponentId().equals(sourceComponentId))
                .forEach(band -> bands.add(TermPriceRows.copy(band, ids.getAsLong(), component.getTermComponentId(), now)));
        }
    }

    /** 模板优先级在目标同币种内未占用时沿用，否则顺延到当前最大值之后。 */
    static int priority(CommercialTermPrice source, Map<String, Integer> maxPriority, Map<String, Set<Integer>> usedPriority) {
        Set<Integer> used = usedPriority.computeIfAbsent(source.getCurrency(), key -> new HashSet<>());
        int priority = used.contains(source.getPriorityNo())
            ? maxPriority.getOrDefault(source.getCurrency(), 0) + 1 : source.getPriorityNo();
        used.add(priority);
        maxPriority.merge(source.getCurrency(), priority, Math::max);
        return priority;
    }
}
