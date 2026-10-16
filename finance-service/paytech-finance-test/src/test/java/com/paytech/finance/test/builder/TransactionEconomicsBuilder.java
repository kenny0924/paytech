package com.paytech.finance.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.paytech.finance.accounting.core.enums.EconomicsType;
import com.paytech.finance.test.dal.row.TransactionEconomicsRow;

/**
 * 交易经济结果夹具的合法模板：PAYMENT、来源 payment、唯一事件键，全部 NOT NULL 列给出稳定默认值
 * <li>支付成功时间默认为一分钟前，落在 LedgerMocker 准备的当月 OPEN 期间内。</li>
 * <li>只构造对象，不访问数据库；主键由 Mocker 分配。</li>
 */
public final class TransactionEconomicsBuilder {
    /** 交易经济结果的来源系统。 */
    public static final String PAYMENT_SOURCE_SYSTEM = "payment";

    private final TransactionEconomicsRow row = new TransactionEconomicsRow();

    private TransactionEconomicsBuilder() {
    }

    /** 无 Partner 的 PAYMENT 模板。 */
    public static TransactionEconomicsBuilder template(String entityNo, long clientId, String currency,
        String grossAmount) {
        TransactionEconomicsBuilder builder = new TransactionEconomicsBuilder();
        TransactionEconomicsRow row = builder.row;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        row.setEconomicsType(EconomicsType.PAYMENT.getCode());
        row.setSourceSystem(PAYMENT_SOURCE_SYSTEM);
        row.setSourceEventKey("payment-succeeded-" + UUID.randomUUID());
        row.setSourceVersion(1L);
        row.setSourcePayloadHash(hex64());
        row.setOrderId(910001L);
        row.setOrderContextId(920001L);
        row.setContextFormat("TERM_PRICE");
        row.setCommercialContextTime(now.minus(10, ChronoUnit.MINUTES));
        row.setTaxSnapshotId(930001L);
        row.setPaymentIntentId(940001L);
        row.setPaymentAttemptId(950001L);
        row.setPaymentSucceededTime(now.minus(1, ChronoUnit.MINUTES));
        row.setClientId(clientId);
        row.setEntityNo(entityNo);
        row.setConsumerCountryCode("AE");
        row.setPaymentMethod("CARD");
        row.setIndustryCode("DIGITAL_GOODS");
        row.setMccCode("5816");
        row.setClientAgreementId(960001L);
        row.setCurrency(currency);
        row.setGrossAmount(new BigDecimal(grossAmount));
        row.setCreateTime(now);
        return builder;
    }

    /** 设置完整 Partner 三元组：Local Partner、Partner Agreement OE 与平台服务费收取方 Finance Entity。 */
    public TransactionEconomicsBuilder partner(long localPartnerId, long partnerAgreementOeId, long financeEntityId) {
        row.setLocalPartnerId(localPartnerId);
        row.setPartnerAgreementOeId(partnerAgreementOeId);
        row.setFinanceEntityId(financeEntityId);
        return this;
    }

    /** 单独覆盖 Local Partner，用于构造三元组不完整场景。 */
    public TransactionEconomicsBuilder localPartnerId(Long localPartnerId) {
        row.setLocalPartnerId(localPartnerId);
        return this;
    }

    /** 单独覆盖 Partner Agreement OE。 */
    public TransactionEconomicsBuilder partnerAgreementOeId(Long partnerAgreementOeId) {
        row.setPartnerAgreementOeId(partnerAgreementOeId);
        return this;
    }

    /** 单独覆盖平台服务费 Finance Entity。 */
    public TransactionEconomicsBuilder financeEntityId(Long financeEntityId) {
        row.setFinanceEntityId(financeEntityId);
        return this;
    }

    /** 覆盖支付成功时间，可为 null。 */
    public TransactionEconomicsBuilder paymentSucceededTime(Instant paymentSucceededTime) {
        row.setPaymentSucceededTime(paymentSucceededTime);
        return this;
    }

    /** 覆盖经济结果类型。 */
    public TransactionEconomicsBuilder economicsType(EconomicsType economicsType) {
        row.setEconomicsType(economicsType.getCode());
        return this;
    }

    /** 返回构造好的夹具行（未分配主键）。 */
    public TransactionEconomicsRow build() {
        return row;
    }

    /** 64 位小写十六进制占位摘要。 */
    static String hex64() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
