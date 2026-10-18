package com.paytech.account.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;

import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.api.response.EconomicFactLineResponseBody;
import com.paytech.billing.api.response.EconomicFactResponseBody;

/**
 * 经济事实（EconomicFactRecorded Payload）的合法模板：交易事实（PAYMENT，明细行无付方 / 收方）与人工调整（MANUAL_ADJUSTMENT，明细行带付方 / 收方）
 * <li>只构造对象，不访问数据库；事实主键由用例分配，行号按追加顺序从 1 起。</li>
 * <li>{@link #responseBody()} 把同一事实转换为 billing 列表接口的 DTO，供重建场景的 DTO Mock 使用。</li>
 */
public final class EconomicFactBuilder {
    private final EconomicFactPayload fact = new EconomicFactPayload();

    private EconomicFactBuilder() {
    }

    /** PAYMENT 交易事实：Client、OE、币种、总额与渠道清算账户；版本 1。 */
    public static EconomicFactBuilder payment(long factId, long clientId, String entityNo, String currency,
        String grossAmount, Long channelAccountId) {
        EconomicFactBuilder builder = base(factId, EconomicsType.PAYMENT, clientId, entityNo, currency);
        builder.fact.setGrossAmount(new BigDecimal(grossAmount));
        builder.fact.setChannelAccountId(channelAccountId);
        builder.fact.setPaymentSucceededTime(builder.fact.getRecordedTime());
        return builder;
    }

    /** MANUAL_ADJUSTMENT 人工调整事实；总额为明细行金额之和。 */
    public static EconomicFactBuilder adjustment(long factId, long clientId, String entityNo, String currency) {
        EconomicFactBuilder builder = base(factId, EconomicsType.MANUAL_ADJUSTMENT, clientId, entityNo, currency);
        builder.fact.setGrossAmount(BigDecimal.ZERO);
        return builder;
    }

    /** COST_ADJUSTMENT 成本调整事实：渠道清算账户必填。 */
    public static EconomicFactBuilder costAdjustment(long factId, long clientId, String entityNo, String currency,
        long channelAccountId) {
        EconomicFactBuilder builder = base(factId, EconomicsType.COST_ADJUSTMENT, clientId, entityNo, currency);
        builder.fact.setGrossAmount(BigDecimal.ZERO);
        builder.fact.setChannelAccountId(channelAccountId);
        return builder;
    }

