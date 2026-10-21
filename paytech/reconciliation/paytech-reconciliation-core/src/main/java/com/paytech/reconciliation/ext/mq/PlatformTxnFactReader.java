package com.paytech.reconciliation.ext.mq;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.PlatformTxnSource;
import com.paytech.reconciliation.api.enums.PlatformTxnSourceRefType;
import com.paytech.reconciliation.domain.PlatformTxnFact;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * BillingFactRecorded Schema v1 信封到平台流水事实的解析（technique D-06、D-07）
 * <li>只做协议解析与派生，不访问数据库。信封聚合身份与 Payload 不一致、无法解析、缺必填头字段：抛 {@link IllegalArgumentException}（拒收进 DLQ）。</li>
 * <li>不是 PAYMENT / REFUND 或状态不是 APPROVED：返回 null（直接确认忽略）。带 supersedesFactId 的更正事实：抛
 * {@link IllegalArgumentException}（切片 1 拒收进 DLQ，人工处理）。</li>
 * <li>缺 CLEARING_ACCOUNT、CHANNEL_TRANSACTION_REF（含外部引用两列）或来源对象引用：抛 {@link ErrorException}（可重试，最终进 DLQ 告警，不静默丢弃，
 * AC-04）。</li>
 */
public final class PlatformTxnFactReader {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PlatformTxnFactReader() {
    }

    /**
     * 解析并派生平台流水事实
     *
     * @param event 已通过信封校验的事件
     * @return 平台流水事实；不需要对账的事实为 null
     */
    public static PlatformTxnFact read(DomainEvent event) {
        BillingFactPayload fact = payload(event);
        boolean payment = BillingFactType.PAYMENT.getCode().equals(fact.getFactType());
        boolean refund = BillingFactType.REFUND.getCode().equals(fact.getFactType());
        if ((!payment && !refund) || !BillingFactStatus.APPROVED.getCode().equals(fact.getStatus())) {
            return null;
        }
        if (fact.getSupersedesFactId() != null) {
            throw new IllegalArgumentException("切片 1 不接受更正关系的计费事实: " + event.eventId() + ", supersedesFactId="
                + fact.getSupersedesFactId());
        }
        return payment ? derive(fact, ClearingDirection.RECEIVABLE, PlatformTxnSource.PAYMENT, FactRefType.PAYMENT_ATTEMPT,
            PlatformTxnSourceRefType.PAYMENT_ATTEMPT)
            : derive(fact, ClearingDirection.PAYABLE, PlatformTxnSource.REFUND, FactRefType.CHANNEL_REFUND,
                PlatformTxnSourceRefType.CHANNEL_REFUND);
    }

    /** 核对信封并绑定 Payload。 */
    static BillingFactPayload payload(DomainEvent event) {
        if (event.schemaVersion() != BillingFactPayload.SCHEMA_VERSION
            || !BillingFactPayload.AGGREGATE_TYPE.equals(event.aggregateType())) {
            throw new IllegalArgumentException("不支持的 BillingFactRecorded 信封: " + event.eventId());
        }
        BillingFactPayload fact;
        try {
            fact = JSON.treeToValue(event.payload(), BillingFactPayload.class);
        } catch (JacksonException invalid) {
            throw new IllegalArgumentException("BillingFactRecorded Payload 无法解析: " + event.eventId(), invalid);
        }
        if (fact.getFactId() == null || !Objects.equals(String.valueOf(fact.getFactId()), event.aggregateId())
            || fact.getEntityNo() == null || fact.getCurrency() == null || fact.getGrossAmount() == null
            || fact.getBusinessTime() == null) {
            throw new IllegalArgumentException("BillingFactRecorded 信封与 Payload 不一致或缺必填字段: " + event.eventId());
        }
        return fact;
    }

    /** 派生平台流水事实：引用、金额与预估费用。 */
    static PlatformTxnFact derive(BillingFactPayload fact, ClearingDirection direction, PlatformTxnSource source,
        FactRefType sourceRefType, PlatformTxnSourceRefType platformSourceRefType) {
        BillingFactRefPayload clearing = requiredRef(fact, FactRefType.CLEARING_ACCOUNT);
        BillingFactRefPayload channelRef = requiredRef(fact, FactRefType.CHANNEL_TRANSACTION_REF);
        BillingFactRefPayload sourceRef = requiredRef(fact, sourceRefType);
        if (channelRef.getExternalReferenceType() == null || channelRef.getExternalTransactionId() == null) {
            throw new ErrorException("计费事实的渠道交易引用缺外部交易号，等待补齐后重试: 事实 " + fact.getFactId());
        }
        return PlatformTxnFact.builder().billingFactId(fact.getFactId()).clearingAccountId(clearingAccountId(fact, clearing))
            .direction(direction).txnSource(source).entityNo(fact.getEntityNo()).sourceRefType(platformSourceRefType)
            .sourceRefId(sourceRef.getRefId()).channelTransactionRefId(channelRef.getRefId())
            .referenceType(channelRef.getExternalReferenceType())
            .externalTransactionId(channelRef.getExternalTransactionId()).currency(fact.getCurrency())
            .expectedAmount(fact.getGrossAmount()).expectedFee(channelCost(fact.getLines()))
            .businessTime(fact.getBusinessTime()).build();
    }

    /** 必需引用；缺失为可重试失败。 */
    static BillingFactRefPayload requiredRef(BillingFactPayload fact, FactRefType refType) {
        List<BillingFactRefPayload> refs = fact.getRefs() == null ? List.of() : fact.getRefs();
        return refs.stream().filter(ref -> refType.getCode().equals(ref.getRefType()) && ref.getRefId() != null)
            .findFirst().orElseThrow(() -> new ErrorException("计费事实缺必需引用 " + refType.getCode()
                + "，等待补齐后重试: 事实 " + fact.getFactId()));
    }

    /** 清算账户引用的主键；格式非法拒收。 */
    static Long clearingAccountId(BillingFactPayload fact, BillingFactRefPayload clearing) {
        try {
            return Long.valueOf(clearing.getRefId());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("计费事实的清算账户引用不是主键: 事实 " + fact.getFactId(), invalid);
        }
    }

    /** CHANNEL_COST 组件行 postedAmount 合计；没有为 0（需求 Q-05）。 */
    static BigDecimal channelCost(List<BillingFactLinePayload> lines) {
        if (lines == null) {
            return BigDecimal.ZERO;
        }
        return lines.stream().filter(line -> FactComponentType.CHANNEL_COST.getCode().equals(line.getComponentType()))
            .map(BillingFactLinePayload::getPostedAmount).filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
