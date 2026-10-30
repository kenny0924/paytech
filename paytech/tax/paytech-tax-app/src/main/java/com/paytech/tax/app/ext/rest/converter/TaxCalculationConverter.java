package com.paytech.tax.app.ext.rest.converter;

import static com.paytech.tax.app.ext.rest.converter.TaxAmounts.amount;
import static com.paytech.tax.app.ext.rest.converter.TaxAmounts.date;
import static com.paytech.tax.app.ext.rest.converter.TaxAmounts.decimal;
import static com.paytech.tax.app.ext.rest.converter.TaxAmounts.id;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxNotManagedReason;
import com.paytech.tax.api.enums.TaxPriceMode;
import com.paytech.tax.api.request.CalculateTaxRequestBody;
import com.paytech.tax.api.request.TaxCalculationLineRequestBody;
import com.paytech.tax.api.request.TaxEvidenceRequestBody;
import com.paytech.tax.api.response.TaxBlockReasonResponseBody;
import com.paytech.tax.api.response.TaxCalculationLineResponseBody;
import com.paytech.tax.api.response.TaxCalculationResponseBody;
import com.paytech.tax.api.response.TaxComponentResponseBody;
import com.paytech.tax.api.response.TaxComponentSummaryResponseBody;
import com.paytech.tax.api.response.TaxEvidenceResponseBody;
import com.paytech.tax.core.dal.dataobject.TaxCalculation;
import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;
import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;
import com.paytech.tax.core.dal.dataobject.TaxEvidence;
import com.paytech.tax.domain.calculation.TaxCalculationView;
import com.paytech.tax.domain.calculation.TaxComponentSummary;
import com.paytech.tax.service.processor.request.CalculateTaxRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 税务计算的 HTTP Body 与内部请求、计算视图之间的协议转换（字段格式已由 Body 注解校验）。 */
@Component
public class TaxCalculationConverter {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * 请求 Body → 内部请求
     *
     * @param body 已通过注解校验的请求
     * @return 类型化的内部请求；日期不是合法日历日期时 400
     */
    public CalculateTaxRequest toRequest(CalculateTaxRequestBody body) {
        List<CalculateTaxRequest.Evidence> evidence = new ArrayList<>();
        for (TaxEvidenceRequestBody item : body.getEvidence()) {
            evidence.add(new CalculateTaxRequest.Evidence(code(TaxEvidenceType.class, item.getType()), item.getCountryCode(),
                item.getSubdivisionCode()));
        }
        List<CalculateTaxRequest.Line> lines = new ArrayList<>();
        for (TaxCalculationLineRequestBody line : body.getLines()) {
            lines.add(new CalculateTaxRequest.Line(line.getLineRef(), line.getLineNo(), line.getCategoryCode(), line.getDescription(),
                new BigDecimal(line.getQuantity()), new BigDecimal(line.getUnitPrice()),
                new BigDecimal(line.getDiscountAmount()), new BigDecimal(line.getLineAmount())));
        }
        return new CalculateTaxRequest(body.getSourceSystem(), body.getSourceObjectType(),
            Long.parseLong(body.getSourceObjectId()), body.getSourceObjectNo(), body.getSourceRevision(),
            body.getTaxScopeKey(), code(TaxCalculationStage.class, body.getStage()), body.getSupplyKind(),
            body.getBusinessMode(), body.getSellerPartyId() == null ? null : Long.valueOf(body.getSellerPartyId()),
            body.getTaxSubjectPartyId() == null ? null : Long.valueOf(body.getTaxSubjectPartyId()),
            Long.parseLong(body.getBuyerClientId()), body.getEntityNo(),
            body.getCurrency(), code(TaxPriceMode.class, body.getPriceMode()), parseDate(body.getTaxPointDate()), evidence, lines,
            body.getDeclaredTaxAmount() == null ? null : new BigDecimal(body.getDeclaredTaxAmount()));
    }

    /**
     * 计算视图 → 响应 Body
     *
     * @param view 计算全貌
     * @return 响应
     */
    public TaxCalculationResponseBody toBody(TaxCalculationView view) {
        TaxCalculation calculation = view.getCalculation();
        String currency = calculation.getCurrency();
        TaxCalculationResponseBody body = new TaxCalculationResponseBody();
        body.setCalculationId(id(calculation.getId()));
        body.setSourceSystem(calculation.getSourceSystem());
        body.setSourceObjectType(calculation.getSourceObjectType());
        body.setSourceObjectId(id(calculation.getSourceObjectId()));
        body.setSourceObjectNo(calculation.getSourceObjectNo());
        body.setSourceRevision(calculation.getSourceRevision());
        body.setTaxScopeKey(calculation.getTaxScopeKey());
        body.setStage(calculation.getCalculationStage().getCode());
        body.setStatus(calculation.getStatus().getCode());
        body.setNotManagedReason(notManagedReason(calculation));
        body.setTaxAmountSource(calculation.getTaxAmountSource().getCode());
        body.setSupplyKind(calculation.getSupplyKind());
        body.setBusinessMode(calculation.getBusinessMode());
        body.setSellerPartyId(id(calculation.getSellerPartyId()));
        body.setTaxSubjectPartyId(id(calculation.getTaxSubjectPartyId()));
        body.setBuyerClientId(id(calculation.getBuyerClientId()));
        body.setEntityNo(calculation.getAcquiringEntityNo());
        body.setCurrency(currency);
        body.setPriceMode(calculation.getPriceMode().getCode());
        body.setTaxPointDate(date(calculation.getTaxPointDate()));
        body.setTaxPointBasis(calculation.getTaxPointBasis());
        body.setNetAmount(amount(currency, calculation.getNetAmount()));
        body.setTaxAmount(amount(currency, calculation.getChargedTaxAmount()));
        body.setRoundingAmount(amount(currency, calculation.getRoundingAmount()));
        body.setGrossAmount(amount(currency, calculation.getGrossAmount()));
        body.setExpiresAt(calculation.getExpiresAt());
        body.setCalculatedAt(calculation.getCreateTime());
        body.setBlockReasons(blockReasons(calculation.getBlockReasonsJson()));
        body.setEvidence(view.getEvidence().stream().map(item -> evidence(view, item)).toList());
        body.setLines(view.getLines().stream().map(line -> line(view, line, currency)).toList());
        body.setComponents(view.componentSummaries().stream().map(summary -> summary(summary, currency)).toList());
        return body;
    }

