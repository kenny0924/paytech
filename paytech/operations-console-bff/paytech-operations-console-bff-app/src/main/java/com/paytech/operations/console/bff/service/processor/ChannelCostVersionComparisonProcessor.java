package com.paytech.operations.console.bff.service.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.ChannelCostClient;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostComponentChange;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostComponentResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostScopeChange;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostScopeResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostVersionComparisonResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostVersionResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCostVersionSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelFieldDifference;
import com.paytech.operations.console.bff.service.processor.request.ChannelCostVersionComparisonRequest;

import lombok.AllArgsConstructor;

/**
 * 渠道成本版本对比：并发读取同一方案的两个版本详情，在 BFF 内对齐比较
 * <li>范围按 MID、OE、国家、币种、支付方式、动作类型六维对齐；同一版本内六维相同的范围按 scopeNo 顺序逐个配对。</li>
 * <li>成对范围比较 channelAgreementRef，组成按 componentCode 对齐后比较类型、计算基础、费率、金额、币种与舍入；十进制按数值比较。</li>
 * <li>两次读取与版本编辑页同样使用 CommercialConcurrentReads 并发执行，任一失败按 channel-connector 原错误整页失败，避免把缺失误报为删除。</li>
 * <li>只读组合，不编排任何写入。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class ChannelCostVersionComparisonProcessor
    extends AbstractProcessor<ChannelCostVersionComparisonRequest, ChannelCostVersionComparisonResponseBody> {
    /** 组成的文本比较字段。 */
    private static final Map<String, Function<ChannelCostComponentResponseBody, String>> COMPONENT_TEXT_FIELDS =
        orderedFields();

    private final ChannelCostClient costs;

    @Override
    protected ChannelCostVersionComparisonResponseBody processRequest(ChannelCostVersionComparisonRequest request) {
        List<ChannelCostVersionResponseBody> versions = CommercialConcurrentReads.all(List.of(
            () -> costs.version(request.getPlanCode(), request.getBaseCostVer()),
            () -> costs.version(request.getPlanCode(), request.getTargetCostVer())));
        return compare(versions.get(0), versions.get(1));
    }

    /** 两个版本的范围逐组配对比较。 */
    static ChannelCostVersionComparisonResponseBody compare(ChannelCostVersionResponseBody base,
        ChannelCostVersionResponseBody target) {
        var result = new ChannelCostVersionComparisonResponseBody();
        result.setBase(summary(base));
        result.setTarget(summary(target));
        result.setAdded(new ArrayList<>());
        result.setRemoved(new ArrayList<>());
        result.setChanged(new ArrayList<>());
        result.setUnchanged(new ArrayList<>());
        Map<String, List<ChannelCostScopeResponseBody>> remaining = byKey(sortedScopes(base.getScopes()),
            ChannelCostVersionComparisonProcessor::scopeKey);
        for (ChannelCostScopeResponseBody scope : sortedScopes(target.getScopes())) {
            ChannelCostScopeResponseBody paired = take(remaining, scopeKey(scope));
            classify(result, paired, scope);
        }
        remaining.values().forEach(result.getRemoved()::addAll);
        return result;
    }

    /** 一对范围归入新增、未变或修改。 */
    static void classify(ChannelCostVersionComparisonResponseBody result, ChannelCostScopeResponseBody base,
        ChannelCostScopeResponseBody target) {
        if (base == null) {
            result.getAdded().add(target);
            return;
        }
        ChannelCostScopeChange change = scopeChange(base, target);
        boolean unchanged = change.getDifferences().isEmpty() && change.getAddedComponents().isEmpty()
            && change.getRemovedComponents().isEmpty() && change.getChangedComponents().isEmpty();
        if (unchanged) {
            result.getUnchanged().add(target);
            return;
        }
        result.getChanged().add(change);
    }

    /** 成对范围的字段与组成差异。 */
    static ChannelCostScopeChange scopeChange(ChannelCostScopeResponseBody base, ChannelCostScopeResponseBody target) {
        var change = new ChannelCostScopeChange();
        change.setBase(base);
        change.setTarget(target);
        change.setDifferences(new ArrayList<>());
        textDifference(change.getDifferences(), "channelAgreementRef", base.getChannelAgreementRef(),
            target.getChannelAgreementRef());
        change.setAddedComponents(new ArrayList<>());
        change.setChangedComponents(new ArrayList<>());
        Map<String, List<ChannelCostComponentResponseBody>> remaining = byKey(components(base.getComponents()),
            ChannelCostComponentResponseBody::getComponentCode);
        for (ChannelCostComponentResponseBody component : components(target.getComponents())) {
            ChannelCostComponentResponseBody paired = take(remaining, component.getComponentCode());
            if (paired == null) {
                change.getAddedComponents().add(component);
                continue;
            }
            List<ChannelFieldDifference> differences = componentDifferences(paired, component);
            if (!differences.isEmpty()) {
                change.getChangedComponents().add(componentChange(paired, component, differences));
            }
        }
        change.setRemovedComponents(new ArrayList<>());
        remaining.values().forEach(change.getRemovedComponents()::addAll);
        return change;
    }

    /** 组成字段差异：文本字段逐个比较，费率与金额按数值比较。 */
    static List<ChannelFieldDifference> componentDifferences(ChannelCostComponentResponseBody base,
        ChannelCostComponentResponseBody target) {
        var differences = new ArrayList<ChannelFieldDifference>();
        COMPONENT_TEXT_FIELDS.forEach((field, read) -> {
            if (isDecimalField(field)) {
                decimalDifference(differences, field, read.apply(base), read.apply(target));
                return;
            }
            textDifference(differences, field, read.apply(base), read.apply(target));
        });
        return differences;
    }

    /** 文本字段不同则记录差异。 */
    static void textDifference(List<ChannelFieldDifference> differences, String field, String base, String target) {
        if (!Objects.equals(base, target)) {
            differences.add(difference(field, base, target));
        }
    }

    /** 十进制字段按数值比较（0.10 与 0.1 相同），一侧为空即不同。 */
    static void decimalDifference(List<ChannelFieldDifference> differences, String field, String base, String target) {
        if (base == null || target == null) {
            textDifference(differences, field, base, target);
            return;
        }
        if (new BigDecimal(base).compareTo(new BigDecimal(target)) != 0) {
            differences.add(difference(field, base, target));
        }
    }

    /** 一个字段差异。 */
    static ChannelFieldDifference difference(String field, String base, String target) {
        var difference = new ChannelFieldDifference();
        difference.setField(field);
        difference.setBaseValue(base);
        difference.setTargetValue(target);
        return difference;
    }

    /** 一个修改过的组成。 */
    static ChannelCostComponentChange componentChange(ChannelCostComponentResponseBody base,
        ChannelCostComponentResponseBody target, List<ChannelFieldDifference> differences) {
        var change = new ChannelCostComponentChange();
        change.setComponentCode(target.getComponentCode());
        change.setBase(base);
        change.setTarget(target);
        change.setDifferences(differences);
        return change;
    }

    /** 版本摘要（不含范围），范围与组成数按版本详情计数。 */
    static ChannelCostVersionSummaryResponseBody summary(ChannelCostVersionResponseBody version) {
        var summary = new ChannelCostVersionSummaryResponseBody();
        summary.setCostVerId(version.getCostVerId());
        summary.setScopeCount(version.getScopes().size());
        summary.setComponentCount(version.getScopes().stream().mapToInt(scope -> components(scope.getComponents()).size()).sum());
        summary.setCostVer(version.getCostVer());
        summary.setStatus(version.getStatus());
        summary.setEffectiveFrom(version.getEffectiveFrom());
        summary.setEffectiveTo(version.getEffectiveTo());
        summary.setContentHash(version.getContentHash());
        summary.setMakerId(version.getMakerId());
        summary.setCheckerId(version.getCheckerId());
        return summary;
    }

    /** 范围六维对齐键。 */
    static String scopeKey(ChannelCostScopeResponseBody scope) {
        return String.join("|", scope.getMidId(), scope.getEntityNo(), scope.getCountryCode(), scope.getCurrency(),
            scope.getPaymentMethodCode(), scope.getActionType());
    }

    /** 按键分组，组内保持 scopeNo（组成为 componentCode）顺序，供逐个配对。 */
    static <T> Map<String, List<T>> byKey(List<T> items, Function<T, String> key) {
        var groups = new LinkedHashMap<String, List<T>>();
        for (T item : items) {
            groups.computeIfAbsent(key.apply(item), ignored -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    /** 取出同键的下一个待配对项；没有则为 null。 */
    static <T> T take(Map<String, List<T>> groups, String key) {
        List<T> group = groups.get(key);
        if (group == null) {
            return null;
        }
        T item = group.removeFirst();
        if (group.isEmpty()) {
            groups.remove(key);
        }
        return item;
    }

    /** 对比版本的范围按 scopeNo 排列。 */
    static List<ChannelCostScopeResponseBody> sortedScopes(List<ChannelCostScopeResponseBody> scopes) {
        return scopes.stream().sorted(Comparator.comparing(ChannelCostScopeResponseBody::getScopeNo,
            Comparator.nullsLast(Integer::compareTo))).toList();
    }

    /** 组成集合；channel-connector 返回 null 时按空集合处理。 */
    static List<ChannelCostComponentResponseBody> components(List<ChannelCostComponentResponseBody> components) {
        return components == null ? List.of() : components;
    }

    /** 费率与金额字段按数值比较。 */
    static boolean isDecimalField(String field) {
        return switch (field) {
            case "rateValue", "fixedAmount", "minAmount", "maxAmount" -> true;
            default -> false;
        };
    }

    /** 组成比较字段，按页面展示顺序。 */
    private static Map<String, Function<ChannelCostComponentResponseBody, String>> orderedFields() {
        var fields = new LinkedHashMap<String, Function<ChannelCostComponentResponseBody, String>>();
        fields.put("componentType", ChannelCostComponentResponseBody::getComponentType);
        fields.put("calculationBase", ChannelCostComponentResponseBody::getCalculationBase);
        fields.put("rateValue", ChannelCostComponentResponseBody::getRateValue);
        fields.put("fixedAmount", ChannelCostComponentResponseBody::getFixedAmount);
        fields.put("currency", ChannelCostComponentResponseBody::getCurrency);
        fields.put("minAmount", ChannelCostComponentResponseBody::getMinAmount);
        fields.put("maxAmount", ChannelCostComponentResponseBody::getMaxAmount);
        fields.put("roundingMode", ChannelCostComponentResponseBody::getRoundingMode);
        return fields;
    }
}
