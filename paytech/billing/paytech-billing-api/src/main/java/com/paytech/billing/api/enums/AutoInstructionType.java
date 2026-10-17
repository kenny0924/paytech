package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 交易自动记账指令类型；目标账簿由类型固定决定，不在规则行配置。 */
public enum AutoInstructionType implements CodeEnum {
    /** 支付成功：交易 OE 账簿 */
    PAYMENT_SUCCEEDED,

    /** 平台服务费 Partner 侧：交易 OE 账簿 */
    PLATFORM_SERVICE_CHARGE_PARTNER,

    /** 平台服务费平台侧：Platform Finance Entity 账簿 */
    PLATFORM_SERVICE_CHARGE_PLATFORM;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
