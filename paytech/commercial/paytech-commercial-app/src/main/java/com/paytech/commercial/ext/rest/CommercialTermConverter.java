package com.paytech.commercial.ext.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.api.enums.RefundBehavior;
import com.paytech.commercial.api.term.request.CommercialTermBandRequestBody;
import com.paytech.commercial.api.term.request.CommercialTermComponentRequestBody;
import com.paytech.commercial.api.term.request.CommercialTermPriceRequestBody;
import com.paytech.commercial.api.term.request.CommercialTermVersionCommandRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermPlanRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermVersionRequestBody;
import com.paytech.commercial.api.term.request.ReplaceCommercialTermPricesRequestBody;
import com.paytech.commercial.api.term.request.ScheduleCommercialTermVersionRequestBody;
import com.paytech.commercial.api.term.response.CommercialTermPlanResponseBody;
import com.paytech.commercial.api.term.response.CommercialTermVersionResponseBody;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.enums.CommercialTermComponentBandMode;
import com.paytech.commercial.domain.enums.CommercialTermComponentComponentType;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.commercial.service.processor.request.CreateCommercialTermPlanRequest;
import com.paytech.commercial.service.processor.request.CreateCommercialTermVersionRequest;
import com.paytech.commercial.service.processor.request.ReplaceCommercialTermPricesRequest;
import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 商业条款写入的 Wire 转换：十进制字符串 ID / 版本 → long，枚举 Code → 内部枚举（值域已由 Body 注解限定），十进制字符串 → BigDecimal
 * <li>只做协议转换，不查询、不校验业务规则。</li>
 */
final class CommercialTermConverter {
    /** 纯静态转换。 */
    private CommercialTermConverter() {
    }

    /** 创建方案；操作者取 X-Gateway-Actor-Subject 请求头（与合同写入同一解析）。 */
    static CreateCommercialTermPlanRequest toRequest(String actorId, CreateCommercialTermPlanRequestBody body) {
        return new CreateCommercialTermPlanRequest(body.getPlanCode(), body.getTermType(), Long.parseLong(body.getAgreementId()),
            id(body.getClientId()), id(body.getLocalPartnerId()), AgreementConverter.actor(actorId));
    }

    /** 创建版本。 */
    static CreateCommercialTermVersionRequest toRequest(String actorId,
        String planId, CreateCommercialTermVersionRequestBody body) {
        return new CreateCommercialTermVersionRequest(Long.parseLong(planId), Long.parseLong(body.getExpectedChangeVersion()),
            AgreementConverter.actor(actorId), id(body.getCopyFromVersionId()), id(body.getAgreementId()),
            code(PricingModel.class, body.getPricingModel()), code(FeeTaxTreatment.class, body.getFeeTaxTreatment()));
    }

    /** 整体替换价格集合。 */
    static ReplaceCommercialTermPricesRequest toRequest(String actorId,
        String versionId, ReplaceCommercialTermPricesRequestBody body) {
        return new ReplaceCommercialTermPricesRequest(Long.parseLong(versionId), Long.parseLong(body.getExpectedChangeVersion()),
            AgreementConverter.actor(actorId), body.getPrices().stream().map(CommercialTermConverter::price).toList());
    }

    /** 提交、批准、驳回、激活。 */
    static CommercialTermVersionCommandRequest toRequest(String actorId,
        String versionId, CommercialTermVersionCommandRequestBody body) {
        return CommercialTermVersionCommandRequest.operator(Long.parseLong(versionId),
            Long.parseLong(body.getExpectedChangeVersion()), AgreementConverter.actor(actorId));
    }

    /** 排期。 */
    static CommercialTermVersionCommandRequest toRequest(String actorId,
        String versionId, ScheduleCommercialTermVersionRequestBody body) {
        return new CommercialTermVersionCommandRequest(Long.parseLong(versionId), Long.parseLong(body.getExpectedChangeVersion()),
            AgreementConverter.actor(actorId), Instant.parse(body.getEffectiveFrom()));
    }

