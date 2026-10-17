package com.paytech.payment.channel.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 经 Platform 执行一次渠道操作的结论；只有 NOT_SENT 保证渠道没有收到请求。 */
@Getter
@AllArgsConstructor
public enum ChannelOperationOutcome implements CodeEnum {
    /** 渠道受理或返回了可解释的结果，业务状态见标准 API 输出。 */
    SUCCESS("SUCCESS"),

    /** 渠道明确拒绝，未创建交易。 */
    FAIL("FAIL"),

    /** 结果无法确认：超时、渠道系统错误、验签失败或 Platform 未知错误。 */
    ERROR("ERROR"),

    /** 执行前失败（冻结坐标不可用、配置无效、读卡或账号初始化失败），保证没有发出渠道请求。 */
    NOT_SENT("NOT_SENT");

    /** 业务比较使用的稳定代码。 */
    private final String code;
}
