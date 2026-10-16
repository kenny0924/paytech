package com.paytech.finance.test.dal.row;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;

/** 测试夹具直接插入的 t_transaction_economics 行；字段与表列一一对应，枚举列保存稳定编码。 */
@Data
public class TransactionEconomicsRow {
    private Long id;
    private String economicsType;
    private String sourceSystem;
    private String sourceEventKey;
    private Long sourceVersion;
    private String sourcePayloadHash;
    private Long orderId;
    private Long orderContextId;
    private String contextFormat;
    private Instant commercialContextTime;
    private Long taxSnapshotId;
    private Long paymentIntentId;
    private Long paymentAttemptId;
    private Instant paymentSucceededTime;
    private Long clientId;
    private String entityNo;
    private Long localPartnerId;
    private String consumerCountryCode;
    private String paymentMethod;
    private String industryCode;
    private String mccCode;
    private Long clientAgreementId;
    private Long partnerAgreementOeId;
    private Long financeEntityId;
    private String currency;
    private BigDecimal grossAmount;
    private Instant createTime;
}