    /** 一行及其组件。 */
    TaxCalculationLineResponseBody line(TaxCalculationView view, TaxCalculationLine line, String currency) {
        TaxCalculationLineResponseBody body = new TaxCalculationLineResponseBody();
        body.setLineId(id(line.getId()));
        body.setLineRef(line.getSourceLineRef());
        body.setLineNo(line.getLineNo());
        body.setCategoryCode(line.getCategoryCode());
        body.setDescription(line.getDescription());
        body.setQuantity(decimal(line.getQuantity()));
        body.setUnitPrice(amount(currency, line.getUnitPrice()));
        body.setDiscountAmount(amount(currency, line.getDiscountAmount()));
        body.setNetAmount(amount(currency, line.getNetAmount()));
        body.setTaxAmount(amount(currency, line.getChargedTaxAmount()));
        body.setGrossAmount(amount(currency, line.getGrossAmount()));
        body.setRoundingDelta(line.getRoundingDelta().toPlainString());
        body.setComponents(view.componentsOf(line.getId()).stream().map(component -> component(component, currency)).toList());
        return body;
    }

    /** 一个行内组件。 */
    TaxComponentResponseBody component(TaxCalculationComponent component, String currency) {
        TaxComponentResponseBody body = new TaxComponentResponseBody();
        body.setComponentId(id(component.getId()));
        body.setComponentCode(component.getComponentCode());
        body.setJurisdictionCode(component.getJurisdictionCode());
        body.setTaxType(component.getTaxType());
        body.setTreatment(component.getTreatment().getCode());
        body.setReasonCode(component.getReasonCode());
        body.setLiableRole(component.getLiableRole().getCode());
        body.setMarketId(id(component.getMarketId()));
        body.setRegistrationId(id(component.getRegistrationId()));
        body.setPolicyId(id(component.getPolicyId()));
        body.setRuleCode(component.getRuleCode());
        body.setTaxBase(amount(currency, component.getTaxBase()));
        body.setRate(decimal(component.getRate()));
        body.setTaxAmount(amount(currency, component.getChargedTaxAmount()));
        body.setCalculationMethod(component.getCalculationMethod().getCode());
        body.setRoundingMethod(component.getRoundingMethod());
        body.setRoundingScale(component.getRoundingScale());
        return body;
    }

    /** 一个汇总组件。 */
    static TaxComponentSummaryResponseBody summary(TaxComponentSummary summary, String currency) {
        TaxComponentSummaryResponseBody body = new TaxComponentSummaryResponseBody();
        body.setJurisdictionCode(summary.jurisdictionCode());
        body.setTaxType(summary.taxType());
        body.setRegistrationId(id(summary.registrationId()));
        body.setTaxSubjectPartyId(Long.toString(summary.taxSubjectPartyId()));
        body.setTaxAmount(amount(currency, summary.taxAmount()));
        return body;
    }

    /** 一条证据。 */
    static TaxEvidenceResponseBody evidence(TaxCalculationView view, TaxEvidence item) {
        TaxEvidenceResponseBody body = new TaxEvidenceResponseBody();
        body.setType(item.getEvidenceType().getCode());
        body.setCountryCode(item.getCountryCode());
        body.setSubdivisionCode(item.getSubdivisionCode());
        body.setPlaceOfSupplyBasis(view.placeOfSupplyBasis(item));
        return body;
    }

    /** block_reasons_json → 原因列表；空为空列表。 */
    static List<TaxBlockReasonResponseBody> blockReasons(String json) {
        List<TaxBlockReasonResponseBody> reasons = new ArrayList<>();
        if (json == null) {
            return reasons;
        }
        for (JsonNode node : JSON.readTree(json)) {
            TaxBlockReasonResponseBody reason = new TaxBlockReasonResponseBody();
            reason.setCode(node.path("code").asString());
            reason.setPath(node.path("path").asString());
            reasons.add(reason);
        }
        return reasons;
    }

    /** NOT_MANAGED 的原因：未传责任方为 TAX_RESPONSIBLE_UNRESOLVED，否则 SUBJECT_NOT_REGISTERED；其他状态为空。 */
    static String notManagedReason(TaxCalculation calculation) {
        if (calculation.getStatus() != TaxCalculationStatus.NOT_MANAGED) {
            return null;
        }
        return (calculation.getTaxSubjectPartyId() == null ? TaxNotManagedReason.TAX_RESPONSIBLE_UNRESOLVED
            : TaxNotManagedReason.SUBJECT_NOT_REGISTERED).getCode();
    }

    /** 已由 Body 注解限定的 Code → 枚举。 */
    static <E extends CodeEnum> E code(Class<E> type, String code) {
        E value = EnumUtils.getEnumByCode(type, code);
        if (value == null) {
            throw new InvalidParameterException("不支持的代码: " + code);
        }
        return value;
    }

    /** yyyy-MM-dd → 日期；非法日历日期 400。 */
    static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException("taxPointDate 不是合法日期: " + text);
        }
    }
}
