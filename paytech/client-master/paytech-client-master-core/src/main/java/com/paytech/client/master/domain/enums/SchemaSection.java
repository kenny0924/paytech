package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 采集 Schema 的前端分区。 */
public enum SchemaSection implements CodeEnum {
    /** 基本信息。 */
    BASIC,

    /** 经营信息。 */
    BUSINESS,

    /** 关联人员。 */
    PEOPLE,

    /** 材料。 */
    MATERIALS,

    /** 声明。 */
    DECLARATIONS;

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
    public static SchemaSection fromCode(String code) {
        for (SchemaSection value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
