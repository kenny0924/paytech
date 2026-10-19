package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 渠道成本命中中调用方需要按错误码区分处理的业务拒绝（HTTP 400）
 * <li>Owner 以 {@code new FailException(code, detail)} 抛出，Problem {@code code} 为枚举 Code，{@code detail} 含请求维度上下文。</li>
 * <li>消费者：billing 渠道成本计算在 {@code HttpServiceClientFactory} 显式注册本枚举；未命中与多重命中都不能当作零成本。</li>
 * <li>未列出的业务拒绝仍使用公共 BUSINESS_CHECK_FAILED。</li>
 */
public enum ChannelCostErrorCode implements CodeMessageEnum {
    /** Channel 无 ACTIVE 方案、无覆盖命中时间的版本或无精确命中的范围 */
    CHANNEL_COST_NOT_RESOLVED("CHANNEL_COST_NOT_RESOLVED", "未命中渠道成本配置"),

    /** 命中多于一个版本或范围，配置存在歧义 */
    CHANNEL_COST_AMBIGUOUS("CHANNEL_COST_AMBIGUOUS", "渠道成本配置命中不唯一");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    ChannelCostErrorCode(String code, String message) {
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
