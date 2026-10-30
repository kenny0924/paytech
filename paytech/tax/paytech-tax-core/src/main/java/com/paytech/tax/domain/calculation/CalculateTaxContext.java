package com.paytech.tax.domain.calculation;

import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxCalculation;
import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;
import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;
import com.paytech.tax.core.dal.dataobject.TaxEvidence;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次新计算待原子保存的头、行、组件与证据；保存失败即丢弃。 */
@Getter
@AllArgsConstructor
public class CalculateTaxContext {
    /** 计算头 */
    private final TaxCalculation calculation;

    /** 行 */
    private final List<TaxCalculationLine> lines;

    /** 组件；BLOCKED 与 NOT_MANAGED 为空 */
    private final List<TaxCalculationComponent> components;

    /** 证据 */
    private final List<TaxEvidence> evidence;

    /**
     * 保存后的只读结果
     *
     * @return 与查询相同结构的计算视图
     */
    public TaxCalculationView toView() {
        return new TaxCalculationView(calculation, lines, components, evidence);
    }
}
