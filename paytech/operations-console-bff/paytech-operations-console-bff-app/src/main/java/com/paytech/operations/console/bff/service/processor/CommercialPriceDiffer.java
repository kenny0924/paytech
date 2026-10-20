package com.paytech.operations.console.bff.service.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paytech.operations.console.bff.service.model.CommercialChangeModels.CommercialTermVersionDiffResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialChangeModels.FieldDifference;
import com.paytech.operations.console.bff.service.model.CommercialChangeModels.PriceChange;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPrice;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPriceBand;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPriceComponent;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPriceSettlement;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;

/**
 * 同一方案两个版本的价格对比（纯计算）
 * <li>配对键为 commercial 价格业务唯一键 uk_term_price_profile 去掉版本 ID 后的 OE + 币种 + 定价画像代码。</li>
 * <li>字段级比较覆盖价格字段（支付方式、优先级、画像条件）、结算四字段、组件（按 componentCode 配对）与档位（按 bandNo 配对）；
 * 主键（priceId / componentId / bandId）与组件序号不参与比较。金额与费率按十进制数值比较，"0.0290" 与 "0.029" 视为相同。</li>
 *
 * @author Claude
 */
final class CommercialPriceDiffer {

    /** 只承载静态方法，禁止创建实例。 */
    private CommercialPriceDiffer() {
    }

    /**
     * 对比两个版本的价格全集
     *
     * @param base 基准版本
     * @param target 目标版本
     * @param basePrices 基准版本全部 OE 的价格
     * @param targetPrices 目标版本全部 OE 的价格
     * @return 版本口径差异与新增 / 删除 / 修改 / 未变四组价格
     */
    static CommercialTermVersionDiffResponseBody diff(CommercialTermVersionResponseBody base,
        CommercialTermVersionResponseBody target, List<CommercialPrice> basePrices, List<CommercialPrice> targetPrices) {
        Map<String, CommercialPrice> baseByKey = byKey(basePrices);
        Map<String, CommercialPrice> targetByKey = byKey(targetPrices);
        var result = new CommercialTermVersionDiffResponseBody();
        result.setBase(base);
        result.setTarget(target);
        result.setVersionDifferences(versionDifferences(base, target));
        result.setAdded(new ArrayList<>());
        result.setChanged(new ArrayList<>());
        result.setUnchanged(new ArrayList<>());
        targetByKey.forEach((key, price) -> classify(baseByKey.get(key), price, result));
        result.setRemoved(baseByKey.entrySet().stream().filter(entry -> !targetByKey.containsKey(entry.getKey()))
            .map(Map.Entry::getValue).toList());
        return result;
    }

    /** 目标价格归入新增、修改或未变。 */
    static void classify(CommercialPrice base, CommercialPrice target, CommercialTermVersionDiffResponseBody result) {
        if (base == null) {
            result.getAdded().add(target);
            return;
        }
        List<FieldDifference> differences = priceDifferences(base, target);
        if (differences.isEmpty()) {
            result.getUnchanged().add(target);
            return;
        }
        var change = new PriceChange();
        change.setEntityNo(target.getEntityNo());
        change.setCurrency(target.getCurrency());
        change.setPricingProfileCode(target.getPricingProfileCode());
        change.setBase(base);
        change.setTarget(target);
        change.setDifferences(differences);
        result.getChanged().add(change);
    }

    /** 价格业务键：OE + 币种 + 定价画像代码；保持入参顺序。 */
    static Map<String, CommercialPrice> byKey(List<CommercialPrice> prices) {
        var map = new LinkedHashMap<String, CommercialPrice>();
        for (CommercialPrice price : prices) {
            map.put(price.getEntityNo() + "|" + price.getCurrency() + "|" + price.getPricingProfileCode(), price);
        }
        return map;
    }

    /** 版本级口径：舍入方式、定价模型、费用税务处理。 */
    static List<FieldDifference> versionDifferences(CommercialTermVersionResponseBody base,
        CommercialTermVersionResponseBody target) {
        var differences = new ArrayList<FieldDifference>();
        text(differences, "roundingMode", base.getRoundingMode(), target.getRoundingMode());
        text(differences, "pricingModel", base.getPricingModel(), target.getPricingModel());
        text(differences, "feeTaxTreatment", base.getFeeTaxTreatment(), target.getFeeTaxTreatment());
        return differences;
    }

