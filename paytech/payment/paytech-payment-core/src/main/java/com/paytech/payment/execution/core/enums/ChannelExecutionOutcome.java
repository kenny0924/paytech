package com.paytech.payment.execution.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次渠道资金动作执行的结论；只有 NOT_SENT 保证渠道没有收到请求。 */
@Getter
@AllArgsConstructor
public enum ChannelExecutionOutcome implements CodeEnum {
    /** 渠道受理或给出可解释结果，业务状态见输出。 */
    SUCCESS("SUCCESS"),

    /** 渠道明确拒绝，未创建交易。 */
    FAIL("FAIL"),

    /** 结果无法确认。 */
    ERROR("ERROR"),

    /** 执行前失败，保证没有发出渠道请求。 */
    NOT_SENT("NOT_SENT");

    /** 业务比较使用的稳定代码。 */
    private final String code;
}
