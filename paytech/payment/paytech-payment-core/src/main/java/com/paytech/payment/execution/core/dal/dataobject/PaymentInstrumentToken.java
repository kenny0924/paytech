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

    /** 工具记录引用（卡工具为 Card instrumentRef）。 */
    private String instrumentRef;

    /** 本次支付关联的 Commerce Customer；访客支付为空。 */
    private Long customerId;

    /** 消费者是否同意支付成功后保存卡。 */
    private boolean consentToSave;

    /** Card 返回的卡 BIN，只用作风控输入。 */
    private String cardBin;

    /** Card 返回的卡指纹（受限值），只经内部同步请求传给 Risk。 */
    private String cardFingerprint;

    /** 业务到期时间。 */
    private Instant expiresAt;

    /** 撤销时间。 */
    private Instant revokedAt;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    /** 不输出卡指纹等受限值。 */
    @Override
    public String toString() {
        return "PaymentInstrumentToken[" + paymentInstrumentTokenId + "]";
    }
}
