package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 计费事实登记、批准与作废中调用方需要按错误码区分处理的业务拒绝（HTTP 400）
 * <li>Owner 以 {@code new FailException(code)} 抛出，Problem {@code code} 为枚举 Code、{@code detail} 为默认消息。</li>
 * <li>消费者：Operations BFF 在 {@code HttpServiceClientFactory} 显式注册本枚举并原样返回 400；Operations Web 按 Code 显示默认消息或落到
 * 对应表单字段。</li>
 * <li>未列出的业务拒绝仍使用公共 BUSINESS_CHECK_FAILED，参数错误使用公共 INVALID_REQUEST，并发冲突使用公共 CONCURRENT_REQUEST。</li>
 */
public enum BillingFactErrorCode implements CodeMessageEnum {
    /** 批准人与编制人为同一 Operations 账号 */
    SELF_REVIEW_FORBIDDEN("SELF_REVIEW_FORBIDDEN", "制单人不能复核自己提交的计费事实"),

    /**
     * 批准或作废的事实不是 DRAFT
     * <li>包括已作废事实的批准、已由其他复核人批准的事实，以及已批准 / 已替代事实的作废；同一复核人重复批准与重复作废仍返回原事实。</li>
     */
    FACT_NOT_DRAFT("FACT_NOT_DRAFT", "只能批准或作废草稿计费事实"),

    /**
     * 提交的明细行违反登记规则
     * <li>组件类型不被事实类型允许、付方 / 收方角色或标识与头部不符、付方等于收方、合作方组件缺少合作方或平台服务费收方不是
     * Finance Entity、目标余额桶用于非 Owner 收方、金额非正或超过币种精度。代码不在目录仍为 INVALID_REQUEST。</li>
     */
    FACT_LINE_INVALID("FACT_LINE_INVALID", "明细行不合法：金额组件、收付方、目标余额桶或金额精度不符合规则"),

    /**
     * 被替代事实不合格
     * <li>提交时不存在或不是同类型、同 Client、同 OE、同币种的 APPROVED 事实；批准时已不是 APPROVED。</li>
     */
    FACT_SUPERSEDE_INVALID("FACT_SUPERSEDE_INVALID", "被替代的计费事实不存在或不可替代");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    BillingFactErrorCode(String code, String message) {
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
