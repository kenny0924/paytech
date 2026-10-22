package com.paytech.channel.connector.protocol.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标准 API 执行在第一个原子节点开始前的失败（原 Platform 内部接口第 3.2 节的 NOT_SENT 集合）
 * <li>出现这些代码保证没有发出任何渠道请求；调用方仍不得自动重放同一请求。</li>
 * <li>Code 与原 Platform Problem code 一致；problemStatus 为原 HTTP 状态，只用于沙箱验证步骤结果的记录口径保持不变。</li>
 */
@Getter
@AllArgsConstructor
public enum StandardApiNotSentCode implements CodeEnum {
    /** 标准 API 编码不存在。 */
    STANDARD_API_NOT_FOUND("STANDARD_API_NOT_FOUND", 404),

    /** 渠道不存在，或没有实现该标准 API。 */
    INTEGRATION_IMPLEMENTATION_NOT_FOUND("INTEGRATION_IMPLEMENTATION_NOT_FOUND", 404),

    /** 加载的流程或原子 API 配置未通过运行前校验。 */
    INTEGRATION_CONFIG_INVALID("INTEGRATION_CONFIG_INVALID", 500),

    /** 必需初始化项失败。 */
    INTEGRATION_INITIALIZATION_FAILED("INTEGRATION_INITIALIZATION_FAILED", 503),

    /** 第一个节点开始前流程预算已耗尽。 */
    INTEGRATION_BUDGET_EXHAUSTED("INTEGRATION_BUDGET_EXHAUSTED", 503),

    /** input 不满足标准 API 请求字段。 */
    VALIDATION_ERROR("VALIDATION_ERROR", 422),

    /** 渠道类型对应的执行舱没有空闲许可（不排队）。 */
    CHANNEL_EXECUTION_BUSY("CHANNEL_EXECUTION_BUSY", 503);

    /** 稳定代码，与原 Platform Problem code 一致。 */
    private final String code;

    /** 原内部接口返回的 HTTP 状态。 */
    private final int problemStatus;
}
