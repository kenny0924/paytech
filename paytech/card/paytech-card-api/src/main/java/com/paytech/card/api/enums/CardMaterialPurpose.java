package com.paytech.card.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 读卡票据用途的稳定代码；读卡入口只接受与票据相同的用途。 */
@Getter
@AllArgsConstructor
public enum CardMaterialPurpose implements CodeEnum {
    /** 渠道资金动作执行：Platform 执行支付类标准 API 时读卡一次。 */
    CHANNEL_EXECUTION("CHANNEL_EXECUTION");

    /** 稳定代码 */
    private final String code;
}