    /** 公共头字段。 */
    static EconomicFactBuilder base(long factId, EconomicsType type, long clientId, String entityNo, String currency) {
        EconomicFactBuilder builder = new EconomicFactBuilder();
        EconomicFactPayload fact = builder.fact;
        fact.setFactId(factId);
        fact.setFactVersion(1L);
        fact.setFactType(type.getCode());
        fact.setSourceSystem("billing-test");
        fact.setSourceEventKey("fact-" + UUID.randomUUID());
        fact.setSourceVersion(1L);
        fact.setSourcePayloadHash((UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", ""));
        fact.setRecordedTime(Instant.now().truncatedTo(ChronoUnit.MICROS));
        fact.setClientId(clientId);
        fact.setEntityNo(entityNo);
        fact.setCurrency(currency);
        fact.setLines(new ArrayList<>());
        return builder;
    }

    /** 设置合作方三元组。 */
    public EconomicFactBuilder partner(long localPartnerId, long partnerAgreementOeId, long financeEntityId) {
        fact.setLocalPartnerId(localPartnerId);
        fact.setPartnerAgreementOeId(partnerAgreementOeId);
        fact.setFinanceEntityId(financeEntityId);
        return this;
    }

    /** 本事实替代前一事实：版本为被替代事实版本 + 1。 */
    public EconomicFactBuilder supersedes(long supersededFactId, long version) {
        fact.setSupersedesFactId(supersededFactId);
        fact.setFactVersion(version);
        return this;
    }

    /** 交易事实明细行：只有费用类型与金额（规则计算产生的行没有付方 / 收方）。 */
    public EconomicFactBuilder component(AmountSource feeType, String amount) {
        return line(feeType, null, null, null, null, null, amount);
    }

    /** 人工调整明细行：费用类型、付方、收方与方向。 */
    public EconomicFactBuilder line(AmountSource feeType, FactPartyRole payerRole, String payerId,
        FactPartyRole payeeRole, String payeeId, FactLineDirection direction, String amount) {
        EconomicFactLinePayload line = new EconomicFactLinePayload();
        line.setLineNo(fact.getLines().size() + 1);
        line.setComponentType(feeType.getCode());
        line.setPayerRole(payerRole == null ? null : payerRole.getCode());
        line.setPayerId(payerId);
        line.setPayeeRole(payeeRole == null ? null : payeeRole.getCode());
        line.setPayeeId(payeeId);
        line.setPartyType(payeeRole == null ? null : payeeRole.getCode());
        line.setPartyId(payeeId);
        line.setDirection(direction == null ? null : direction.getCode());
        line.setPostedAmount(new BigDecimal(amount));
        fact.getLines().add(line);
        if (!EconomicsType.PAYMENT.getCode().equals(fact.getFactType())) {
            fact.setGrossAmount(fact.getGrossAmount().add(new BigDecimal(amount)));
        }
        return this;
    }

    /** 返回构造好的载荷。 */
    public EconomicFactPayload build() {
        return fact;
    }

    /** 同一事实的 billing 列表 DTO（状态 APPROVED；被替代的事实由用例改为 SUPERSEDED）。 */
    public EconomicFactResponseBody responseBody() {
        return responseBody(fact, EconomicFactStatus.APPROVED);
    }

    /** 把载荷转换为 billing 列表 DTO。 */
    public static EconomicFactResponseBody responseBody(EconomicFactPayload fact, EconomicFactStatus status) {
        EconomicFactResponseBody body = new EconomicFactResponseBody();
        body.setFactId(fact.getFactId().toString());
        body.setFactType(fact.getFactType());
        body.setStatus(status.getCode());
        body.setFactVersion(fact.getFactVersion().toString());
        body.setSourceSystem(fact.getSourceSystem());
        body.setSourceReference(fact.getSourceEventKey());
        body.setContentHash(fact.getSourcePayloadHash());
        body.setSupersedesFactId(text(fact.getSupersedesFactId()));
        body.setClientId(text(fact.getClientId()));
        body.setEntityNo(fact.getEntityNo());
        body.setLocalPartnerId(text(fact.getLocalPartnerId()));
        body.setPartnerAgreementOeId(text(fact.getPartnerAgreementOeId()));
        body.setFinanceEntityId(text(fact.getFinanceEntityId()));
        body.setCurrency(fact.getCurrency());
        body.setGrossAmount(fact.getGrossAmount().toPlainString());
        body.setChannelAccountId(text(fact.getChannelAccountId()));
        body.setApprovedTime(fact.getRecordedTime());
        body.setLines(fact.getLines().stream().map(EconomicFactBuilder::lineBody).toList());
        return body;
    }

    /** 明细行 DTO。 */
    static EconomicFactLineResponseBody lineBody(EconomicFactLinePayload line) {
        EconomicFactLineResponseBody body = new EconomicFactLineResponseBody();
        body.setLineNo(line.getLineNo().toString());
        body.setFeeTypeCode(line.getComponentType());
        body.setPayerRole(line.getPayerRole());
        body.setPayerId(line.getPayerId());
        body.setPayeeRole(line.getPayeeRole());
        body.setPayeeId(line.getPayeeId());
        body.setAmount(line.getPostedAmount().toPlainString());
        body.setDirection(line.getDirection());
        return body;
    }

    /** 可空数值文本。 */
    static String text(Long value) {
        return value == null ? null : value.toString();
    }
}
