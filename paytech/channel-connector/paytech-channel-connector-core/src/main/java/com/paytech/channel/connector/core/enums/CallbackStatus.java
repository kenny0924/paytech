package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道回调信封的处理状态（t_channel_callback.callback_status）。 */
@Getter
@AllArgsConstructor
public enum CallbackStatus implements CodeEnum {
    /** 已接收并入库，等待发布通知。 */
    RECEIVED("RECEIVED"),

    /** 处理中（本期不写入）。 */
    PROCESSING("PROCESSING"),

    /** 已处理：通知已发布并收到 Broker 确认，或无需发布。 */
    PROCESSED("PROCESSED"),

    /** 验签失败而拒绝。 */
    REJECTED("REJECTED"),

    /** 异常待处置（本期不写入）。 */
    EXCEPTION("EXCEPTION");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
