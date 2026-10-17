package com.paytech.payment.api.execution.response;

import lombok.Getter;
import lombok.Setter;

/** 支付提交结果 */
@Getter
@Setter
public class PayResponseBody {
    /**
     * 支付尝试 ID
     * <li>本次新建或幂等命中的尝试，使用正十进制 long 字符串。</li>
     */
    private String attemptId;

    /**
     * 支付尝试状态
     * <li>PROCESSING、SUCCEEDED、FAILED 或 CANCELLED。</li>
     * <li>PROCESSING 不表示已扣款，手动授权后未收款仍使用该状态。</li>
     */
    private String status;

    /**
     * 消费者交互指令
     * <li>仅本次新提交需要消费者完成交互（如 3DS）时返回，状态为 PROCESSING；其余为 null。</li>
     * <li>跳转地址只能临时使用，不得保存；原提交重放不再返回。</li>
     */
    private NextAction nextAction;

    /** 消费者交互指令 */
    @Getter
    @Setter
    public static class NextAction {
        /**
         * 交互类型
         * <li>THREE_DS_CHALLENGE：跳转完成 3DS 认证。</li>
         */
        private String type;

        /** 跳转地址 */
        private String redirectUrl;

        /** 交互截止时间，UTC RFC 3339 */
        private String expiresAt;

        @Override
        public String toString() {
            return "NextAction[" + type + ", redirectUrl redacted]";
        }
    }
}
