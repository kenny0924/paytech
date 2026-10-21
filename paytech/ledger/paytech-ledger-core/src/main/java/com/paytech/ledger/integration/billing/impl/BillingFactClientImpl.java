package com.paytech.ledger.integration.billing.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.BillingFactQueryApi;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;
import com.paytech.billing.api.response.BillingFactLineResponseBody;
import com.paytech.billing.api.response.BillingFactProgressResponseBody;
import com.paytech.billing.api.response.BillingFactRefResponseBody;
import com.paytech.billing.api.response.BillingFactResponseBody;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.BillingFactProgress;
import com.paytech.ledger.integration.billing.BillingFactClient;

import lombok.AllArgsConstructor;

/**
 * 只适配 billing-api 计费事实查询 DTO；HTTP 与错误解码交给 Foundation 公共客户端
 * <li>事实详情按字段直接转换为 billing 发布的 {@link BillingFactPayload}：头、外部引用与明细行（付方 / 收方）原样带出；
 * 定价侧表、行证据与行链接不进载荷。</li>
 */
@Component
@AllArgsConstructor
public class BillingFactClientImpl implements BillingFactClient {
    private final BillingFactQueryApi billingFactQueryApi;

    /** 三个计数字段必须存在。 */
    @Override
    public BillingFactProgress getProgress(LedgerBook book) {
        BillingFactProgressResponseBody body = billingFactQueryApi.getBillingFactProgress(
            book.getEntityType().getCode(), book.getEntityId(), book.getCurrency());
        if (body == null || body.getPendingCount() == null || body.getPublishedFactCount() == null) {
            throw new ErrorException("billing progress 响应无效: 账簿 " + book.getId());
        }
        return new BillingFactProgress(Long.parseLong(body.getPendingCount()),
            Long.parseLong(body.getPublishedFactCount()), toLong(body.getMaxPublishedFactId()));
    }

    /** 回显的主键必须一致；版本不一致说明事实尚未定稿或已被替代，按并发冲突等待重投。 */
    @Override
    public BillingFactPayload getFact(long factId, long factVersion) {
        BillingFactResponseBody body = billingFactQueryApi.getBillingFact(Long.toString(factId));
        if (body == null || !Long.toString(factId).equals(body.getFactId()) || body.getLines() == null) {
            throw new ErrorException("billing 计费事实响应无效: " + factId);
        }
        if (!Long.toString(factVersion).equals(body.getFactVersion())) {
            throw new ConcurrentRequestException("计费事实版本与入账批次不一致: " + factId + ", 批次版本 " + factVersion
                + ", billing 版本 " + body.getFactVersion());
        }
        return toPayload(body);
    }

    /** 事实头转换。 */
    BillingFactPayload toPayload(BillingFactResponseBody body) {
        BillingFactPayload payload = new BillingFactPayload();
        payload.setFactId(Long.valueOf(body.getFactId()));
        payload.setFactVersion(Long.valueOf(body.getFactVersion()));
        payload.setSupersedesFactId(toLong(body.getSupersedesFactId()));
        payload.setParentFactId(toLong(body.getParentFactId()));
        payload.setFactType(body.getFactType());
        payload.setOriginType(body.getOriginType());
        payload.setStatus(body.getStatus());
        payload.setSourceSystem(body.getSourceSystem());
        payload.setSourceEventKey(body.getSourceEventKey());
        payload.setSourceVersion(toLong(body.getSourceVersion()));
        payload.setSourcePayloadHash(body.getSourcePayloadHash());
        payload.setBusinessTime(body.getBusinessTime());
        payload.setRecordedTime(body.getRecordedTime());
        payload.setBalanceAvailableTime(body.getBalanceAvailableTime());
        payload.setClientId(toLong(body.getClientId()));
        payload.setEntityNo(body.getEntityNo());
        payload.setLocalPartnerId(toLong(body.getLocalPartnerId()));
        payload.setFinanceEntityId(toLong(body.getFinanceEntityId()));
        payload.setCurrency(body.getCurrency());
        payload.setGrossAmount(toDecimal(body.getGrossAmount()));
        payload.setRefs(body.getRefs() == null ? List.of() : body.getRefs().stream().map(this::toRef).toList());
        List<BillingFactLinePayload> lines = body.getLines().stream().map(this::toLine)
            .sorted(Comparator.comparing(BillingFactLinePayload::getLineNo, Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList();
        payload.setLines(lines);
        return payload;
    }

    /** 外部引用转换。 */
    BillingFactRefPayload toRef(BillingFactRefResponseBody body) {
        BillingFactRefPayload ref = new BillingFactRefPayload();
        ref.setRefType(body.getRefType());
        ref.setRefId(body.getRefId());
        ref.setExternalReferenceType(body.getExternalReferenceType());
        ref.setExternalTransactionId(body.getExternalTransactionId());
        return ref;
    }

    /** 明细行转换。 */
    BillingFactLinePayload toLine(BillingFactLineResponseBody body) {
        BillingFactLinePayload line = new BillingFactLinePayload();
        line.setLineNo(body.getLineNo() == null ? null : Integer.valueOf(body.getLineNo()));
        line.setComponentType(body.getComponentType());
        line.setPayerRole(body.getPayerRole());
        line.setPayerId(body.getPayerId());
        line.setPayeeRole(body.getPayeeRole());
        line.setPayeeId(body.getPayeeId());
        line.setDirection(body.getDirection());
        line.setPostedAmount(toDecimal(body.getPostedAmount()));
        line.setTargetBucket(body.getTargetBucket());
        return line;
    }

    /** 可空十进制字符串转 BigDecimal；缺失字段原样为空，由过账引擎的完整性断言拒绝。 */
    static BigDecimal toDecimal(String text) {
        return text == null ? null : new BigDecimal(text);
    }

    /** 可空十进制字符串转 Long。 */
    static Long toLong(String text) {
        return text == null ? null : Long.valueOf(text);
    }
}
