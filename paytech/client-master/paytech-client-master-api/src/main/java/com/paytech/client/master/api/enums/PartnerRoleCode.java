package com.paytech.client.master.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Local Partner 可承担的商业角色。 */
public enum PartnerRoleCode implements CodeEnum {
    /** 向平台提供 Operating Entity（PARTNER 来源）。 */
    OE_PROVIDER("OE_PROVIDER"),

    /** 向平台推荐 Client。 */
    REFERRER("REFERRER");

    private final String code;

    /** 固定 Code。 */
    PartnerRoleCode(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 按 Code 解析
     *
     * @param code 待解析的 Code
     * @return 对应角色；不认识的 Code 返回 null，由调用方按自身合同拒绝
     */
    public static PartnerRoleCode fromCode(String code) {
        for (PartnerRoleCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
