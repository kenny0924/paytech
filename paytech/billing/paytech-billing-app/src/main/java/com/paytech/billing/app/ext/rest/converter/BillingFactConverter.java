package com.paytech.billing.app.ext.rest.converter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;
import com.paytech.billing.api.request.ApproveBillingFactRequestBody;
import com.paytech.billing.api.request.BillingFactLineRequestBody;
import com.paytech.billing.api.request.BillingFactRefRequestBody;
import com.paytech.billing.api.request.SubmitBillingFactRequestBody;
import com.paytech.billing.api.response.BillingFactLineLinkResponseBody;
import com.paytech.billing.api.response.BillingFactLineResponseBody;
import com.paytech.billing.api.response.BillingFactListResponseBody;
import com.paytech.billing.api.response.BillingFactPricingResponseBody;
import com.paytech.billing.api.response.BillingFactProgressResponseBody;
import com.paytech.billing.api.response.BillingFactRefResponseBody;
import com.paytech.billing.api.response.BillingFactResponseBody;
import com.paytech.billing.domain.view.BillingFactLineEvidenceView;
import com.paytech.billing.domain.view.BillingFactLineLinkView;
import com.paytech.billing.domain.view.BillingFactPageView;
import com.paytech.billing.domain.view.BillingFactPricingView;
import com.paytech.billing.domain.view.BillingFactProgressView;
import com.paytech.billing.domain.view.BillingFactView;
import com.paytech.billing.service.processor.request.ApproveBillingFactRequest;
import com.paytech.billing.service.processor.request.SubmitBillingFactRequest;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 计费事实接口的 Wire 模型与内部请求 / View 转换
 * <li>ID、版本与整数输出为十进制字符串，金额已由 View 按币种精度补齐；代码类字段保持字符串传入，由 Processor 按目录解析。</li>
 */
@Component
public class BillingFactConverter {
    /** 列表接口缺省游标。 */
    static final long DEFAULT_FROM_FACT_ID = 0;

    /** 列表接口缺省每页条数。 */
    static final int DEFAULT_LIMIT = 100;

    /** 按账簿主体列表每页条数上限。 */
    static final int MAX_BOOK_LIMIT = 500;

    /** 按引用列表每页条数上限。 */
    static final int MAX_REF_LIMIT = 200;

    /** 转换提交请求；缺省引用为空列表。 */
    public SubmitBillingFactRequest toRequest(SubmitBillingFactRequestBody body) {
        SubmitBillingFactRequest request = new SubmitBillingFactRequest();
        request.setFactType(body.getFactType());
        request.setSourceSystem(body.getSourceSystem());
        request.setSourceReference(body.getSourceReference());
        request.setParentFactId(id(body.getParentFactId()));
        request.setSupersedesFactId(id(body.getSupersedesFactId()));
        request.setClientId(id(body.getClientId()));
        request.setEntityNo(body.getEntityNo());
        request.setLocalPartnerId(id(body.getLocalPartnerId()));
        request.setFinanceEntityId(id(body.getFinanceEntityId()));
        request.setCurrency(body.getCurrency());
        request.setMakerId(id(body.getMakerId()));
        List<BillingFactRefRequestBody> refs = body.getRefs() == null ? List.of() : body.getRefs();
        request.setRefs(new ArrayList<>(refs.stream().map(this::toRef).toList()));
        request.setLines(body.getLines().stream().map(this::toLine).toList());
        return request;
    }

    /** 转换外部引用。 */
    SubmitBillingFactRequest.Ref toRef(BillingFactRefRequestBody body) {
        SubmitBillingFactRequest.Ref ref = new SubmitBillingFactRequest.Ref();
        ref.setRefType(body.getRefType());
        ref.setRefId(body.getRefId());
        return ref;
    }

