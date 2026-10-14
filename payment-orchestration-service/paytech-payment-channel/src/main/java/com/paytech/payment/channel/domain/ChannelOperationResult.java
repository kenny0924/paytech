package com.paytech.payment.channel.domain;

import java.util.Map;

import com.paytech.payment.channel.core.enums.ChannelOperationOutcome;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次渠道操作的结论与标准 API 输出；不含卡值或账号材料。 */
@Getter
@AllArgsConstructor
public class ChannelOperationResult {
    /** 执行结论。 */
    private final ChannelOperationOutcome outcome;

    /**
     * 标准 API 输出
     * <li>SUCCESS、FAIL 时为渠道整合流程的出口输出；其余为空映射。</li>
     * <li>可能含临时交互指令（如 3DS 跳转地址），调用方不得持久化。</li>
     */
    private final Map<String, Object> outputs;

    /** NOT_SENT 的原因代码（Platform Problem code 或本地校验），其余为 null。 */
    private final String failureCode;

    /** 构造执行前失败的结论。 */
    public static ChannelOperationResult notSent(String failureCode) {
        return new ChannelOperationResult(ChannelOperationOutcome.NOT_SENT, Map.of(), failureCode);
    }

    /** 构造结果未知的结论。 */
    public static ChannelOperationResult unknown() {
        return new ChannelOperationResult(ChannelOperationOutcome.ERROR, Map.of(), null);
    }
}