    /** 一条价格的全部字段级差异。 */
    static List<FieldDifference> priceDifferences(CommercialPrice base, CommercialPrice target) {
        var differences = new ArrayList<FieldDifference>();
        text(differences, "paymentMethodCode", base.getPaymentMethodCode(), target.getPaymentMethodCode());
        text(differences, "priorityNo", string(base.getPriorityNo()), string(target.getPriorityNo()));
        text(differences, "conditions", conditions(base), conditions(target));
        settlementDifferences(differences, base.getSettlement(), target.getSettlement());
        componentDifferences(differences, base.getComponents(), target.getComponents());
        return differences;
    }

    /** 结算四字段；Partner 条款两侧均为空时无差异。 */
    static void settlementDifferences(List<FieldDifference> differences, CommercialPriceSettlement base,
        CommercialPriceSettlement target) {
        text(differences, "settlement.settlementBusinessDays", settlement(base, CommercialPriceSettlement::getSettlementBusinessDays),
            settlement(target, CommercialPriceSettlement::getSettlementBusinessDays));
        text(differences, "settlement.businessCalendarCode", settlement(base, CommercialPriceSettlement::getBusinessCalendarCode),
            settlement(target, CommercialPriceSettlement::getBusinessCalendarCode));
        text(differences, "settlement.settlementTimezone", settlement(base, CommercialPriceSettlement::getSettlementTimezone),
            settlement(target, CommercialPriceSettlement::getSettlementTimezone));
        text(differences, "settlement.cutoffSecondOfDay", settlement(base, CommercialPriceSettlement::getCutoffSecondOfDay),
            settlement(target, CommercialPriceSettlement::getCutoffSecondOfDay));
    }

    /** 组件按 componentCode 配对：目标顺序在前，基准独有的组件随后。 */
    static void componentDifferences(List<FieldDifference> differences, List<CommercialPriceComponent> base,
        List<CommercialPriceComponent> target) {
        Map<String, CommercialPriceComponent> baseByCode = byCode(base, CommercialPriceComponent::getComponentCode);
        Map<String, CommercialPriceComponent> targetByCode = byCode(target, CommercialPriceComponent::getComponentCode);
        var codes = new LinkedHashSet<String>(targetByCode.keySet());
        codes.addAll(baseByCode.keySet());
        for (String code : codes) {
            component(differences, "components[" + code + "]", baseByCode.get(code), targetByCode.get(code));
        }
    }

    /** 一个组件：一侧缺失记为整体差异，否则逐字段比较。 */
    static void component(List<FieldDifference> differences, String path, CommercialPriceComponent base,
        CommercialPriceComponent target) {
        if (base == null || target == null) {
            differences.add(FieldDifference.of(path, componentSummary(base), componentSummary(target)));
            return;
        }
        text(differences, path + ".componentType", base.getComponentType(), target.getComponentType());
        text(differences, path + ".triggerCode", base.getTriggerCode(), target.getTriggerCode());
        text(differences, path + ".calculationBase", base.getCalculationBase(), target.getCalculationBase());
        text(differences, path + ".refundBehavior", base.getRefundBehavior(), target.getRefundBehavior());
        decimal(differences, path + ".rate", base.getRate(), target.getRate());
        decimal(differences, path + ".fixedAmount", base.getFixedAmount(), target.getFixedAmount());
        text(differences, path + ".currency", base.getCurrency(), target.getCurrency());
        decimal(differences, path + ".minAmount", base.getMinAmount(), target.getMinAmount());
        decimal(differences, path + ".maxAmount", base.getMaxAmount(), target.getMaxAmount());
        text(differences, path + ".bandBasis", base.getBandBasis(), target.getBandBasis());
        text(differences, path + ".bandMode", base.getBandMode(), target.getBandMode());
        bandDifferences(differences, path, base.getBands(), target.getBands());
    }

