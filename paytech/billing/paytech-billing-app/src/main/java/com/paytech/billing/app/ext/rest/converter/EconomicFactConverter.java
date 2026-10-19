package com.paytech.billing.app.ext.rest.converter;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.api.request.ApproveEconomicFactRequestBody;
import com.paytech.billing.api.request.EconomicFactLineRequestBody;
import com.paytech.billing.api.request.SubmitEconomicFactRequestBody;
import com.paytech.billing.api.response.EconomicFactLineResponseBody;
import com.paytech.billing.api.response.EconomicFactListResponseBody;
import com.paytech.billing.api.response.EconomicFactResponseBody;
import com.paytech.billing.api.response.PendingEconomicFactsResponseBody;
import com.paytech.billing.domain.view.EconomicFactPageView;
import com.paytech.billing.domain.view.EconomicFactPublicationView;
import com.paytech.billing.domain.view.EconomicFactView;
import com.paytech.billing.service.processor.request.ApproveEconomicFactRequest;
import com.paytech.billing.service.processor.request.SubmitEconomicFactRequest;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 经济事实接口的 Wire 模型与内部请求 / View 转换
 * <li>ID 与版本输出为十进制字符串，金额已由 View 按币种精度补齐；代码类字段保持字符串传入，由 Processor 按目录解析。</li>
 */
@Component
public class EconomicFactConverter {
    /** 列表接口缺省游标。 */
    static final long DEFAULT_FROM_FACT_ID = 0;

    /** 列表接口缺省每页条数。 */
    static final int DEFAULT_LIMIT = 100;

    /** 列表接口每页条数上限。 */
    static final int MAX_LIMIT = 500;

    /** 转换提交请求。 */
    public SubmitEconomicFactRequest toRequest(SubmitEconomicFactRequestBody body) {
        SubmitEconomicFactRequest request = new SubmitEconomicFactRequest();
        request.setFactType(body.getFactType());
        request.setSourceSystem(body.getSourceSystem());
        request.setSourceReference(body.getSourceReference());
        request.setParentFactId(id(body.getParentFactId()));
        request.setSupersedesFactId(id(body.getSupersedesFactId()));
        request.setClientId(id(body.getClientId()));
        request.setEntityNo(body.getEntityNo());
        request.setLocalPartnerId(id(body.getLocalPartnerId()));
        request.setPartnerAgreementOeId(id(body.getPartnerAgreementOeId()));
        request.setFinanceEntityId(id(body.getFinanceEntityId()));
        request.setCurrency(body.getCurrency());
        request.setChannelAccountId(id(body.getChannelAccountId()));
        request.setMakerId(id(body.getMakerId()));
        request.setLines(body.getLines().stream().map(this::toLine).toList());
        return request;
    }

    /** 转换明细行。 */
    SubmitEconomicFactRequest.Line toLine(EconomicFactLineRequestBody body) {
        SubmitEconomicFactRequest.Line line = new SubmitEconomicFactRequest.Line();
        line.setFeeTypeCode(body.getFeeTypeCode());
        line.setPayerRole(body.getPayerRole());
        line.setPayerId(body.getPayerId());
        line.setPayeeRole(body.getPayeeRole());
        line.setPayeeId(body.getPayeeId());
        line.setAmount(new BigDecimal(body.getAmount()));
        line.setDirection(body.getDirection());
        return line;
    }

    /** 转换批准请求。 */
    public ApproveEconomicFactRequest toRequest(String factId, ApproveEconomicFactRequestBody body) {
        ApproveEconomicFactRequest request = new ApproveEconomicFactRequest();
        request.setFactId(Long.valueOf(factId));
        request.setCheckerId(Long.valueOf(body.getCheckerId()));
        return request;
    }

    /** 列表游标；缺省为 0，非十进制非负整数以参数错误拒绝。 */
    public long fromFactId(String fromFactId) {
        if (fromFactId == null) {
            return DEFAULT_FROM_FACT_ID;
        }
        if (!fromFactId.matches("0|[1-9][0-9]{0,18}")) {
            throw new InvalidParameterException("fromFactId 必须是非负十进制整数: " + fromFactId);
        }
        return Long.parseLong(fromFactId);
    }

