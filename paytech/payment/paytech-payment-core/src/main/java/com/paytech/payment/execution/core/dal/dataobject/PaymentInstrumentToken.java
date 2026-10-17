package com.paytech.payment.execution.core.dal.dataobject;

import java.time.Instant;

import com.paytech.payment.execution.core.enums.InstrumentType;

import lombok.Getter;
import lombok.Setter;

/** t_payment_instrument_token 的完整持久化映射；不通过 HTTP 暴露。 */
@Getter
@Setter
public class PaymentInstrumentToken {
    /** 交易工具绑定标识。 */
    private Long paymentInstrumentTokenId;

    /** 交易工具 Token。 */
    private String paymentInstrumentToken;

    /** Client 标识。 */
    private Long clientId;

    /** 支付意图标识。 */
    private Long paymentIntentId;

    /** 原支付提交标识。 */
    private Long sourceSubmissionId;

    /** 支付方式代码。 */
    private String paymentMethodCode;

    /** 工具类型。 */
    private InstrumentType instrumentType;

    /** 工具记录引用。 */
    private String instrumentRef;

    /** 业务到期时间。 */
    private Instant expiresAt;

    /** 撤销时间。 */
    private Instant revokedAt;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;
}
