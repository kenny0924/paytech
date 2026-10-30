package com.paytech.tax.service.processor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import com.paytech.tax.api.enums.TaxAmountSource;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxPriceMode;
import com.paytech.tax.core.constant.TaxCodes;
import com.paytech.tax.core.dal.dataobject.TaxCalculation;
import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;
import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;
import com.paytech.tax.core.dal.dataobject.TaxEvidence;
import com.paytech.tax.core.enums.EvidenceValidationStatus;
import com.paytech.tax.domain.calculation.CalculateTaxContext;
import com.paytech.tax.domain.calculation.LineTaxAmounts;
import com.paytech.tax.domain.calculation.TaxBlock;
import com.paytech.tax.service.processor.request.CalculateTaxRequest;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * 从计算请求构造待保存的计算记录（CalculateTaxProcessor 的纯协作者）
 * <li>头、行、证据的公共字段一次构造；三种结果只决定状态、金额与组件。ID 来自本服务生成器，时间由调用方给出。</li>
 * <li>BLOCKED 与 NOT_MANAGED 不能计算的金额置空（不以零表示成功）：EXCLUSIVE 时净额已知、含税额为空；INCLUSIVE 反之。</li>
 */
final class CalculationDraft {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CalculateTaxRequest request;

    private final LongSupplier ids;

    private final Instant now;

    private final TaxCalculation calculation;

    private final List<TaxCalculationLine> lines;

    private final List<TaxEvidence> evidence;

    /**
     * 构造头、行与证据的公共部分
     *
     * @param request 已校验的计算请求
     * @param ids 本服务 Snowflake 生成器
     * @param now 本次计算时间（微秒）
     */
    CalculationDraft(CalculateTaxRequest request, LongSupplier ids, Instant now) {
        this.request = request;
        this.ids = ids;
        this.now = now;
        this.calculation = header();
        this.lines = request.getLines().stream().map(this::line).toList();
        this.evidence = request.getEvidence().stream().map(this::evidence).toList();
    }

    /** NOT_MANAGED：税额取自报值（缺省 0），不建组件与证据（technique 9.1 第 2 步）；行税额为空。 */
    CalculateTaxContext notManaged() {
        BigDecimal declared = request.getDeclaredTaxAmount() == null ? BigDecimal.ZERO : request.getDeclaredTaxAmount();
        calculation.setStatus(TaxCalculationStatus.NOT_MANAGED);
        calculation.setTaxAmountSource(TaxAmountSource.DECLARED);
        BigDecimal total = lineTotal();
        boolean inclusive = request.getPriceMode() == TaxPriceMode.INCLUSIVE;
        calculation.setNetAmount(inclusive ? total.subtract(declared) : total);
        calculation.setGrossAmount(inclusive ? total : total.add(declared));
        calculation.setChargedTaxAmount(declared);
        calculation.setRoundingAmount(BigDecimal.ZERO);
        return new CalculateTaxContext(calculation, lines, List.of(), List.of());
    }

    /** BLOCKED：保存原因，税额、尾差与不可知的一侧金额为空。 */
    CalculateTaxContext blocked(List<TaxBlock> blocks) {
        calculation.setStatus(TaxCalculationStatus.BLOCKED);
        calculation.setTaxAmountSource(TaxAmountSource.COMPUTED);
        ArrayNode reasons = JSON.createArrayNode();
        blocks.forEach(block -> reasons.addObject().put("code", block.code().getCode()).put("path", block.path()));
        calculation.setBlockReasonsJson(JSON.writeValueAsString(reasons));
        BigDecimal total = lineTotal();
        boolean inclusive = request.getPriceMode() == TaxPriceMode.INCLUSIVE;
        calculation.setNetAmount(inclusive ? null : total);
        calculation.setGrossAmount(inclusive ? total : null);
        return new CalculateTaxContext(calculation, lines, List.of(), evidence);
    }

