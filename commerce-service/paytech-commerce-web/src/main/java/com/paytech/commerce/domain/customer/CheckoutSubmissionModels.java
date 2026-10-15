package com.paytech.commerce.domain.customer;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.commerce.customer.api.v1.enums.CustomerPaymentStatus;
import com.paytech.commerce.domain.checkout.CheckoutUpstreamProtocolException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Commerce 支付提交模型：瞬时卡输入、法律事实与结果查询坐标。 */
public final class CheckoutSubmissionModels {
    /** 禁止实例化。 */
    private CheckoutSubmissionModels() {}

    /** 当前卡的基础输入，与瞬时 CVV 分开持有。 */
    public record CardIdentity(@NotBlank @Pattern(regexp = "[0-9]{12,19}") String pan,
        @NotBlank @Pattern(regexp = "0[1-9]|1[0-2]") String expiryMonth,
        @NotBlank @Pattern(regexp = "[0-9]{4}") String expiryYear,
        @NotBlank @Size(max = 128) @Pattern(regexp = "[^\\p{Cc}]*") String cardholderName) {
        /** 避免异常或调试格式化输出卡身份。 */
        @Override
        public String toString() {
            return "CardIdentity[REDACTED]";
        }
    }

    /** 仅当前请求持有；不得交给持久化、补偿或通用日志。 */
    public record CardInput(@NotNull @Valid CardIdentity identity,
        @NotBlank @Pattern(regexp = "[0-9]{3,4}") String cvv) {
        /** CVV 和卡身份均不出现在字符串投影。 */
        @Override
        public String toString() {
            return "CardInput[REDACTED]";
        }
    }

    /**
     * 本次支付的卡来源：手输卡（可选择保存）或已存卡加本次 CVV；只在当前请求内存中传递
     *
     * @param card 手输卡；使用已存卡时为空
     * @param storedCardToken 已存卡 Token；手输卡时为空
     * @param storedCardCvv 使用已存卡时本次输入的 CVV
     * @param customerId 已核对的订单 Customer；访客或未选择保存时为空
     * @param saveCard 是否同意支付成功后保存手输卡
     */
    public record CardChoice(CardInput card, String storedCardToken, String storedCardCvv, Long customerId,
        boolean saveCard) {
        /** 不暴露卡输入、CVV 或已存卡 Token。 */
        @Override
        public String toString() {
            return "CardChoice[REDACTED]";
        }
    }

    /**
     * 渠道执行所需的付款人信息；不持久化
     *
     * @param consumerReference 不透明付款人引用：有 Customer 时为其消费者引用，访客为订单级引用
     * @param clientIp 可信连接的消费者 IP
     * @param userAgent 消费者 User-Agent
     * @param returnUrl 3DS 等交互后的回跳地址（本 Checkout 页面）
     */
    public record Payer(String consumerReference, String clientIp, String userAgent, String returnUrl) {
        /** 不暴露受限的网络原值与引用。 */
        @Override
        public String toString() {
            return "Payer[REDACTED]";
        }
    }

    /** 查询支付所需的最小归属；提交事实由 Payment 持有。 */
    public record PaymentReference(long submissionId, long orderId, long intentId) {
    }

    /** 当前提交携带的法律接受事实；不作为请求或响应快照持久化。 */
    public record SubmissionReceipt(CheckoutAcceptanceReceipt acceptance, Instant expiresAt) {
        /** 当前支付的来源提交编号。 */
        public long submissionId() {
            return Long.parseLong(acceptance.submissionId());
        }

        /** 下游查询只携带业务身份，不重复传递法律接受事实。 */
        public PaymentReference reference() {
            return new PaymentReference(submissionId(), Long.parseLong(acceptance.orderId()),
                Long.parseLong(acceptance.paymentIntentId()));
        }
    }

    /** 仅供当前支付使用的受限工具，不进入数据库、日志或公开响应。 */
    public record CardInstrument(String paymentMethodCode, String token, Instant expiresAt) {
        /** 工具必须仍有效且不能晚于原订单期限。 */
        public void checkUsable(Instant now, Instant orderExpiresAt) {
            if (!expiresAt.isAfter(now) || expiresAt.isAfter(orderExpiresAt)) {
                throw new CheckoutUpstreamProtocolException("Payment 卡工具期限超出有效范围");
            }
        }

        /** 避免调试输出工具 Token。 */
        @Override
        public String toString() {
            return "CardInstrument[REDACTED]";
        }
    }

    /** 通用支付只携带卡工具，原始卡输入不再传入后续支付步骤。 */
    public record PaymentRequest(SubmissionReceipt receipt, long orderVersion, long clientId, CardInstrument instrument,
        Payer payer) {
        /** 不暴露当前卡输入。 */
        @Override
        public String toString() {
            return "PaymentRequest[REDACTED]";
        }
    }

    /** Payment 返回的受限 3DS 动作；URL 仅用于当前响应，不进入本地快照。 */
    public record CustomerAction(String actionId, String type, Instant expiresAt, String redirectUrl) {
    }

    /** Payment 当前事实；null 查询结果只表示当时没有找到，不证明资金未发送。 */
    public record PaymentResult(long submissionId, long orderId, long intentId, String attemptId, String status,
        String last4, CustomerAction nextAction, String failureCode, Instant observedAt) {
        private static final Set<String> STATUSES = Arrays.stream(CustomerPaymentStatus.values())
            .map(CustomerPaymentStatus::getCode).collect(Collectors.toUnmodifiableSet());

        /** 校验当前结果归属和公开字段；外部返回值不能跨订单或伪造受控状态。 */
        public void validate(PaymentReference reference, Instant now) {
            if (submissionId != reference.submissionId() || orderId != reference.orderId()
                || intentId != reference.intentId() || status == null || !STATUSES.contains(status)
                || observedAt == null || (attemptId != null && !attemptId.matches("[1-9][0-9]{0,18}"))
                || (last4 != null && !last4.matches("[0-9]{4}"))
                || (failureCode != null && !failureCode.matches("[A-Z][A-Z0-9_]{0,63}"))) {
                throw new CheckoutUpstreamProtocolException("Payment 原提交结果无效");
            }
            if (nextAction != null) {
                validateAction(now);
            }
        }

        /** 3DS 指令仅允许原动作的未过期 HTTPS 地址，不由 Commerce 抓取或持久化。 */
        void validateAction(Instant now) {
            if (!CustomerPaymentStatus.REQUIRES_ACTION.getCode().equals(status)
                || !"THREE_DS_CHALLENGE".equals(nextAction.type()) || nextAction.actionId() == null
                || !nextAction.actionId().matches("[1-9][0-9]{0,18}") || nextAction.redirectUrl() == null
                || nextAction.redirectUrl().length() > 4096
                || (nextAction.expiresAt() != null && !nextAction.expiresAt().isAfter(now))) {
                throw new CheckoutUpstreamProtocolException("Payment 3DS 动作无效");
            }
            try {
                var uri = URI.create(nextAction.redirectUrl());
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException invalid) {
                throw new CheckoutUpstreamProtocolException("Payment 3DS 地址无效");
            }
        }
    }

}
