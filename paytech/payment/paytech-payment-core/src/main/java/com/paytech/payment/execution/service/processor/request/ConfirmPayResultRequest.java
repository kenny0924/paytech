package com.paytech.payment.execution.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 确认一个支付尝试当前 PAY 动作结果的请求；由查单恢复任务、刷新与渠道通知入口构造 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPayResultRequest {
    /** 支付意图 ID，决定资源锁 */
    @NotNull
    @Positive
    private Long intentId;

    /** 支付尝试 ID，必须属于该支付意图 */
    @NotNull
    @Positive
    private Long attemptId;
}
