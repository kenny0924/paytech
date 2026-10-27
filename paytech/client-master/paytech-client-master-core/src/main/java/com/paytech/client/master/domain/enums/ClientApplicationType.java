package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Client 申请类型。 */
public enum ClientApplicationType implements CodeEnum {
    /** 入驻。 */
    ONBOARDING,

    /** 批准后资料修订（后续启用）。 */
    PROFILE_AMENDMENT;

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
    public static ClientApplicationType fromCode(String code) {
        for (ClientApplicationType value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
