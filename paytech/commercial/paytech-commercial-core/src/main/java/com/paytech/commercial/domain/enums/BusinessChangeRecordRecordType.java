package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * BusinessChangeRecord RecordType 的数据库闭合值域。
 * <li>2026-10-19 六层定价 B 线增加 AGREEMENT_PRODUCT、AGREEMENT_CAPABILITY 两个 AGREEMENT 下的受审计 Child（database.md §3）；
 * 库表 record_type 注释白名单仍是拆分前 CCT 全集，待模型生成器收口（已知遗留）。</li>
 */
public enum BusinessChangeRecordRecordType implements CodeEnum {
    COMMERCIAL_TERM_VERSION("COMMERCIAL_TERM_VERSION"),

    AGREEMENT("AGREEMENT"),

    AGREEMENT_PRODUCT("AGREEMENT_PRODUCT"),

    AGREEMENT_CAPABILITY("AGREEMENT_CAPABILITY"),

    COMMERCIAL_TERM_PLAN("COMMERCIAL_TERM_PLAN"),

    PAYMENT_METHOD_PROFILE("PAYMENT_METHOD_PROFILE"),

    /** 产品目录 Root。 */
    PRODUCT_CATALOG("PRODUCT_CATALOG");

    private final String code;
    BusinessChangeRecordRecordType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
