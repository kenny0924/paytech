package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 标准 API 执行在第一个原子节点开始前的失败（内部接口第 3.2 节）。
 *
 * <p>返回这些代码保证没有发出任何渠道请求；调用方仍不得自动重放同一请求。
 */
public enum StandardApiExecutionErrorCode implements CodeMessageEnum {

    /** 标准 API 编码不存在。 */
    STANDARD_API_NOT_FOUND("标准 API 不存在"),

    /** 接入方不存在，或没有实现该标准 API。 */
    INTEGRATION_IMPLEMENTATION_NOT_FOUND("接入方不存在或没有实现该标准 API"),

    /** 加载的流程或原子 API 配置未通过运行前校验。 */
    INTEGRATION_CONFIG_INVALID("外部 API 配置未通过运行前校验"),

    /** 必需初始化项失败。 */
    INTEGRATION_INITIALIZATION_FAILED("外部 API 初始化失败"),

    /** 第一个节点开始前流程预算已耗尽。 */
    INTEGRATION_BUDGET_EXHAUSTED("外部 API 流程预算已耗尽");

    private final String message;

    /** 绑定默认说明。 */
    StandardApiExecutionErrorCode(String message) {
        this.message = message;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return name();
    }

    /** 返回默认说明。 */
    @Override
    public String getMessage() {
        return message;
    }
}
