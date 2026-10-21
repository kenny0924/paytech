package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 合同价格组件与来源模板组件的比较结果（破标）。 */
public enum PriceBenchmarkStatus implements CodeEnum {
    /** 任一费率、固定额、最低收费、最高收费或档位低于模板值 */
    BELOW_STANDARD("BELOW_STANDARD"),

    /** 不低于模板值 */
    AT_OR_ABOVE("AT_OR_ABOVE"),

    /** 没有来源模板组件或档位结构不同，无标准可比 */
    NO_BENCHMARK("NO_BENCHMARK");

    private final String code;

    PriceBenchmarkStatus(String code) {
        this.code = code;
    }

    /** 接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
