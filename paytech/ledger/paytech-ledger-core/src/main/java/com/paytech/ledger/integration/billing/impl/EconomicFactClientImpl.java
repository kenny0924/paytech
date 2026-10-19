package com.paytech.ledger.integration.billing.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.EconomicFactCommandApi;
import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.api.response.EconomicFactLineResponseBody;
import com.paytech.billing.api.response.EconomicFactResponseBody;
import com.paytech.billing.api.response.PendingEconomicFactsResponseBody;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.EconomicFactProgress;
import com.paytech.ledger.integration.billing.EconomicFactClient;

import lombok.AllArgsConstructor;

/**
 * 只适配 billing-api 经济事实 DTO；HTTP 与错误解码交给 Foundation 公共客户端
 * <li>事实详情转换为 billing 发布的 {@link EconomicFactPayload} 字段语义：登记时间取批准时间，明细行的 partyType / partyId 取收方
 * （billing 登记时即以收方作为明细行主体）。</li>
 */
@Component
@AllArgsConstructor
public class EconomicFactClientImpl implements EconomicFactClient {
    private final EconomicFactCommandApi economicFactApi;

    /** 三个计数字段必须存在。 */
    @Override
    public EconomicFactProgress getProgress(LedgerBook book) {
        PendingEconomicFactsResponseBody body = economicFactApi.getPendingEconomicFacts(book.getEntityType().getCode(),
            book.getEntityId(), book.getCurrency());
        if (body == null || body.getPendingCount() == null || body.getPublishedFactCount() == null) {
            throw new ErrorException("billing pending 响应无效: 账簿 " + book.getId());
        }
        return new EconomicFactProgress(Long.parseLong(body.getPendingCount()),
            Long.parseLong(body.getPublishedFactCount()), toLong(body.getMaxPublishedFactId()));
    }

    /** 回显的主键必须一致；版本不一致说明事实尚未定稿或已被替代，按并发冲突等待重投。 */
    @Override
    public EconomicFactPayload getFact(long factId, long factVersion) {
        EconomicFactResponseBody body = economicFactApi.getEconomicFact(Long.toString(factId));
        if (body == null || !Long.toString(factId).equals(body.getFactId()) || body.getLines() == null) {
            throw new ErrorException("billing 经济事实响应无效: " + factId);
        }
        if (!Long.toString(factVersion).equals(body.getFactVersion())) {
            throw new ConcurrentRequestException("经济事实版本与入账批次不一致: " + factId + ", 批次版本 " + factVersion
                + ", billing 版本 " + body.getFactVersion());
        }
        return toPayload(body);
    }

    /** 事实头转换。 */
    EconomicFactPayload toPayload(EconomicFactResponseBody body) {
        EconomicFactPayload payload = new EconomicFactPayload();
        payload.setFactId(Long.valueOf(body.getFactId()));
        payload.setFactVersion(Long.valueOf(body.getFactVersion()));
        payload.setSupersedesFactId(toLong(body.getSupersedesFactId()));
        payload.setParentFactId(toLong(body.getParentFactId()));
        payload.setFactType(body.getFactType());
        payload.setSourceSystem(body.getSourceSystem());
        payload.setSourceEventKey(body.getSourceReference());
        payload.setSourcePayloadHash(body.getContentHash());
        payload.setRecordedTime(body.getApprovedTime());
        payload.setPaymentSucceededTime(body.getPaymentSucceededTime());
        payload.setClientId(toLong(body.getClientId()));
        payload.setEntityNo(body.getEntityNo());
        payload.setLocalPartnerId(toLong(body.getLocalPartnerId()));
        payload.setPartnerAgreementOeId(toLong(body.getPartnerAgreementOeId()));
        payload.setFinanceEntityId(toLong(body.getFinanceEntityId()));
        payload.setCurrency(body.getCurrency());
        payload.setGrossAmount(toDecimal(body.getGrossAmount()));
        payload.setChannelAccountId(toLong(body.getChannelAccountId()));
        List<EconomicFactLinePayload> lines = body.getLines().stream().map(this::toLine)
            .sorted(Comparator.comparing(EconomicFactLinePayload::getLineNo, Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList();
        payload.setLines(lines);
        return payload;
    }

    /** 明细行转换：主体取收方。 */
    EconomicFactLinePayload toLine(EconomicFactLineResponseBody body) {
        EconomicFactLinePayload line = new EconomicFactLinePayload();
        line.setLineNo(body.getLineNo() == null ? null : Integer.valueOf(body.getLineNo()));
        line.setComponentType(body.getFeeTypeCode());
        line.setPartyType(body.getPayeeRole());
        line.setPartyId(body.getPayeeId());
        line.setPayerRole(body.getPayerRole());
        line.setPayerId(body.getPayerId());
        line.setPayeeRole(body.getPayeeRole());
        line.setPayeeId(body.getPayeeId());
        line.setDirection(body.getDirection());
        line.setPostedAmount(toDecimal(body.getAmount()));
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
