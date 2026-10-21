package com.paytech.commercial.domainservice.validator.term;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PriceRelevanceReason;
import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.domain.CapabilityAcceptance;
import com.paytech.commercial.domain.CommercialPriceCandidate;
import com.paytech.commercial.domain.PriceRelevanceIssue;

/**
 * Client 合同方案的价格相关性（technique.md §4.7）：每条价格须对应一个 ENABLED 开通项
 * <li>价格行先要有锚定开通项：支付方式类画像的价格须对应一个支付方式类开通项（画像支付方式相等、币种在 CURRENCY 条件内、画像条件与接受条件相容），
 * 不满足时整行不相关，与其组件无关。</li>
 * <li>在此前提下，组件触发的计费事件挂接种类再逐项要求：无需开通的事件不要求；FEATURE 须有同代码功能开通项；其他种类须有同种类开通项且同样满足
 * 支付方式、币种与条件相容。</li>
 * <li>零开通：价格所在 OE 没有任何本方案计费事件种类的 ENABLED 开通项。</li>
 * <li>纯计算，不访问数据库；保存价格时作为提示，排期与激活时拒绝，基于模板生成时用于筛选。</li>
 */
public final class TermPriceRelevance {
    private final List<CapabilityAcceptance> capabilities;

    private final Map<String, ProductFeeEvent> feeEvents;

    /**
     * 组合合同开通项与方案条款类型的计费事件。
     *
     * @param capabilities 合同全部开通项（含条件与目录）
     * @param feeEvents 方案条款类型登记的计费事件
     */
    public TermPriceRelevance(List<CapabilityAcceptance> capabilities, List<ProductFeeEvent> feeEvents) {
        this.capabilities = capabilities.stream().filter(CapabilityAcceptance::enabled).toList();
        this.feeEvents = feeEvents.stream().collect(Collectors.toMap(ProductFeeEvent::getFeeEventCode, Function.identity(),
            (left, right) -> left));
    }

    /**
     * 一组价格的不相关原因，每条价格至多一条
     *
     * @param candidates 价格与画像
     * @param components 这些价格的组件
     * @return 按价格顺序
     */
    public List<PriceRelevanceIssue> issues(List<CommercialPriceCandidate> candidates, List<CommercialTermComponent> components) {
        List<PriceRelevanceIssue> issues = new ArrayList<>();
        for (CommercialPriceCandidate candidate : candidates) {
            PriceRelevanceIssue issue = issue(candidate, components.stream()
                .filter(component -> component.getTermPriceId().equals(candidate.price().getTermPriceId())).toList());
            if (issue != null) {
                issues.add(issue);
            }
        }
        return issues;
    }

    /**
     * 一条价格（及给定组件）的不相关原因
     *
     * @param candidate 价格与画像
     * @param components 该价格的组件
     * @return 不相关原因，相关为 null
     */
    public PriceRelevanceIssue issue(CommercialPriceCandidate candidate, List<CommercialTermComponent> components) {
        List<ProductFeeEvent> required = components.stream().map(component -> feeEvents.get(component.getTriggerCode()))
            .filter(event -> event != null && event.getCapabilityKind() != null).distinct().toList();
        Set<CapabilityKind> kinds = requiredKinds(candidate, required);
        if (kinds.isEmpty() && required.isEmpty()) {
            return null;
        }
        if (!hasPlanCapability(candidate.price().getEntityNo())) {
            return issue(candidate, PriceRelevanceReason.NO_CAPABILITY_ON_ENTITY, "该 OE 没有与本方案计费事件相关的已开通项");
        }
        for (CapabilityKind kind : kinds) {
            PriceRelevanceIssue issue = kindIssue(candidate, kind);
            if (issue != null) {
                return issue;
            }
        }
        for (ProductFeeEvent event : required) {
            PriceRelevanceIssue issue = event.getCapabilityKind() == CapabilityKind.FEATURE ? featureIssue(candidate, event) : null;
            if (issue != null) {
                return issue;
            }
        }
        return null;
    }

    /**
     * 价格行本身的不相关原因（锚定开通项），与组件无关；基于模板生成时先按它筛选价格行
     *
     * @param candidate 价格与画像
     * @return 不相关原因，相关或无需锚定为 null
     */
    public PriceRelevanceIssue rowIssue(CommercialPriceCandidate candidate) {
        if (anchorKind(candidate) == null) {
            return null;
        }
        if (!hasPlanCapability(candidate.price().getEntityNo())) {
            return issue(candidate, PriceRelevanceReason.NO_CAPABILITY_ON_ENTITY, "该 OE 没有与本方案计费事件相关的已开通项");
        }
        return kindIssue(candidate, anchorKind(candidate));
    }

