package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 产品允许被哪种合同业务模式订阅（t_product.business_mode_scope）。 */
public enum ProductBusinessModeScope implements CodeEnum {
    /** 只允许 MOR 合同订阅 */
    MOR_ONLY("MOR_ONLY"),

    /** 只允许 PSP 合同订阅 */
    PSP_ONLY("PSP_ONLY"),

    /** MOR 与 PSP 合同均可订阅 */
    BOTH("BOTH");

    private final String code;

    ProductBusinessModeScope(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
