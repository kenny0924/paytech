package com.paytech.risk.decision.domain.variable;

import com.paytech.risk.decision.domain.enums.*;
import java.util.List;
import java.util.Set;

/** 已支持的取数合同形状，不包含变量编码、显示名或可选值；运营别名由数据库登记。 */
public final class DecisionSourceContracts {
    private static final Set<VariableUsage> CONDITIONS = Set.of(VariableUsage.RULE_CONDITION, VariableUsage.FLOW_CONDITION);
    private static final List<Contract> CONTRACTS = List.of(
        new Contract("ORDER_PREPARATION", 4, "CHECKOUT_PREPARE", VariableSource.INPUT, null, List.of(
            input("country_code", DecisionValueType.STRING, null, null, null),
            input("locale", DecisionValueType.STRING, null, null, null),
            input("industry_code", DecisionValueType.STRING, null, null, null),
            input("mcc_code", DecisionValueType.STRING, null, null, null),
            input("currency", DecisionValueType.STRING, null, null, "CURRENCY"),
            input("merchandise_amount", DecisionValueType.NUMBER, NumericUnit.MONEY, "currency", null),
            input("discount_amount", DecisionValueType.NUMBER, NumericUnit.MONEY, "currency", null),
            input("net_amount", DecisionValueType.NUMBER, NumericUnit.MONEY, "currency", null),
            input("entity_source_type", DecisionValueType.STRING, null, null, null),
            new Field("client_id", DecisionValueType.STRING, true, DataClassification.INTERNAL,
                Set.of(VariableUsage.ACTION_PARAMETER), null, null, "CLIENT_ID")
        )),
        new Contract("RULE_SET_NODE_RESULT", 1, "CHECKOUT_PREPARE", VariableSource.NODE_OUTPUT, "RULE_SET", List.of(
            new Field("matched", DecisionValueType.BOOLEAN, true, DataClassification.INTERNAL,
                Set.of(VariableUsage.FLOW_CONDITION), null, null, null),
            new Field("decision", DecisionValueType.STRING, false, DataClassification.INTERNAL,
                Set.of(VariableUsage.FLOW_CONDITION), null, null, null)
        ))
    );

    /** 工程合同不可变且不依赖外部状态。 */
    private DecisionSourceContracts() { }

    /** 只返回已经支持的精确合同，不能作为变量候选目录使用。 */
    public static List<Contract> all() { return CONTRACTS; }

    /** 找到准确来源合同，缺失不回退到最新版本。 */
    public static Contract find(RiskVariable.Definition definition) {
        return CONTRACTS.stream().filter(c -> c.pointCode().equals(definition.pointCode())
            && c.sourceType() == definition.sourceType() && c.contractCode().equals(definition.sourceContractCode())
            && Integer.valueOf(c.contractVersion()).equals(definition.sourceContractVersion())).findFirst().orElse(null);
    }

    /** 判断决策点是否有已支持输入能力。 */
    public static boolean supports(String pointCode) {
        return CONTRACTS.stream().anyMatch(c -> c.pointCode().equals(pointCode));
    }

    /** 构造真实输入字段的通用条件用途上限。 */
    static Field input(String key, DecisionValueType type, NumericUnit unit, String currencyField, String role) {
        return new Field(key, type, true, DataClassification.INTERNAL, CONDITIONS, unit, currencyField, role);
    }

    /** 合同字段只描述来源能提供的事实，不携带变量管理值。 */
    public record Field(String fieldKey, DecisionValueType valueType, boolean required, DataClassification dataClassification,
        Set<VariableUsage> allowedUsages, NumericUnit numericUnit, String currencyFieldKey, String semanticRole) { }

    /** 当前节点结果合同只是配置边界，本期不构造或执行结果。 */
    public record Contract(String contractCode, int contractVersion, String pointCode, VariableSource sourceType,
        String nodeType, List<Field> fields) { }
}
