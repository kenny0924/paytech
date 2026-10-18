package com.paytech.client.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Gateway 重建的 CLIENT 会话上下文类型；Code 即 X-Gateway-Context-Type 的合同值。 */
public enum ClientContextType implements CodeEnum {
    /** 账号上下文：尚未进入任何工作区。 */
    ACCOUNT("ACCOUNT"),

    /** 租户上下文：已进入某个工作区成员身份。 */
    TENANT("TENANT");

    private final String code;

    ClientContextType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }

    /**
     * 按 Code 解析上下文类型。
     *
     * @param code 请求头取值
     * @return 对应枚举；未知 Code 返回 null，由调用方按会话无效拒绝
     */
    public static ClientContextType fromCode(String code) {
        for (ClientContextType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
