package com.paytech.ledger.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;

import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.fact.EconomicFactPayload;

/**
 * 经济事实载荷的合法模板：PAYMENT、来源 payment、唯一事件键，头部必填字段给出稳定默认值
 * <li>作为 billing 事实详情 DTO 的来源（版本 1、批准时间为当前时刻），不写 billing 表；支付成功时间默认为一分钟前，落在
 * LedgerMocker 准备的当月 OPEN 期间内。</li>
 * <li>只构造对象，不访问数据库；事实主键由用例分配，明细行由 {@link EconomicComponentBuilder} 构造后按顺序编号。</li>
 */
public final class TransactionEconomicsBuilder {
    /** 经济事实的来源系统。 */
    public static final String PAYMENT_SOURCE_SYSTEM = "payment";

    private final EconomicFactPayload fact = new EconomicFactPayload();

    private TransactionEconomicsBuilder() {
    }

    /** 无 Partner 的 PAYMENT 模板；渠道清算账户主键由 billing 在头中给出。 */
    public static TransactionEconomicsBuilder template(String entityNo, long clientId, String currency,
        String grossAmount, long channelAccountId) {
        TransactionEconomicsBuilder builder = new TransactionEconomicsBuilder();
        EconomicFactPayload fact = builder.fact;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        fact.setFactVersion(1L);
        fact.setRecordedTime(now);
        fact.setFactType(EconomicsType.PAYMENT.getCode());
        fact.setSourceSystem(PAYMENT_SOURCE_SYSTEM);
        fact.setSourceEventKey("payment-succeeded-" + UUID.randomUUID());
        fact.setSourceVersion(1L);
        fact.setSourcePayloadHash(hex64());
        fact.setPaymentSucceededTime(now.minus(1, ChronoUnit.MINUTES));
        fact.setClientId(clientId);
        fact.setEntityNo(entityNo);
        fact.setCurrency(currency);
        fact.setGrossAmount(new BigDecimal(grossAmount));
        fact.setChannelAccountId(channelAccountId);
        fact.setLines(new ArrayList<>());
        return builder;
    }

    /** 设置完整 Partner 三元组：Local Partner、Partner Agreement OE 与平台服务费收取方 Finance Entity。 */
    public TransactionEconomicsBuilder partner(long localPartnerId, long partnerAgreementOeId, long financeEntityId) {
        fact.setLocalPartnerId(localPartnerId);
        fact.setPartnerAgreementOeId(partnerAgreementOeId);
        fact.setFinanceEntityId(financeEntityId);
        return this;
    }

    /** 单独覆盖 Local Partner。 */
    public TransactionEconomicsBuilder localPartnerId(Long localPartnerId) {
        fact.setLocalPartnerId(localPartnerId);
        return this;
    }

    /** 单独覆盖平台服务费 Finance Entity。 */
    public TransactionEconomicsBuilder financeEntityId(Long financeEntityId) {
        fact.setFinanceEntityId(financeEntityId);
        return this;
    }

    /** 覆盖渠道清算账户主键，可为 null（载荷缺失场景）。 */
    public TransactionEconomicsBuilder channelAccountId(Long channelAccountId) {
        fact.setChannelAccountId(channelAccountId);
        return this;
    }

    /** 覆盖支付成功时间，可为 null。 */
    public TransactionEconomicsBuilder paymentSucceededTime(Instant paymentSucceededTime) {
        fact.setPaymentSucceededTime(paymentSucceededTime);
        return this;
    }

    /** 替代给定事实：记录被替代事实主键，版本为其版本 + 1。 */
    public TransactionEconomicsBuilder supersedes(EconomicFactPayload previous) {
        fact.setSupersedesFactId(previous.getFactId());
        fact.setFactVersion(previous.getFactVersion() + 1);
        return this;
    }

    /** 覆盖经济事实类型。 */
    public TransactionEconomicsBuilder economicsType(EconomicsType economicsType) {
        fact.setFactType(economicsType.getCode());
        return this;
    }

    /** 分配事实主键并按入参顺序追加明细行（行号从 1 开始），返回构造好的载荷。 */
    public EconomicFactPayload build(long factId, EconomicComponentBuilder... components) {
        fact.setFactId(factId);
        int lineNo = fact.getLines().size() + 1;
        for (EconomicComponentBuilder component : components) {
            fact.getLines().add(component.build(lineNo++));
        }
        return fact;
    }

    /** 64 位小写十六进制占位摘要。 */
    static String hex64() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
