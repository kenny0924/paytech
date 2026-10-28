package com.paytech.client.master.domain.enums;

import java.math.BigDecimal;

import com.paytech.foundation.common.enums.CodeEnum;

/** 持股比例与受益人门槛的比较方式（rule_code=UBO_THRESHOLD_COMPARISON 的取值）。 */
public enum UboThresholdComparison implements CodeEnum {
    /** 达到门槛即计入（25% 或以上）。 */
    GTE,

    /** 须超过门槛（超过 25%）。 */
    GT;

    /** 稳定 Code，等于常量名。 */
    @Override
    public String getCode() {
        return name();
    }

    /**
     * 持股比例是否满足门槛
     *
     * @param ownershipPercentage 持股百分比
     * @param threshold 门槛百分比
     * @return 按本比较方式满足时为 true
     */
    public boolean satisfiedBy(BigDecimal ownershipPercentage, BigDecimal threshold) {
        int compared = ownershipPercentage.compareTo(threshold);
        return this == GTE ? compared >= 0 : compared > 0;
    }

    /**
     * 按 Code 解析
     *
     * @param code 待解析的 Code
     * @return 对应常量；不认识或为空时返回 null，由调用方按自身合同处理
     */
    public static UboThresholdComparison fromCode(String code) {
        for (UboThresholdComparison value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
