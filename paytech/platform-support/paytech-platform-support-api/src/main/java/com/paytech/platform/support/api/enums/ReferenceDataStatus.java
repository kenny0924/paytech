package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 参考数据目录条目状态（t_ref_currency、t_ref_country、t_ref_country_subdivision、t_ref_industry、t_ref_mcc 的 status）
 * <li>只有 ACTIVE 允许被新业务选择；已保存的业务数据不因停用失效。HTTP 字段使用 String。</li>
 * <li>Code 与 {@link CurrencySpecStatus} 一致；旧币种规则接口继续使用后者。</li>
 */
public enum ReferenceDataStatus implements CodeEnum {
    /** 草稿，未生效。 */
    DRAFT("DRAFT"),

    /** 允许新业务选择。 */
    ACTIVE("ACTIVE"),

    /** 已停用，不允许新业务选择。 */
    INACTIVE("INACTIVE");

    private final String code;

    ReferenceDataStatus(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