    /** 每页条数；缺省 100，取值 1–500，其余以参数错误拒绝。 */
    public int limit(String limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (!limit.matches("[1-9][0-9]{0,2}") || Integer.parseInt(limit) > MAX_LIMIT) {
            throw new InvalidParameterException("limit 必须在 1 到 " + MAX_LIMIT + " 之间: " + limit);
        }
        return Integer.parseInt(limit);
    }

    /** 转换事实详情。 */
    public EconomicFactResponseBody toResponseBody(EconomicFactView view) {
        EconomicFactPayload payload = view.getPayload();
        EconomicFactResponseBody body = new EconomicFactResponseBody();
        body.setFactId(text(payload.getFactId()));
        body.setFactType(payload.getFactType());
        body.setStatus(view.getStatus().getCode());
        body.setFactVersion(text(payload.getFactVersion()));
        body.setSourceSystem(payload.getSourceSystem());
        body.setSourceReference(payload.getSourceEventKey());
        body.setContentHash(payload.getSourcePayloadHash());
        body.setParentFactId(text(payload.getParentFactId()));
        body.setSupersedesFactId(text(payload.getSupersedesFactId()));
        body.setClientId(text(payload.getClientId()));
        body.setEntityNo(payload.getEntityNo());
        body.setLocalPartnerId(text(payload.getLocalPartnerId()));
        body.setPartnerAgreementOeId(text(payload.getPartnerAgreementOeId()));
        body.setFinanceEntityId(text(payload.getFinanceEntityId()));
        body.setCurrency(payload.getCurrency());
        body.setGrossAmount(payload.getGrossAmount().toPlainString());
        body.setChannelAccountId(text(payload.getChannelAccountId()));
        body.setMakerId(text(view.getMakerId()));
        body.setCheckerId(text(view.getCheckerId()));
        body.setApprovedTime(view.getApprovedTime());
        body.setPaymentSucceededTime(payload.getPaymentSucceededTime());
        body.setPublishedAt(view.getPublishedAt());
        body.setLines(payload.getLines().stream().map(this::toLineBody).toList());
        return body;
    }

    /** 转换明细行。 */
    EconomicFactLineResponseBody toLineBody(EconomicFactLinePayload payload) {
        EconomicFactLineResponseBody body = new EconomicFactLineResponseBody();
        body.setLineNo(text(payload.getLineNo()));
        body.setFeeTypeCode(payload.getComponentType());
        body.setPayerRole(payload.getPayerRole());
        body.setPayerId(payload.getPayerId());
        body.setPayeeRole(payload.getPayeeRole());
        body.setPayeeId(payload.getPayeeId());
        body.setAmount(payload.getPostedAmount().toPlainString());
        body.setDirection(payload.getDirection());
        return body;
    }

    /** 转换一页事实。 */
    public EconomicFactListResponseBody toResponseBody(EconomicFactPageView page) {
        EconomicFactListResponseBody body = new EconomicFactListResponseBody();
        body.setItems(page.getItems().stream().map(this::toResponseBody).toList());
        body.setNextFromFactId(text(page.getNextFromFactId()));
        return body;
    }

    /** 转换发布进度。 */
    public PendingEconomicFactsResponseBody toResponseBody(EconomicFactPublicationView view) {
        PendingEconomicFactsResponseBody body = new PendingEconomicFactsResponseBody();
        body.setPendingCount(Long.toString(view.getPendingCount()));
        body.setMaxPendingFactId(text(view.getMaxPendingFactId()));
        body.setPublishedFactCount(Long.toString(view.getPublishedFactCount()));
        body.setMaxPublishedFactId(text(view.getMaxPublishedFactId()));
        return body;
    }

    /** 十进制 ID 字符串转 Long；为空时为空。 */
    static Long id(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    /** 数值转十进制字符串；为空时为空。 */
    static String text(Number value) {
        return value == null ? null : value.toString();
    }
}
