package com.paytech.risk.control.domain.list;

import com.paytech.foundation.common.enums.CodeEnum;

/** 风险名单与累计事件可关联的受控主体类型。 */
public enum RiskSubjectType implements CodeEnum {
    /** Client 命名空间内的消费者引用，键为 clientId:consumerReference。 */
    CONSUMER("CONSUMER"),

    /** 消费者邮箱 Vault Token。 */
    EMAIL_TOKEN("EMAIL_TOKEN"),

    /** 消费者手机号 Vault Token。 */
    PHONE_TOKEN("PHONE_TOKEN"),

    /** 消费者 IP；IPv4 原值，IPv6 取 /64 前缀。 */
    IP_ADDRESS("IP_ADDRESS"),

    /** Client 标识。 */
    CLIENT("CLIENT"),

    /** Operating Entity 编号。 */
    OPERATING_ENTITY("OPERATING_ENTITY"),

    /** 跨交易稳定卡指纹。 */
    CARD_FINGERPRINT("CARD_FINGERPRINT"),

    /** 卡号前 6 至 8 位。 */
    CARD_BIN("CARD_BIN");

    private final String code;

    RiskSubjectType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
