package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 产品目录与可开通项目录的生命周期状态（t_product.status、t_product_capability.status）。 */
public enum ProductStatus implements CodeEnum {
    /** 草稿，不可被合同引用 */
    DRAFT("DRAFT"),

    /** 可被新合同订阅或开通 */
    ACTIVE("ACTIVE"),

    /** 阻止新订阅或开通，历史引用继续可读 */
    RETIRED("RETIRED");

    private final String code;

    ProductStatus(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
