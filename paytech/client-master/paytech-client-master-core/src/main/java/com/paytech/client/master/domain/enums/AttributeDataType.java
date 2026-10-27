package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 扩展属性的取值类型。 */
public enum AttributeDataType implements CodeEnum {
    /** 文本。 */
    TEXT,

    /** 日期 YYYY-MM-DD。 */
    DATE,

    /** ISO 3166-1 alpha-2 国家代码。 */
    COUNTRY,

    /** 通用目录代码。 */
    CODE,

    /** 精确十进制数。 */
    DECIMAL;

    /** 稳定 Code，等于常量名。 */
    @Override
    public String getCode() {
        return name();
    }

    /**
     * 按 Code 解析
     *
     * @param code 待解析的 Code
     * @return 对应常量；不认识或为空时返回 null，由调用方按自身合同处理
     */
    public static AttributeDataType fromCode(String code) {
        for (AttributeDataType value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
