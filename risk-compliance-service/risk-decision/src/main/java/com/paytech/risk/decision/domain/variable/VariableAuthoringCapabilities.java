package com.paytech.risk.decision.domain.variable;

import com.paytech.risk.decision.domain.enums.*;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 工程提供的变量编排能力，不替代数据库变量候选。 */
@Getter
@AllArgsConstructor
public class VariableAuthoringCapabilities {
    /** 已支持决策点。 */
    private final List<String> supportedPoints;
    /** 本次作用域的准确合同。 */
    private final List<DecisionSourceContracts.Contract> contracts;
    /** 所有已支持来源类型。 */
    private final List<VariableSource> sources;
    /** 允许运营创建的来源类型。 */
    private final List<VariableSource> creatableSources;
    /** 已支持标量类型。 */
    private final List<DecisionValueType> valueTypes;
    /** 已支持通用格式。 */
    private final List<VariableFormat> formats;
    /** 已支持数值量纲。 */
    private final List<NumericUnit> numericUnits;
    /** 集合成员数量上限。 */
    private final int memberLimit;
    /** 整批成员请求字节上限。 */
    private final int memberBodyBytes;
    /** 规则内联集合元素上限。 */
    private final int literalSetLimit;
}