    /**
     * READY：逐行写入金额与组件，头金额为行合计，头级尾差为 0
     *
     * @param amounts 与行同序的计算金额
     * @param components 与行同序的组件（尚未绑定行与计算 ID）
     * @param taxPointBasis 地区包税点依据
     * @param quoteValidityHours 报价有效小时数；只对 FINAL 生效
     * @return 待保存上下文
     */
    CalculateTaxContext ready(List<LineTaxAmounts> amounts, List<TaxCalculationComponent> components,
        String taxPointBasis, int quoteValidityHours) {
        List<TaxCalculationComponent> bound = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            TaxCalculationLine line = lines.get(index);
            LineTaxAmounts amount = amounts.get(index);
            line.setNetAmount(amount.net());
            line.setChargedTaxAmount(amount.tax());
            line.setGrossAmount(amount.gross());
            line.setRoundingDelta(amount.roundingDelta());
            bound.add(bind(components.get(index), line));
        }
        calculation.setStatus(TaxCalculationStatus.READY);
        calculation.setTaxAmountSource(TaxAmountSource.COMPUTED);
        calculation.setTaxPointBasis(taxPointBasis);
        calculation.setNetAmount(sum(amounts.stream().map(LineTaxAmounts::net).toList()));
        calculation.setChargedTaxAmount(sum(amounts.stream().map(LineTaxAmounts::tax).toList()));
        calculation.setGrossAmount(sum(amounts.stream().map(LineTaxAmounts::gross).toList()));
        calculation.setRoundingAmount(BigDecimal.ZERO);
        if (request.getStage() == TaxCalculationStage.FINAL) {
            calculation.setExpiresAt(now.plus(quoteValidityHours, ChronoUnit.HOURS));
        }
        return new CalculateTaxContext(calculation, lines, List.copyOf(bound), evidence);
    }

    /** 本计算的头（ID 已分配）。 */
    TaxCalculation calculation() {
        return calculation;
    }

    /** 组件绑定所属计算与行，并分配 ID。 */
    TaxCalculationComponent bind(TaxCalculationComponent component, TaxCalculationLine line) {
        component.setId(ids.getAsLong());
        component.setCalculationId(calculation.getId());
        component.setLineId(line.getId());
        component.setCreateTime(now);
        return component;
    }

    /** 计算头公共字段。 */
    TaxCalculation header() {
        TaxCalculation header = new TaxCalculation();
        header.setId(ids.getAsLong());
        header.setSourceSystem(request.getSourceSystem());
        header.setSourceObjectType(request.getSourceObjectType());
        header.setSourceObjectId(request.getSourceObjectId());
        header.setSourceObjectNo(request.getSourceObjectNo());
        header.setSourceRevision(request.getSourceRevision());
        header.setTaxScopeKey(request.getTaxScopeKey());
        header.setCalculationStage(request.getStage());
        header.setSupplyKind(request.getSupplyKind());
        header.setBusinessMode(request.getBusinessMode());
        header.setSellerPartyId(request.getSellerPartyId());
        header.setBuyerClientId(request.getBuyerClientId());
        header.setTaxSubjectPartyId(request.getTaxSubjectPartyId());
        header.setAcquiringEntityNo(request.getEntityNo());
        header.setCurrency(request.getCurrency());
        header.setPriceMode(request.getPriceMode());
        header.setTaxPointDate(request.getTaxPointDate());
        header.setTaxPointBasis(TaxCodes.TAX_POINT_PAYMENT);
        header.setCreateTime(now);
        return header;
    }

    /** 行公共字段：已知一侧金额按价格口径填写，税额与另一侧金额待结果决定。 */
    TaxCalculationLine line(CalculateTaxRequest.Line source) {
        TaxCalculationLine line = new TaxCalculationLine();
        line.setId(ids.getAsLong());
        line.setCalculationId(calculation.getId());
        line.setSourceLineRef(source.getLineRef());
        line.setLineNo(source.getLineNo());
        line.setCategoryCode(source.getCategoryCode());
        line.setDescription(source.getDescription());
        line.setQuantity(source.getQuantity());
        line.setUnitPrice(source.getUnitPrice());
        line.setDiscountAmount(source.getDiscountAmount());
        boolean inclusive = request.getPriceMode() == TaxPriceMode.INCLUSIVE;
        line.setNetAmount(inclusive ? null : source.getLineAmount());
        line.setGrossAmount(inclusive ? source.getLineAmount() : null);
        line.setRoundingDelta(BigDecimal.ZERO);
        line.setCreateTime(now);
        return line;
    }

    /** 证据：本期国家证据均未核验。 */
    TaxEvidence evidence(CalculateTaxRequest.Evidence source) {
        TaxEvidence item = new TaxEvidence();
        item.setId(ids.getAsLong());
        item.setCalculationId(calculation.getId());
        item.setEvidenceType(source.getType());
        item.setSourceCode(request.getSourceSystem());
        item.setCountryCode(source.getCountryCode());
        item.setSubdivisionCode(source.getSubdivisionCode());
        item.setValidationStatus(EvidenceValidationStatus.NOT_CHECKED);
        item.setCreateTime(now);
        return item;
    }

    /** 请求行金额合计。 */
    BigDecimal lineTotal() {
        return sum(request.getLines().stream().map(CalculateTaxRequest.Line::getLineAmount).toList());
    }

    /** 金额求和。 */
    static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