    /** 档位按 bandNo 配对。 */
    static void bandDifferences(List<FieldDifference> differences, String componentPath, List<CommercialPriceBand> base,
        List<CommercialPriceBand> target) {
        Map<String, CommercialPriceBand> baseByNo = byCode(base, band -> string(band.getBandNo()));
        Map<String, CommercialPriceBand> targetByNo = byCode(target, band -> string(band.getBandNo()));
        var numbers = new LinkedHashSet<String>(baseByNo.keySet());
        numbers.addAll(targetByNo.keySet());
        for (String bandNo : numbers.stream().sorted((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b))).toList()) {
            band(differences, componentPath + ".bands[" + bandNo + "]", baseByNo.get(bandNo), targetByNo.get(bandNo));
        }
    }

    /** 一个档位：一侧缺失记为整体差异，否则逐字段比较。 */
    static void band(List<FieldDifference> differences, String path, CommercialPriceBand base, CommercialPriceBand target) {
        if (base == null || target == null) {
            differences.add(FieldDifference.of(path, bandSummary(base), bandSummary(target)));
            return;
        }
        decimal(differences, path + ".lowerBound", base.getLowerBound(), target.getLowerBound());
        decimal(differences, path + ".upperBound", base.getUpperBound(), target.getUpperBound());
        decimal(differences, path + ".rate", base.getRate(), target.getRate());
        decimal(differences, path + ".fixedAmount", base.getFixedAmount(), target.getFixedAmount());
        text(differences, path + ".currency", base.getCurrency(), target.getCurrency());
    }

    /** 文本字段不同则记录。 */
    static void text(List<FieldDifference> differences, String field, String base, String target) {
        if (!Objects.equals(base, target)) {
            differences.add(FieldDifference.of(field, base, target));
        }
    }

    /** 十进制字段按数值比较，不同则记录原文。 */
    static void decimal(List<FieldDifference> differences, String field, String base, String target) {
        if (base != null && target != null && numericallyEqual(base, target)) {
            return;
        }
        text(differences, field, base, target);
    }

    /** 两个十进制字符串数值相等；不可解析时按文本比较。 */
    static boolean numericallyEqual(String base, String target) {
        try {
            return new BigDecimal(base).compareTo(new BigDecimal(target)) == 0;
        } catch (NumberFormatException notDecimal) {
            return base.equals(target);
        }
    }

    /** 画像条件的规范文本：DIM=VALUE 按字典序以逗号连接；无条件为 null。 */
    static String conditions(CommercialPrice price) {
        if (price.getConditions() == null || price.getConditions().isEmpty()) {
            return null;
        }
        return price.getConditions().stream().map(c -> c.getDimensionCode() + "=" + c.getConditionValue()).sorted()
            .collect(Collectors.joining(", "));
    }

    /** 组件的一句摘要，用于整体新增 / 删除。 */
    static String componentSummary(CommercialPriceComponent component) {
        return component == null ? null : component.getComponentType() + " · " + component.getTriggerCode();
    }

    /** 档位的一句摘要：[下界, 上界) 费率或固定额。 */
    static String bandSummary(CommercialPriceBand band) {
        if (band == null) {
            return null;
        }
        String upper = band.getUpperBound() == null ? "∞" : band.getUpperBound();
        String fee = band.getRate() != null ? "rate " + band.getRate() : "fixed " + band.getFixedAmount();
        return "[" + band.getLowerBound() + ", " + upper + ") " + fee;
    }

    /** 结算字段的文本值；无结算为 null。 */
    static <T> String settlement(CommercialPriceSettlement settlement, Function<CommercialPriceSettlement, T> field) {
        return settlement == null ? null : string(field.apply(settlement));
    }

    /** 可空值的文本。 */
    static String string(Object value) {
        return value == null ? null : value.toString();
    }

    /** 按键建有序映射；空集合视为没有元素。 */
    static <T> Map<String, T> byCode(List<T> items, Function<T, String> key) {
        var map = new LinkedHashMap<String, T>();
        if (items != null) {
            items.forEach(item -> map.put(key.apply(item), item));
        }
        return map;
    }
}
