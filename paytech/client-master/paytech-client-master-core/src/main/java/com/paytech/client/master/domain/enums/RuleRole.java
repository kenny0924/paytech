package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 关系类型在关系人规则校验中的角色。 */
public enum RuleRole implements CodeEnum {
    /** 股东。 */
    OWNER,

    /** 最终受益人。 */
    UBO,

    /** 控制人。 */
    CONTROL,

    /** 董事或经理。 */
    DIRECTOR,

    /** 经理。 */
    MANAGER,

    /** 授权签字人。 */
    SIGNATORY,

    /** 其他，不参与门槛计数。 */
    OTHER;

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
    public static RuleRole fromCode(String code) {
        for (RuleRole value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
