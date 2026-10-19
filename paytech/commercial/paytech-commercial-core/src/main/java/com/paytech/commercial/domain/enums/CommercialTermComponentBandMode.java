package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** CommercialTermComponent BandMode 的数据库闭合值域；仅 TIERED 组件非空。 */
public enum CommercialTermComponentBandMode implements CodeEnum {
    /** 命中档位参数用于完整基数 */
    WHOLE_BASE("WHOLE_BASE"),

    /** 各档位区间分别计算后相加 */
    PROGRESSIVE("PROGRESSIVE"),

    /** 按周期累计命中档位回溯整期；只允许与 PERIOD_VOLUME 组合 */
    RETROACTIVE("RETROACTIVE");

    private final String code;

    CommercialTermComponentBandMode(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
