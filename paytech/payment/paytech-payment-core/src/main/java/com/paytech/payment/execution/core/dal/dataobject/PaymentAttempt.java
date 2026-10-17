package com.paytech.payment.execution.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import org.apache.commons.lang3.Strings;

import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.CaptureMode;

import lombok.Getter;
import lombok.Setter;

/** t_payment_attempt 的完整持久化映射；不通过 HTTP 暴露。 */
@Getter
@Setter
public class PaymentAttempt {
    /** 支付尝试标识。 */
    private Long paymentAttemptId;

    /** 支付意图标识。 */
    private Long paymentIntentId;

    /** 支付尝试序号。 */
    private Integer attemptNo;

    /** 原支付提交标识。 */
    private Long sourceSubmissionId;

    /** 支付方式代码。 */
    private String paymentMethodCode;

    /** 捕获模式。 */
    private CaptureMode captureMode;

    /** 交易工具绑定标识。 */
    private Long paymentInstrumentTokenId;

    /** 支付渠道 ID */
    private Long channelId;

    /** 渠道账号 ID */
    private Long channelAccountId;

    /** 渠道 MID */
    private Long midId;

    /** 支付尝试状态。 */
    private AttemptStatus attemptStatus;

    /** 活动动作标识。 */
    private Long activeActionId;

    /** 已授权金额。 */
    private BigDecimal authorizedAmount;

    /** 已捕获金额。 */
    private BigDecimal capturedAmount;

    /** 乐观锁版本。 */
    private Long version;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    public boolean samePayMethod(String paymentMethodCode, String captureMode) {
        return Strings.CS.equals(paymentMethodCode, this.paymentMethodCode)
            && Strings.CS.equals(captureMode, this.captureMode.getCode());
    }
}