    /** 转换明细行。 */
    SubmitBillingFactRequest.Line toLine(BillingFactLineRequestBody body) {
        SubmitBillingFactRequest.Line line = new SubmitBillingFactRequest.Line();
        line.setComponentType(body.getComponentType());
        line.setPayerRole(body.getPayerRole());
        line.setPayerId(body.getPayerId());
        line.setPayeeRole(body.getPayeeRole());
        line.setPayeeId(body.getPayeeId());
        line.setAmount(new BigDecimal(body.getAmount()));
        line.setDirection(body.getDirection());
        line.setTargetBucket(body.getTargetBucket());
        return line;
    }

    /** 转换批准请求。 */
    public ApproveBillingFactRequest toRequest(String factId, ApproveBillingFactRequestBody body) {
        ApproveBillingFactRequest request = new ApproveBillingFactRequest();
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

    /** 按账簿主体列表每页条数；缺省 100，取值 1–500。 */
    public int bookLimit(String limit) {
        return limit(limit, MAX_BOOK_LIMIT);
    }

    /** 按引用列表每页条数；缺省 100，取值 1–200。 */
    public int refLimit(String limit) {
        return limit(limit, MAX_REF_LIMIT);
    }

    /** 每页条数；缺省 100，超出 1–max 以参数错误拒绝。 */
    static int limit(String limit, int max) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (!limit.matches("[1-9][0-9]{0,2}") || Integer.parseInt(limit) > max) {
            throw new InvalidParameterException("limit 必须在 1 到 " + max + " 之间: " + limit);
        }
        return Integer.parseInt(limit);
    }

    /** 引用类型代码；不在目录以参数错误拒绝。 */
    public FactRefType refType(String refType) {
        return CodeEnumConverter.parse(FactRefType.class, refType);
    }

    /** 转换事实详情。 */
    public BillingFactResponseBody toResponseBody(BillingFactView view) {
        BillingFactPayload payload = view.getPayload();
        BillingFactResponseBody body = new BillingFactResponseBody();
        body.setFactId(text(payload.getFactId()));
        body.setFactVersion(text(payload.getFactVersion()));
        body.setSupersedesFactId(text(payload.getSupersedesFactId()));
        body.setParentFactId(text(payload.getParentFactId()));
        body.setFactType(payload.getFactType());
        body.setOriginType(payload.getOriginType());
        body.setStatus(view.getStatus().getCode());
        body.setSourceSystem(payload.getSourceSystem());
        body.setSourceEventKey(payload.getSourceEventKey());
        body.setSourceVersion(text(payload.getSourceVersion()));
        body.setSourcePayloadHash(payload.getSourcePayloadHash());
        body.setBusinessTime(payload.getBusinessTime());
        body.setRecordedTime(payload.getRecordedTime());
        body.setBalanceAvailableTime(payload.getBalanceAvailableTime());
        body.setClientId(text(payload.getClientId()));
        body.setEntityNo(payload.getEntityNo());
        body.setLocalPartnerId(text(payload.getLocalPartnerId()));
        body.setFinanceEntityId(text(payload.getFinanceEntityId()));
        body.setCurrency(payload.getCurrency());
        body.setGrossAmount(payload.getGrossAmount().toPlainString());
        body.setMakerId(text(view.getMakerId()));
        body.setCheckerId(text(view.getCheckerId()));
        body.setApprovedTime(view.getApprovedTime());
        body.setPublishedAt(view.getPublishedAt());
        body.setPricing(view.getPricing() == null ? null : toPricingBody(view.getPricing()));
        body.setRefs(payload.getRefs().stream().map(this::toRefBody).toList());
        body.setLines(toLineBodies(payload.getLines(), view.getLineEvidence()));
        body.setLinks(view.getLinks().stream().map(this::toLinkBody).toList());
        return body;
    }

    /** 转换外部引用。 */
    BillingFactRefResponseBody toRefBody(BillingFactRefPayload payload) {
        BillingFactRefResponseBody body = new BillingFactRefResponseBody();
        body.setRefType(payload.getRefType());
        body.setRefId(payload.getRefId());
        return body;
    }

