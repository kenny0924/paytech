package com.paytech.acquiring.api.checkout.v1.payment.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 原支付提交的当前结果；本地受理不表示支付成功。 */
@Getter
@AllArgsConstructor
public class PaymentSubmissionResponseBody {
    /** 当前支付提交编号，用于后续查询；再次 POST 会生成新编号。 */
    private final String submissionId;
    /** 原提交访问期限，重试不延长。 */
    private final Instant expiresAt;
    /** Payment 支付尝试编号，尚未取得时为空。 */
    private final String attemptId;
    /** 当前支付状态 Code。 */
    private final String status;
    /** 可公开的卡摘要，尚未取得时为空。 */
    private final CardSummaryInfo card;
    /** 当前可执行的 3DS 动作，没有动作时为空。 */
    private final NextActionInfo nextAction;
    /** 受控失败信息，未失败或结果未知时为空。 */
    private final FailureInfo failure;
    /** Payment 观察时刻，未取得权威结果时为空。 */
    private final Instant observedAt;
    /** 建议轮询间隔，单位毫秒；无需轮询时为空。 */
    private final Integer pollAfterMs;

    /** 卡摘要只公开尾号。 */
    @Getter
    @AllArgsConstructor
    public static class CardSummaryInfo {
        /** 卡号最后四位。 */
        private final String last4;
    }

    /** 原 3DS 动作的临时指令，不写入 Commerce 快照。 */
    @Getter
    @AllArgsConstructor
    public static class NextActionInfo {
        /** Payment 原动作编号。 */
        private final String actionId;
        /** 动作类型 Code。 */
        private final String type;
        /** 动作到期时刻，上游未指定时为空。 */
        private final Instant expiresAt;
        /** 当前动作的 HTTPS 跳转地址。 */
        private final String redirectUrl;
    }

    /** 受控失败信息，不透传 Channel 原始原因。 */
    @Getter
    @AllArgsConstructor
    public static class FailureInfo {
        /** 受控失败 Code。 */
        private final String code;
        /** 可展示的安全提示。 */
        private final String message;
    }
}
