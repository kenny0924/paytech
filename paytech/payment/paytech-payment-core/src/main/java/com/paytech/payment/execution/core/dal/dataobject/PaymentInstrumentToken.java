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

    /**
     * 冻结的卡组织（BIN 库快照）
     * <li>card {@code CardBrand} Code；未识别或非卡工具为空。用于 Risk 输入、路由 schemeCode 与 PaymentSucceeded。</li>
     */
    private String cardBrand;

    /** 冻结的借贷记类型：CREDIT、DEBIT、PREPAID、CHARGE；未识别或号段未提供为空。 */
    private String fundingType;

    /** 冻结的卡等级（开放代码）；未识别或号段未提供为空。 */
    private String cardLevel;

    /** 冻结的卡产品类别：CONSUMER、COMMERCIAL；未识别或号段未提供为空。 */
    private String cardProduct;

    /** 冻结的发卡国 ISO 3166-1 alpha-2；未识别为空。 */
    private String issuerCountry;

    /** 冻结的发卡区域（Platform 国家区域映射 region_code）；未识别为空。 */
    private String issuerRegion;

    /**
     * 查询时的 BIN 库生效版本号
     * <li>没有生效版本或非卡工具为空；有版本但未命中时仍有值。</li>
     */
    private Integer binLibraryVersionNo;

    /** 命中号段的前缀位数（6、7、8）；未识别为空。 */
    private Integer binMatchLength;

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
