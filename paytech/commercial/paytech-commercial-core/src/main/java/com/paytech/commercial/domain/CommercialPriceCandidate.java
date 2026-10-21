package com.paytech.commercial.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Set;

import com.paytech.commercial.api.enums.CommercialPriceErrorCode;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.foundation.common.error.FailException;

/**
 * 一条价格及其画像与条件，承载命中所需的纯判断（价格命中、激活覆盖校验、费率卡排序共用）
 * <li>条件同维度多值按 OR、不同维度按 AND；具体度为不同条件维度数。</li>
 */
public class CommercialPriceCandidate {
    private final CommercialTermPrice price;

    private final PricingProfile profile;

    private final List<PricingProfileCond> conditions;

    /**
     * 组合价格、画像与条件。
     *
     * @param price 价格
     * @param profile 价格引用的画像
     * @param conditions 画像的全部条件
     */
    public CommercialPriceCandidate(CommercialTermPrice price, PricingProfile profile, List<PricingProfileCond> conditions) {
        this.price = Objects.requireNonNull(price, "price");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.conditions = List.copyOf(conditions);
    }

    /** 价格。 */
    public CommercialTermPrice price() {
        return price;
    }

    /** 画像。 */
    public PricingProfile profile() {
        return profile;
    }

    /** 画像条件。 */
    public List<PricingProfileCond> conditions() {
        return conditions;
    }

    /** 条件使用的不同维度。 */
    public Set<PricingDimension> dimensions() {
        Set<PricingDimension> dimensions = EnumSet.noneOf(PricingDimension.class);
        conditions.forEach(condition -> dimensions.add(condition.getDimensionCode()));
        return dimensions;
    }

    /** 具体度：不同条件维度数。 */
    public int specificity() {
        return dimensions().size();
    }

    /** 画像绑定的支付方式与事实精确相等（非支付方式画像两者都为空）。 */
    public boolean appliesTo(String paymentMethodCode) {
        return Objects.equals(profile.getPaymentMethodCode(), paymentMethodCode);
    }

    /**
     * 全部条件维度在事实中有值且命中其中一个条件值；未知或不适用的维度视为不命中。
     *
     * @param facts 定价事实
     * @return 是否命中
     */
    public boolean matches(PricingFacts facts) {
        for (PricingDimension dimension : dimensions()) {
            String value = facts.value(dimension);
            boolean hit = value != null && conditions.stream()
                .anyMatch(condition -> condition.getDimensionCode() == dimension && value.equals(condition.getConditionValue()));
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    /** 命中排序：具体度降序、priorityNo 升序。 */
    public static int compareForHit(CommercialPriceCandidate left, CommercialPriceCandidate right) {
        int bySpecificity = Integer.compare(right.specificity(), left.specificity());
        if (bySpecificity != 0) {
            return bySpecificity;
        }
        return Integer.compare(left.price.getPriorityNo(), right.price.getPriorityNo());
    }

    /**
     * 命中算法（technique.md §2.4）：候选已按版本、准确 OE 与币种精确筛选；再按支付方式精确 → 条件等值 → 不同维度数多者先 →
     * priorityNo 小者先选唯一价格
     * <li>支付方式候选使用的任一维度在事实中未知：PRICING_FACTS_INCOMPLETE，不借宽泛画像把未知当作不匹配。</li>
     * <li>没有命中：PRICE_NOT_FOUND；最优两条具体度与优先级都相同：PRICE_AMBIGUOUS（uk_term_price_priority 正常时不可达，失败关闭）。</li>
     *
     * @param candidates 同版本、同 OE、同币种的全部价格
     * @param facts 定价事实
     * @return 唯一命中
     */
    public static CommercialPriceCandidate selectHit(List<CommercialPriceCandidate> candidates, PricingFacts facts) {
        List<CommercialPriceCandidate> methodCandidates =
            candidates.stream().filter(candidate -> candidate.appliesTo(facts.paymentMethodCode())).toList();
        Set<PricingDimension> unknown = EnumSet.noneOf(PricingDimension.class);
        methodCandidates.forEach(candidate -> candidate.dimensions().stream().filter(dimension -> !facts.known(dimension))
            .forEach(unknown::add));
        if (!unknown.isEmpty()) {
            throw new FailException(CommercialPriceErrorCode.PRICING_FACTS_INCOMPLETE, "缺少定价事实: "
                + unknown.stream().map(PricingDimension::getCode).collect(Collectors.joining(",")));
        }
        List<CommercialPriceCandidate> hits = methodCandidates.stream().filter(candidate -> candidate.matches(facts))
            .sorted(CommercialPriceCandidate::compareForHit).toList();
        if (hits.isEmpty()) {
            throw new FailException(CommercialPriceErrorCode.PRICE_NOT_FOUND, "没有命中的价格: 币种 " + facts.currency()
                + ", 支付方式 " + facts.paymentMethodCode());
        }
        if (hits.size() > 1 && compareForHit(hits.get(0), hits.get(1)) == 0) {
            throw new FailException(CommercialPriceErrorCode.PRICE_AMBIGUOUS, "价格命中存在歧义: "
                + hits.get(0).price.getTermPriceId() + ", " + hits.get(1).price.getTermPriceId());
        }
        return hits.getFirst();
    }
}
