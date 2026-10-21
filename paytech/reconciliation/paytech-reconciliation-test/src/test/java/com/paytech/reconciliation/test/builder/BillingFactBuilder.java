package com.paytech.reconciliation.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * BillingFactRecorded Payload 的合法模板（technique D-06）：PAYMENT / REFUND 事实带 CLEARING_ACCOUNT 引用、CHANNEL_TRANSACTION_REF 引用
 * （refId + externalReferenceType + externalTransactionId）与来源对象引用（PAYMENT_ATTEMPT / CHANNEL_REFUND）
 * <li>只构造对象，不访问外部资源；事实主键由测试序列分配，版本 1、APPROVED、AUTO，业务时间取当前时间。</li>
 * <li>预估渠道费用 {@link #channelCost} 追加 CHANNEL_COST 行；负例可用 {@link #withoutRef} 去掉必需引用，或 {@link #supersedes} 构造更正事实。</li>
 */
public final class BillingFactBuilder {
    /** 测试 Client ID。 */
    public static final long CLIENT_ID = 81_000_001L;

    /** 测试渠道参与方标识。 */
    public static final String CHANNEL_PARTY_ID = ChannelMidBuilder.CHANNEL_ID;

    private final BillingFactPayload fact = new BillingFactPayload();

    private BillingFactBuilder() {
    }

    /** PAYMENT 事实：清算账户、外部交易号（PAY 类型）与毛额。 */
    public static BillingFactBuilder payment(long clearingAccountId, String externalTransactionId, String grossAmount) {
        return base(BillingFactType.PAYMENT, grossAmount)
            .ref(FactRefType.CLEARING_ACCOUNT, String.valueOf(clearingAccountId), null, null)
            .ref(FactRefType.CHANNEL_TRANSACTION_REF, String.valueOf(ReconciliationTestConstants.nextId()),
                ReconciliationTestConstants.PAY_REFERENCE_TYPE, externalTransactionId)
            .ref(FactRefType.PAYMENT_ATTEMPT, String.valueOf(ReconciliationTestConstants.nextId()), null, null);
    }

    /** REFUND 事实：清算账户、外部交易号（REFUND 类型）与退款额。 */
    public static BillingFactBuilder refund(long clearingAccountId, String externalTransactionId, String grossAmount) {
        return base(BillingFactType.REFUND, grossAmount)
            .ref(FactRefType.CLEARING_ACCOUNT, String.valueOf(clearingAccountId), null, null)
            .ref(FactRefType.CHANNEL_TRANSACTION_REF, String.valueOf(ReconciliationTestConstants.nextId()),
                ReconciliationTestConstants.REFUND_REFERENCE_TYPE, externalTransactionId)
            .ref(FactRefType.CHANNEL_REFUND, String.valueOf(ReconciliationTestConstants.nextId()), null, null);
    }

    /** 其他类型的事实（对账应忽略），只带毛额。 */
    public static BillingFactBuilder of(BillingFactType type, String grossAmount) {
        return base(type, grossAmount);
    }

    /** 公共头字段。 */
    static BillingFactBuilder base(BillingFactType type, String grossAmount) {
        BillingFactBuilder builder = new BillingFactBuilder();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        BillingFactPayload fact = builder.fact;
        fact.setFactId(ReconciliationTestConstants.nextId());
        fact.setFactVersion(1L);
        fact.setFactType(type.getCode());
        fact.setOriginType(FactOriginType.AUTO.getCode());
        fact.setStatus(BillingFactStatus.APPROVED.getCode());
        fact.setSourceSystem("payment");
        fact.setSourceEventKey("fact-" + UUID.randomUUID());
        fact.setSourceVersion(1L);
        fact.setSourcePayloadHash((UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", ""));
        fact.setBusinessTime(now);
        fact.setRecordedTime(now);
        fact.setClientId(CLIENT_ID);
        fact.setEntityNo(ReconciliationTestConstants.ENTITY_NO);
        fact.setCurrency(ReconciliationTestConstants.CURRENCY);
        fact.setGrossAmount(new BigDecimal(grossAmount));
        fact.setRefs(new ArrayList<>());
        fact.setLines(new ArrayList<>());
        return builder;
    }

    /** 追加一条引用；外部引用两列只在 CHANNEL_TRANSACTION_REF 上非空。 */
    public BillingFactBuilder ref(FactRefType refType, String refId, String externalReferenceType,
        String externalTransactionId) {
        BillingFactRefPayload ref = new BillingFactRefPayload();
        ref.setRefType(refType.getCode());
        ref.setRefId(refId);
        ref.setExternalReferenceType(externalReferenceType);
        ref.setExternalTransactionId(externalTransactionId);
        fact.getRefs().add(ref);
        return this;
    }

    /** 去掉某类引用（缺必需引用的负例，AC-04）。 */
    public BillingFactBuilder withoutRef(FactRefType refType) {
        fact.getRefs().removeIf(ref -> refType.getCode().equals(ref.getRefType()));
        return this;
    }

    /** 追加一条 CHANNEL_COST 预估渠道费用行（OE 付给渠道）。 */
    public BillingFactBuilder channelCost(String amount) {
        BillingFactLinePayload line = new BillingFactLinePayload();
        line.setLineNo(fact.getLines().size() + 1);
        line.setComponentType(FactComponentType.CHANNEL_COST.getCode());
        line.setPayerRole(FactPartyRole.OPERATING_ENTITY.getCode());
        line.setPayerId(fact.getEntityNo());
        line.setPayeeRole(FactPartyRole.CHANNEL.getCode());
        line.setPayeeId(CHANNEL_PARTY_ID);
        line.setDirection(FactLineDirection.CHARGE.getCode());
        line.setPostedAmount(new BigDecimal(amount));
        fact.getLines().add(line);
        return this;
    }

    /** 本事实替代前一事实（D-07：切片 1 拒收更正事实）。 */
    public BillingFactBuilder supersedes(long supersededFactId) {
        fact.setSupersedesFactId(supersededFactId);
        fact.setFactVersion(2L);
        return this;
    }

    /** 返回构造结果。 */
    public BillingFactPayload build() {
        return fact;
    }
}
