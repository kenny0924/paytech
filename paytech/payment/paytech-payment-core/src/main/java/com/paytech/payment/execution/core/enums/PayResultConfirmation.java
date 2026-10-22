package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次“确认支付结果”的结论（3DS 全链路 technique 3.2），供查单恢复任务、刷新与渠道通知判断后续处理 */
@Getter
@AllArgsConstructor
public enum PayResultConfirmation implements CodeEnum {
    /** 当前动作已是终态或不是需要确认的 PAY（本次推进到终态，或此前已有终态） */
    CONFIRMED("CONFIRMED"),

    /** 渠道仍在处理或查单没有结论，动作保持 PROCESSING / UNKNOWN */
    PENDING("PENDING"),

    /** 渠道报告成功但金额或币种与动作不一致；不推进，转人工 */
    AMOUNT_MISMATCH("AMOUNT_MISMATCH");

    /** 稳定代码 */
    private final String code;
}
