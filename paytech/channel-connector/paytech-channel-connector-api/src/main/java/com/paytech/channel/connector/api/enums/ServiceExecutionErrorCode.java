package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 服务渠道通用执行入口在发出外部请求前的失败（Problem code）；出现这些代码保证没有发出任何外部请求，调用方仍不得自动重放
 * <li>前五项沿用原 Platform 标准 API 执行合同的代码与 HTTP 状态（404、404、500、503、503）。</li>
 * <li>CHANNEL_CALL_NOT_GRANTED（403）让消费者区分“未授权”与配置问题；CHANNEL_EXECUTION_BUSY（503）表示服务渠道执行舱并发已满，不排队。</li>
 * <li>消费者：调用服务渠道的业务系统在 {@code HttpServiceClientFactory} 显式注册本枚举。</li>
 */
public enum ServiceExecutionErrorCode implements CodeMessageEnum {
    /** 标准 API 编码不存在。 */
    STANDARD_API_NOT_FOUND("STANDARD_API_NOT_FOUND", "标准 API 不存在"),

    /** 渠道没有实现该标准 API。 */
    INTEGRATION_IMPLEMENTATION_NOT_FOUND("INTEGRATION_IMPLEMENTATION_NOT_FOUND", "渠道没有实现该标准 API"),

    /** 加载的流程或原子 API 配置未通过运行前校验。 */
    INTEGRATION_CONFIG_INVALID("INTEGRATION_CONFIG_INVALID", "外部 API 配置未通过运行前校验"),

    /** 必需初始化项失败。 */
    INTEGRATION_INITIALIZATION_FAILED("INTEGRATION_INITIALIZATION_FAILED", "外部 API 初始化失败"),

    /** 第一个节点开始前流程预算已耗尽。 */
    INTEGRATION_BUDGET_EXHAUSTED("INTEGRATION_BUDGET_EXHAUSTED", "外部 API 流程预算已耗尽"),

    /** 服务渠道执行舱没有空闲许可。 */
    CHANNEL_EXECUTION_BUSY("CHANNEL_EXECUTION_BUSY", "渠道执行繁忙"),

    /** 调用方没有该渠道该标准 API 的 ACTIVE 调用授权。 */
    CHANNEL_CALL_NOT_GRANTED("CHANNEL_CALL_NOT_GRANTED", "调用方未获该渠道标准 API 的调用授权");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    ServiceExecutionErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 稳定错误 Code。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 可外显的默认消息。 */
    @Override
    public String getMessage() {
        return message;
    }
}
