package com.paytech.channel.connector.domainservice.validator.channel;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.channel.connector.api.ChannelCapabilityCodes;
import com.paytech.channel.connector.core.configuration.CapabilityDefinition;
import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 能力组创建与整组替换共用的明细校验；只读入参，不访问数据库
 * <li>规则与候选查询的读取解析一致：代码已注册、值类型与目录一致且恰有一个值、BOOLEAN 为 0/1、CODE 在允许值内；
 * 同组内能力代码不重复；金额上下限为非负且下限不大于上限。写入时不合法的配置会在读取时被判定为配置损坏。</li>
 */
public final class MidCapabilityDetailValidator {

    /** 工具类不实例化。 */
    private MidCapabilityDetailValidator() {
    }

    /**
     * 校验同一能力组的全部明细
     *
     * @param details 待写入的明细，值类型已按目录填写
     * @param catalog 能力目录
     */
    public static void checkDetails(List<MidCapabilityDetail> details, Map<String, CapabilityDefinition> catalog) {
        Set<String> codes = new HashSet<>();
        for (MidCapabilityDetail detail : details) {
            if (!codes.add(detail.getCapabilityCode())) {
                throw new BizCheckFailException("能力代码重复: " + detail.getCapabilityCode());
            }
            checkValue(detail, catalog.get(detail.getCapabilityCode()));
        }
        checkAmountRange(details);
    }

    /** 单项明细与目录定义一致。 */
    static void checkValue(MidCapabilityDetail detail, CapabilityDefinition definition) {
        if (definition == null) {
            throw new BizCheckFailException("能力代码未注册: " + detail.getCapabilityCode());
        }
        int values = (detail.getBooleanValue() == null ? 0 : 1) + (detail.getDecimalValue() == null ? 0 : 1)
            + (detail.getCodeValue() == null ? 0 : 1);
        if (values != 1 || !valuePresentForType(detail)) {
            throw new BizCheckFailException("能力值与目录类型不一致: " + detail.getCapabilityCode() + ", 应为 "
                + definition.getValueType().getCode() + " 且恰好提供一个值");
        }
        if (detail.getCodeValue() != null && (definition.getAllowedCodes() == null
            || !definition.getAllowedCodes().contains(detail.getCodeValue()))) {
            throw new BizCheckFailException("能力代码值不在允许范围: " + detail.getCapabilityCode() + ", 值 "
                + detail.getCodeValue());
        }
    }

    /** 值类型对应的列必须有值。 */
    static boolean valuePresentForType(MidCapabilityDetail detail) {
        return switch (detail.getValueType()) {
            case BOOLEAN -> detail.getBooleanValue() != null;
            case DECIMAL -> detail.getDecimalValue() != null;
            case CODE -> detail.getCodeValue() != null;
        };
    }

    /** 金额上下限非负且顺序正确。 */
    static void checkAmountRange(List<MidCapabilityDetail> details) {
        MidCapabilityDetail minimum = find(details, ChannelCapabilityCodes.AMOUNT_MIN);
        MidCapabilityDetail maximum = find(details, ChannelCapabilityCodes.AMOUNT_MAX);
        for (MidCapabilityDetail amount : new MidCapabilityDetail[] {minimum, maximum}) {
            if (amount != null && amount.getDecimalValue().signum() < 0) {
                throw new BizCheckFailException("金额限制不能为负数: " + amount.getCapabilityCode());
            }
        }
        if (minimum != null && maximum != null && minimum.getDecimalValue().compareTo(maximum.getDecimalValue()) > 0) {
            throw new BizCheckFailException("最小金额不能大于最大金额: " + minimum.getDecimalValue().toPlainString() + " > "
                + maximum.getDecimalValue().toPlainString());
        }
    }

    /** 按能力代码查找明细，不存在返回空。 */
    static MidCapabilityDetail find(List<MidCapabilityDetail> details, String code) {
        return details.stream().filter(detail -> code.equals(detail.getCapabilityCode())).findFirst().orElse(null);
    }
}
