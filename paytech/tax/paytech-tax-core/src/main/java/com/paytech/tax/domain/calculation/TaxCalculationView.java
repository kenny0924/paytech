package com.paytech.tax.domain.calculation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.core.dal.dataobject.TaxCalculation;
import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;
import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;
import com.paytech.tax.core.dal.dataobject.TaxEvidence;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次计算的完整结果：头、行（按行号）、组件与证据（多表组合，计算保存与查询共用）
 * <li>计算不可变，本对象只读；组件汇总与供应地证据判断为纯函数。</li>
 */
@Getter
@AllArgsConstructor
public class TaxCalculationView {
    /** 计算头 */
    private final TaxCalculation calculation;

    /** 行，按行号升序 */
    private final List<TaxCalculationLine> lines;

    /** 全部组件 */
    private final List<TaxCalculationComponent> components;

    /** 冻结的证据 */
    private final List<TaxEvidence> evidence;

    /**
     * 一行上的组件
     *
     * @param lineId 计算行 ID
     * @return 该行组件，保持保存顺序
     */
    public List<TaxCalculationComponent> componentsOf(long lineId) {
        return components.stream().filter(component -> component.getLineId() == lineId).toList();
    }

    /**
     * 按辖区 + 注册 + 纳税主体汇总税额大于 0 的组件
     *
     * @return 汇总，按首次出现顺序；BLOCKED 与 NOT_MANAGED 为空
     */
    public List<TaxComponentSummary> componentSummaries() {
        Map<List<Object>, BigDecimal> totals = new LinkedHashMap<>();
        Map<List<Object>, TaxCalculationComponent> firsts = new LinkedHashMap<>();
        for (TaxCalculationComponent component : components) {
            if (component.getChargedTaxAmount().signum() <= 0) {
                continue;
            }
            List<Object> key = new ArrayList<>();
            key.add(component.getJurisdictionCode());
            key.add(component.getRegistrationId());
            totals.merge(key, component.getChargedTaxAmount(), BigDecimal::add);
            firsts.putIfAbsent(key, component);
        }
        return totals.entrySet().stream().map(entry -> new TaxComponentSummary(
            firsts.get(entry.getKey()).getJurisdictionCode(), firsts.get(entry.getKey()).getTaxType(),
            firsts.get(entry.getKey()).getRegistrationId(), calculation.getTaxSubjectPartyId(), entry.getValue())).toList();
    }

    /**
     * 本证据是否为确定供应地所用：FINAL 用账单国家，PROVISIONAL 用声明国家
     *
     * @param item 本计算的一条证据
     * @return 是供应地依据时为 true
     */
    public boolean placeOfSupplyBasis(TaxEvidence item) {
        return Objects.equals(item.getEvidenceType(), basisEvidenceType(calculation.getCalculationStage()));
    }

    /**
     * 阶段对应的供应地证据类型
     *
     * @param stage 计算阶段
     * @return FINAL 为 BILLING_COUNTRY，PROVISIONAL 为 DECLARED_COUNTRY
     */
    public static TaxEvidenceType basisEvidenceType(TaxCalculationStage stage) {
        return stage == TaxCalculationStage.FINAL ? TaxEvidenceType.BILLING_COUNTRY : TaxEvidenceType.DECLARED_COUNTRY;
    }
}