    /** 须检查的非功能种类：先锚定种类，再加组件计费事件挂接的其他非功能种类。 */
    static Set<CapabilityKind> requiredKinds(CommercialPriceCandidate candidate, List<ProductFeeEvent> required) {
        Set<CapabilityKind> kinds = new LinkedHashSet<>();
        if (anchorKind(candidate) != null) {
            kinds.add(anchorKind(candidate));
        }
        required.stream().map(ProductFeeEvent::getCapabilityKind).filter(kind -> kind != CapabilityKind.FEATURE)
            .forEach(kinds::add);
        return kinds;
    }

    /** 价格行的锚定开通项种类：支付方式类画像锚定支付方式类开通项，其他画像不锚定。 */
    static CapabilityKind anchorKind(CommercialPriceCandidate candidate) {
        return candidate.profile().getProfileKind() == PricingProfileKind.PAYMENT_METHOD ? CapabilityKind.PAYMENT_METHOD : null;
    }

    /** 功能类事件：须有同代码的 ENABLED 功能开通项。 */
    PriceRelevanceIssue featureIssue(CommercialPriceCandidate candidate, ProductFeeEvent event) {
        boolean enabled = capabilities.stream().anyMatch(capability -> capability.on(candidate.price().getEntityNo())
            && capability.kind() == CapabilityKind.FEATURE
            && (event.getCapabilityCode() == null || event.getCapabilityCode().equals(capability.capability().getCapabilityCode())));
        return enabled ? null : issue(candidate, PriceRelevanceReason.FEATURE_NOT_ENABLED,
            "计费事件 " + event.getFeeEventCode() + " 对应的功能未开通: " + Objects.toString(event.getCapabilityCode(), "FEATURE"));
    }

    /** 其他种类：同种类开通项中取最接近的不相关原因（支付方式 → 币种 → 条件）。 */
    PriceRelevanceIssue kindIssue(CommercialPriceCandidate candidate, CapabilityKind kind) {
        List<CapabilityAcceptance> ofKind = capabilities.stream()
            .filter(capability -> capability.on(candidate.price().getEntityNo()) && capability.kind() == kind).toList();
        if (ofKind.isEmpty()) {
            return issue(candidate, kind == CapabilityKind.PAYMENT_METHOD ? PriceRelevanceReason.PAYMENT_METHOD_NOT_ENABLED
                : PriceRelevanceReason.NO_CAPABILITY_ON_ENTITY, "该 OE 没有 " + kind.getCode() + " 类已开通项");
        }
        PriceRelevanceReason closest = PriceRelevanceReason.PAYMENT_METHOD_NOT_ENABLED;
        for (CapabilityAcceptance capability : ofKind) {
            PriceRelevanceReason reason = capability.mismatch(candidate);
            if (reason == null) {
                return null;
            }
            closest = reason.ordinal() > closest.ordinal() ? reason : closest;
        }
        return issue(candidate, closest, message(closest, candidate));
    }

    /** OE 是否有任何本方案计费事件种类的 ENABLED 开通项（零开通判断）。 */
    boolean hasPlanCapability(String entityNo) {
        Set<CapabilityKind> kinds = feeEvents.values().stream().map(ProductFeeEvent::getCapabilityKind)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        return capabilities.stream().anyMatch(capability -> capability.on(entityNo) && kinds.contains(capability.kind()));
    }

    /** 原因说明。 */
    static String message(PriceRelevanceReason reason, CommercialPriceCandidate candidate) {
        return switch (reason) {
            case PAYMENT_METHOD_NOT_ENABLED -> "画像支付方式未开通: " + candidate.profile().getPaymentMethodCode();
            case CURRENCY_NOT_ENABLED -> "价格币种不在开通项币种条件内: " + candidate.price().getCurrency();
            case CONDITION_OUT_OF_SCOPE -> "画像条件与开通项接受条件没有交集: " + candidate.price().getPricingProfileCode();
            default -> reason.getCode();
        };
    }

    /** 构造一条原因。 */
    static PriceRelevanceIssue issue(CommercialPriceCandidate candidate, PriceRelevanceReason reason, String message) {
        return new PriceRelevanceIssue(candidate.price().getEntityNo(), candidate.price().getCurrency(),
            candidate.price().getPricingProfileCode(), reason, message);
    }
}