    /** 明细行载荷与计算证据按下标合并。 */
    List<BillingFactLineResponseBody> toLineBodies(List<BillingFactLinePayload> lines,
        List<BillingFactLineEvidenceView> evidence) {
        List<BillingFactLineResponseBody> bodies = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            bodies.add(toLineBody(lines.get(index), evidence.get(index)));
        }
        return bodies;
    }

    /** 转换一行明细及其计算证据。 */
    BillingFactLineResponseBody toLineBody(BillingFactLinePayload payload, BillingFactLineEvidenceView evidence) {
        BillingFactLineResponseBody body = new BillingFactLineResponseBody();
        body.setLineNo(text(payload.getLineNo()));
        body.setComponentType(payload.getComponentType());
        body.setPayerRole(payload.getPayerRole());
        body.setPayerId(payload.getPayerId());
        body.setPayeeRole(payload.getPayeeRole());
        body.setPayeeId(payload.getPayeeId());
        body.setDirection(payload.getDirection());
        body.setPostedAmount(payload.getPostedAmount().toPlainString());
        body.setTargetBucket(payload.getTargetBucket());
        body.setBasisAmount(evidence.getBasisAmount().toPlainString());
        body.setRateValue(decimal(evidence.getRateValue()));
        body.setRawAmount(evidence.getRawAmount().toPlainString());
        body.setSourceRuleType(CodeEnum.defaultCode(evidence.getSourceRuleType()));
        body.setSourceRuleVersionId(text(evidence.getSourceRuleVersionId()));
        body.setSourcePriceId(text(evidence.getSourcePriceId()));
        body.setSourceRuleComponentId(text(evidence.getSourceRuleComponentId()));
        body.setSourceEvidence(evidence.getSourceEvidence());
        return body;
    }

    /** 转换定价输入。 */
    BillingFactPricingResponseBody toPricingBody(BillingFactPricingView view) {
        BillingFactPricingResponseBody body = new BillingFactPricingResponseBody();
        body.setClientAgreementId(text(view.getClientAgreementId()));
        body.setPartnerAgreementOeId(text(view.getPartnerAgreementOeId()));
        body.setCommercialContextTime(view.getCommercialContextTime());
        body.setConsumerCountryCode(view.getConsumerCountryCode());
        body.setPaymentMethod(view.getPaymentMethod());
        body.setIndustryCode(view.getIndustryCode());
        body.setMccCode(view.getMccCode());
        body.setCardNetwork(view.getCardNetwork());
        body.setFundingType(view.getFundingType());
        body.setCardLevel(view.getCardLevel());
        body.setAuthenticationClass(view.getAuthenticationClass());
        body.setCommercialPriceId(text(view.getCommercialPriceId()));
        body.setSettlementBusinessDays(text(view.getSettlementBusinessDays()));
        body.setBusinessCalendarCode(view.getBusinessCalendarCode());
        body.setSettlementTimezone(view.getSettlementTimezone());
        body.setCutoffSecondOfDay(text(view.getCutoffSecondOfDay()));
        return body;
    }

    /** 转换行链接。 */
    BillingFactLineLinkResponseBody toLinkBody(BillingFactLineLinkView view) {
        BillingFactLineLinkResponseBody body = new BillingFactLineLinkResponseBody();
        body.setSourceLineId(text(view.getSourceLineId()));
        body.setTargetLineId(text(view.getTargetLineId()));
        body.setLinkType(view.getLinkType().getCode());
        body.setLinkedAmount(view.getLinkedAmount().toPlainString());
        return body;
    }

    /** 转换一页事实。 */
    public BillingFactListResponseBody toResponseBody(BillingFactPageView page) {
        BillingFactListResponseBody body = new BillingFactListResponseBody();
        body.setItems(page.getItems().stream().map(this::toResponseBody).toList());
        body.setNextFromFactId(text(page.getNextFromFactId()));
        return body;
    }

    /** 转换发布进度。 */
    public BillingFactProgressResponseBody toResponseBody(BillingFactProgressView view) {
        BillingFactProgressResponseBody body = new BillingFactProgressResponseBody();
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

    /** 十进制普通字符串；为空时为空。 */
    static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