    /** 方案响应。 */
    static CommercialTermPlanResponseBody toPlanResponseBody(CommercialTermVersionView view) {
        CommercialTermPlan plan = view.plan();
        CommercialTermPlanResponseBody body = new CommercialTermPlanResponseBody();
        body.setPlanId(text(plan.getTermPlanId()));
        body.setPlanCode(plan.getPlanCode());
        body.setTermType(plan.getTermType());
        body.setAgreementId(text(plan.getAgreementId()));
        body.setEntityNo(plan.getEntityNo());
        body.setClientId(text(plan.getClientId()));
        body.setLocalPartnerId(text(plan.getLocalPartnerId()));
        body.setStatus(CodeEnum.defaultCode(plan.getStatus()));
        body.setChangeVersion(Long.toString(view.changeVersion()));
        return body;
    }

    /** 版本响应。 */
    static CommercialTermVersionResponseBody toVersionResponseBody(CommercialTermVersionView view) {
        CommercialTermVersion version = view.version();
        CommercialTermVersionResponseBody body = new CommercialTermVersionResponseBody();
        body.setVersionId(text(version.getTermVersionId()));
        body.setPlanId(text(version.getTermPlanId()));
        body.setTermType(view.plan().getTermType());
        body.setAgreementId(text(version.getAgreementId()));
        body.setVersionNo(text(version.getVersionNo()));
        body.setStatus(CodeEnum.defaultCode(version.getStatus()));
        body.setEffectiveFrom(text(version.getEffectiveFrom()));
        body.setEffectiveTo(text(version.getEffectiveTo()));
        body.setRoundingMode(CodeEnum.defaultCode(version.getRoundingMode()));
        body.setPricingModel(CodeEnum.defaultCode(version.getPricingModel()));
        body.setFeeTaxTreatment(CodeEnum.defaultCode(version.getFeeTaxTreatment()));
        body.setChangeVersion(Long.toString(view.changeVersion()));
        return body;
    }

    /** 一条价格。 */
    static ReplaceCommercialTermPricesRequest.PriceItem price(CommercialTermPriceRequestBody body) {
        return new ReplaceCommercialTermPricesRequest.PriceItem(body.getEntityNo(), body.getCurrency(),
            body.getPricingProfileCode(), body.getPriorityNo(), body.getSettlementBusinessDays(),
            body.getBusinessCalendarCode(), body.getSettlementTimezone(), body.getCutoffSecondOfDay(),
            body.getComponents().stream().map(CommercialTermConverter::component).toList());
    }

    /** 一个组件。 */
    static ReplaceCommercialTermPricesRequest.ComponentItem component(CommercialTermComponentRequestBody body) {
        List<CommercialTermBandRequestBody> bands = body.getBands() == null ? List.of() : body.getBands();
        return new ReplaceCommercialTermPricesRequest.ComponentItem(body.getComponentCode(),
            code(CommercialTermComponentComponentType.class, body.getComponentType()), body.getTriggerCode(),
            code(CalculationBase.class, body.getCalculationBase()), code(RefundBehavior.class, body.getRefundBehavior()),
            decimal(body.getRate()), decimal(body.getFixedAmount()), body.getCurrency(), decimal(body.getMinAmount()),
            decimal(body.getMaxAmount()), code(BandBasis.class, body.getBandBasis()),
            code(CommercialTermComponentBandMode.class, body.getBandMode()),
            bands.stream().map(band -> new ReplaceCommercialTermPricesRequest.BandItem(decimal(band.getLowerBound()),
                decimal(band.getUpperBound()), decimal(band.getRate()), decimal(band.getFixedAmount()))).toList());
    }

    /** 按 Code 取枚举，空为空；值域已由 Body 注解限定。 */
    static <E extends Enum<E> & CodeEnum> E code(Class<E> type, String code) {
        if (code == null) {
            return null;
        }
        for (E value : type.getEnumConstants()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " code");
    }

    /** 十进制字符串，空为空。 */
    static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    /** 可空十进制 ID。 */
    static Long id(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    /** 文本，空为空。 */
    static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
