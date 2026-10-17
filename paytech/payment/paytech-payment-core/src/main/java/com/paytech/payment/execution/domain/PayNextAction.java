package com.paytech.payment.execution.domain;

import java.time.Instant;

import com.paytech.payment.execution.core.enums.InteractionType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 本次支付需要消费者完成的交互指令（如 3DS 跳转）
 * <li>只在本次 pay 响应中返回，不持久化；原提交重放不再返回。</li>
 */
@Getter
@AllArgsConstructor
public class PayNextAction {
    /** 交互类型 */
    private final InteractionType type;

    /** 跳转地址，临时有效 */
    private final String redirectUrl;

    /** 交互截止时间 */
    private final Instant expiresAt;

    @Override
    public String toString() {
        return "PayNextAction[" + type + ", redirectUrl redacted]";
    }
}
