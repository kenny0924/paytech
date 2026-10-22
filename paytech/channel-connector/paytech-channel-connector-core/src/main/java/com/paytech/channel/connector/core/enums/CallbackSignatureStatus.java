package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道回调签名结论（t_channel_callback.signature_status）。 */
@Getter
@AllArgsConstructor
public enum CallbackSignatureStatus implements CodeEnum {
    /** 签名已按回调定义验证通过。 */
    VERIFIED("VERIFIED"),

    /** 签名缺失、格式错误或不匹配。 */
    REJECTED("REJECTED"),

    /** 无法确定（本期不写入）。 */
    UNKNOWN("UNKNOWN");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
