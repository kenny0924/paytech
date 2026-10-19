package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 可售产品模块代码（t_product.product_code 等）；创建后不可修改。 */
public enum ProductCode implements CodeEnum {
    /** 收单 */
    ACQUIRING("ACQUIRING"),

    /** 出款 */
    PAYOUT("PAYOUT"),

    /** 换汇 */
    FX("FX"),

    /** 争议处理 */
    DISPUTE("DISPUTE"),

    /** 税务与发票（基座产品） */
    TAX_INVOICE("TAX_INVOICE"),

    /** 数据分析 */
    DATA_ANALYTICS("DATA_ANALYTICS"),

    /** 平台服务（基座产品） */
    PLATFORM("PLATFORM");

    private final String code;

    ProductCode(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
