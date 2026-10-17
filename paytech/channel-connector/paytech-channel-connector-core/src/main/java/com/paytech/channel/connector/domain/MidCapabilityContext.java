package com.paytech.channel.connector.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paytech.channel.connector.api.ChannelCapabilityCodes;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.channel.connector.core.configuration.CapabilityDefinition;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.channel.connector.core.enums.CapabilityStatus;
import com.paytech.channel.connector.core.enums.CapabilityValueType;
import com.paytech.channel.connector.service.processor.request.MidCriteria;

import lombok.AllArgsConstructor;

/** 一致读取的能力主从对象；只执行完整条件匹配与逐代码解析，不访问外部依赖。 */
@AllArgsConstructor
public class MidCapabilityContext {
    /** 同一 MID 的能力条件组。 */
    private final List<MidCapability> scopes;

    /** 这些条件组的明细。 */
    private final List<MidCapabilityDetail> details;

    /** 逐能力代码选择最精确的唯一匹配；冲突、非法类型或金额区间均明确报错。 */
    public List<CapabilityView> resolve(MidCriteria criteria, Map<String, CapabilityDefinition> catalog) {
        scopes.forEach(this::validateScope);
        var matching = scopes.stream().filter(scope -> matches(scope, criteria))
            .collect(Collectors.toMap(MidCapability::getMidCapabilityId, Function.identity()));
        Map<String, List<MidCapabilityDetail>> byCode = new HashMap<>();
        for (var detail : details) {
            if (matching.containsKey(detail.getMidCapabilityId())) {
                validateValue(detail, catalog.get(detail.getCapabilityCode()));
                byCode.computeIfAbsent(detail.getCapabilityCode(), key -> new ArrayList<>()).add(detail);
            }
        }
        var values = byCode.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> resolveCode(entry.getValue(), matching)).toList();
        validateAmountRange(values);
        return values;
    }

    /** 拒绝尚未迁移的 MCC 通配符或非法目录格式，不把配置损坏当成没有候选。 */
    void validateScope(MidCapability scope) {
        if (!validCode(scope.getPaymentMethodCode(), "[A-Z0-9][A-Z0-9_.-]{0,63}")
            || !validCode(scope.getSchemeCode(), "\\*|[A-Z0-9][A-Z0-9_.-]{0,63}")
            || !validCode(scope.getCountryCode(), "\\*|[A-Z]{2}")
            || !validCode(scope.getCurrency(), "\\*|[A-Z]{3}")
            || !validCode(scope.getMccCode(), "[0-9]{4}")) {
            throw new ErrorException("MID 能力条件组包含非法交易维度");
        }
    }

    /** 校验数据库配置的必填代码格式。 */
    boolean validCode(String code, String pattern) {
        return code != null && code.matches(pattern);
    }

    /** 完整交易维度必须同时命中；不把不同配置行的 MCC 和币种组合。 */
    boolean matches(MidCapability scope, MidCriteria criteria) {
        return scope.getCapabilityStatus() == CapabilityStatus.ENABLED
            && Objects.equals(scope.getPaymentMethodCode(), criteria.getPaymentMethodCode())
            && Objects.equals(scope.getMccCode(), criteria.getMccCode())
            && matchesDimension(scope.getSchemeCode(), criteria.getSchemeCode())
            && matchesDimension(scope.getCountryCode(), criteria.getCountryCode())
            && matchesDimension(scope.getCurrency(), criteria.getCurrency());
    }

    /** 未提供维度时，仅通配规则可匹配。 */
    boolean matchesDimension(String configured, String requested) {
        return "*".equals(configured) || requested != null && requested.equals(configured);
    }

    /** 精确度只计算允许通配的三个维度，其余维度已经精确匹配。 */
    int specificity(MidCapability scope) {
        return ("*".equals(scope.getSchemeCode()) ? 0 : 1)
            + ("*".equals(scope.getCountryCode()) ? 0 : 1)
            + ("*".equals(scope.getCurrency()) ? 0 : 1);
    }

    /** 只在最高精确度决胜；更低层的歧义不会覆盖一个唯一的更精确配置。 */
    CapabilityView resolveCode(List<MidCapabilityDetail> candidates, Map<Long, MidCapability> matching) {
        var comparator = Comparator.comparingInt((MidCapabilityDetail detail) ->
            specificity(matching.get(detail.getMidCapabilityId())));
        var selected = candidates.stream().max(comparator).orElseThrow();
        long winners = candidates.stream().filter(detail -> comparator.compare(selected, detail) == 0).count();
        if (winners != 1) {
            throw new ErrorException("MID 能力存在同等精确度的配置冲突：" + selected.getCapabilityCode());
        }
        var result = new CapabilityView();
        result.setCapabilityCode(selected.getCapabilityCode());
        result.setValueType(selected.getValueType());
        result.setBooleanValue(selected.getBooleanValue() == null ? null : selected.getBooleanValue() == 1);
        result.setDecimalValue(selected.getDecimalValue());
        result.setCodeValue(selected.getCodeValue());
        return result;
    }

    /** 已注册的值类型及恰好一个值列共同约束能力，不把非法值转换为默认支持。 */
    void validateValue(MidCapabilityDetail detail, CapabilityDefinition definition) {
        int values = (detail.getBooleanValue() == null ? 0 : 1)
            + (detail.getDecimalValue() == null ? 0 : 1) + (detail.getCodeValue() == null ? 0 : 1);
        if (definition == null || detail.getValueType() == null
            || definition.getValueType() != detail.getValueType() || values != 1) {
            throw new ErrorException("MID 能力未注册或值类型不一致");
        }
        boolean valid = switch (detail.getValueType()) {
            case BOOLEAN -> Integer.valueOf(0).equals(detail.getBooleanValue())
                || Integer.valueOf(1).equals(detail.getBooleanValue());
            case DECIMAL -> detail.getDecimalValue() != null;
            case CODE -> detail.getCodeValue() != null && definition.getAllowedCodes() != null
                && definition.getAllowedCodes().contains(detail.getCodeValue());
        };
        if (!valid) {
            throw new ErrorException("MID 能力值不符合已注册类型或值域");
        }
    }

    /** 金额上下限只能非负且顺序正确，缺失限额不伪造为零。 */
    void validateAmountRange(List<CapabilityView> values) {
        var amounts = values.stream().filter(value -> ChannelCapabilityCodes.AMOUNT_MIN.equals(value.getCapabilityCode())
            || ChannelCapabilityCodes.AMOUNT_MAX.equals(value.getCapabilityCode())).collect(Collectors.toMap(
                CapabilityView::getCapabilityCode, Function.identity()));
        for (var value : amounts.values()) {
            if (value.getValueType() != CapabilityValueType.DECIMAL || value.getDecimalValue().signum() < 0) {
                throw new ErrorException("MID 金额限制必须是非负精确数值");
            }
        }
        if (amounts.containsKey(ChannelCapabilityCodes.AMOUNT_MIN) && amounts.containsKey(ChannelCapabilityCodes.AMOUNT_MAX)
            && amounts.get(ChannelCapabilityCodes.AMOUNT_MIN).getDecimalValue()
                .compareTo(amounts.get(ChannelCapabilityCodes.AMOUNT_MAX).getDecimalValue()) > 0) {
            throw new ErrorException("MID 最小金额不能大于最大金额");
        }
    }
}
