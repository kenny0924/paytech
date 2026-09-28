package com.paytech.client.contract.domain;

import static com.paytech.client.contract.api.enums.CheckoutErrorCode.CCT_CONTEXT_AMBIGUOUS;
import static com.paytech.client.contract.domain.CheckoutResolutionContext.*;

import java.util.*;
import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.*;
import com.paytech.client.contract.domain.CheckoutCandidateContext.EntityEligibility;
import com.paytech.foundation.common.error.FailException;
import lombok.Value;
import lombok.experimental.Accessors;

/** 已验证的条款计划与版本；校验仅使用同一次本地读取的价格、组件和 Profile。 */
@Value
@Accessors(fluent = true)
public class CheckoutPricingContext {
    /** 原商业条款计划。 */
    CommercialTermPlan plan;
    /** 商业时点生效的原不可变版本。 */
    CommercialTermVersion version;

    static CheckoutPricingContext resolve(CheckoutResolutionContext context, Agreement agreement, EntityEligibility candidate, String type) {
        boolean client = type.equals("CLIENT_PRICING");
        CommercialTermPlan plan = unique(context.termPlans(), p -> code(p.termType(), type)
                && "ACTIVE".equals(p.status()) && (client
                        ? Objects.equals(p.agreementId(), agreement.agreementId())
                        : Objects.equals(p.localPartnerId(), agreement.localPartnerId())));
        if (plan == null) {
            return null;
        }
        require(client
                ? Objects.equals(plan.clientId(), context.query().clientId()) && plan.localPartnerId() == null
                        && Objects.equals(plan.entityNo(), candidate.entity().entityNo())
                : plan.clientId() == null && plan.agreementId() == null && plan.entityNo() == null);
        CommercialTermVersion version = unique(context.termVersions(),
                v -> v.termPlanId().equals(plan.termPlanId())
                        && (code(v.status(), "ACTIVE") || code(v.status(), "SUPERSEDED") || code(v.status(), "EXPIRED"))
                        && applicable(v.effectiveFrom(), v.effectiveTo(), context.query().asOf()));
        if (version == null) {
            return null;
        }
        require(version.agreementId().equals(agreement.agreementId()) && version.effectiveFrom() != null
                && !version.effectiveFrom().isBefore(agreement.effectiveFrom()));
        List<CommercialTermPrice> prices = context.termPrices().stream()
                .filter(p -> p.termVersionId().equals(version.termVersionId())
                        && p.entityNo().equals(candidate.entity().entityNo()))
                .toList();
        if (prices.isEmpty()) {
            return null;
        }
        require(prices.size() <= 1000);
        for (CommercialTermPrice price : prices) {
            validatePrice(context, price, client);
        }
        return new CheckoutPricingContext(plan, version);
    }

    private static void validatePrice(CheckoutResolutionContext context, CommercialTermPrice price, boolean client) {
        require(price.priorityNo() != null && price.priorityNo() > 0);
        PaymentMethodProfile profile = unique(context.methodProfiles(),
                p -> p.paymentMethodProfileCode().equals(price.paymentMethodProfileCode()));
        require(profile != null && !code(profile.status(), "DRAFT"));
        List<CommercialTermComponent> components = context.termComponents().stream()
                .filter(c -> c.termPriceId().equals(price.termPriceId())).toList();
        require(!components.isEmpty());
        Set<Integer> numbers = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (CommercialTermComponent component : components) {
            require(component.componentNo() != null && component.componentNo() > 0
                    && component.componentNo() <= components.size() && numbers.add(component.componentNo())
                    && component.componentCode() != null && !component.componentCode().isBlank()
                    && codes.add(component.componentCode()));
            validateComponent(context, component);
        }
        if (client) {
            require(price.settlementBusinessDays() != null && price.settlementBusinessDays() >= 0
                    && price.businessCalendarCode() != null && !price.businessCalendarCode().isBlank()
                    && price.settlementTimezone() != null && price.cutoffSecondOfDay() != null
                    && price.cutoffSecondOfDay() >= 0 && price.cutoffSecondOfDay() <= 86399);
            try {
                java.time.ZoneId.of(price.settlementTimezone());
            } catch (java.time.DateTimeException exception) {
                throw new FailException(CCT_CONTEXT_AMBIGUOUS);
            }
        } else {
            require(price.settlementBusinessDays() == null && price.businessCalendarCode() == null
                    && price.settlementTimezone() == null && price.cutoffSecondOfDay() == null);
        }
    }

    private static void validateComponent(CheckoutResolutionContext context, CommercialTermComponent c) {
        require(c.triggerCode() != null && !c.triggerCode().isBlank() && c.calculationBase() != null
                && !c.calculationBase().isBlank() && nonnegative(c.fixedAmount()) && nonnegative(c.minAmount())
                && nonnegative(c.maxAmount()) && rate(c.rate()));
        require(c.minAmount() == null || c.maxAmount() == null || c.minAmount().compareTo(c.maxAmount()) <= 0);
        if (!code(c.componentType(), "TIERED")) {
            require(context.termBands().stream()
                    .noneMatch(b -> b.termComponentId().equals(c.termComponentId())));
        }
        if (code(c.componentType(), "PERCENTAGE")) {
            require(c.rate() != null && c.fixedAmount() == null && c.bandMode() == null);
        } else if (code(c.componentType(), "FIXED")) {
            require(c.rate() == null && c.fixedAmount() != null && c.minAmount() == null && c.maxAmount() == null
                    && c.bandMode() == null);
        } else {
            require(code(c.componentType(), "TIERED") && c.rate() == null && c.fixedAmount() == null
                    && c.bandMode() != null);
            validateBands(context, c);
        }
        boolean money = c.fixedAmount() != null || c.minAmount() != null || c.maxAmount() != null
                || code(c.componentType(), "TIERED");
        require(money ? c.currency() != null && c.currency().matches("[A-Z]{3}") : c.currency() == null);
    }

    private static void validateBands(CheckoutResolutionContext context, CommercialTermComponent c) {
        List<CommercialTermComponentBand> bands = context.termBands().stream()
                .filter(b -> b.termComponentId().equals(c.termComponentId()))
                .sorted(Comparator.comparing(CommercialTermComponentBand::bandNo)).toList();
        require(!bands.isEmpty() && bands.size() <= 1000);
        java.math.BigDecimal lower = java.math.BigDecimal.ZERO;
        for (int i = 0; i < bands.size(); i++) {
            var band = bands.get(i);
            require(band.bandNo() == i + 1 && band.lowerBound() != null && lower != null
                    && lower.compareTo(band.lowerBound()) == 0
                    && (band.upperBound() == null ? i == bands.size() - 1 : band.upperBound().compareTo(lower) > 0)
                    && ((band.rate() != null) != (band.fixedAmount() != null)) && rate(band.rate())
                    && nonnegative(band.fixedAmount())
                    && (!code(c.bandMode(), "PROGRESSIVE") || band.rate() != null));
            lower = band.upperBound();
        }
        require(lower == null);
    }

    private static boolean nonnegative(java.math.BigDecimal value) {
        return value == null || value.signum() >= 0;
    }

    private static boolean rate(java.math.BigDecimal value) {
        return nonnegative(value) && (value == null || value.compareTo(java.math.BigDecimal.ONE) <= 0);
    }

}
