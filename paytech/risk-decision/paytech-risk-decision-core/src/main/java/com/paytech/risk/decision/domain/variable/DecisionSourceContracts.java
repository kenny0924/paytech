package com.paytech.risk.decision.domain.variable;

import com.paytech.risk.decision.domain.enums.*;
import java.util.List;
import java.util.Set;

/** 已支持的取数合同形状，不包含变量编码、显示名或可选值；运营别名由数据库登记。 */
public final class DecisionSourceContracts {
    private static final Set<VariableUsage> CONDITIONS = Set.of(VariableUsage.RULE_CONDITION, VariableUsage.FLOW_CONDITION);
    private static final List<Contract> CONTRACTS = List.of(
        // 结账旧合同只供已有变量、规则集与流程读取：不在目录中出现，也不能新建变量。
        new Contract("ORDER_PREPARATION", 4, "CHECKOUT_PREPARE", VariableSource.INPUT, null, true, List.of(
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
        // 结账当前合同：Commerce 创建结账、订单落库前已有的订单与商业事实；client_id 保留动作参数用途以便旧配置规则集可读。
        new Contract("ORDER_PREPARATION", 5, "CHECKOUT_PREPARE", VariableSource.INPUT, null, false, List.of(
            new Field("client_id", DecisionValueType.STRING, true, DataClassification.INTERNAL,
                Set.of(VariableUsage.RULE_CONDITION, VariableUsage.FLOW_CONDITION, VariableUsage.ACTION_PARAMETER), null,
                null, "CLIENT_ID"),
            input("entity_no", DecisionValueType.STRING, null, null, null),
            input("entity_source_type", DecisionValueType.STRING, null, null, null),
            optional("local_partner_id"),
            input("consumer_country_code", DecisionValueType.STRING, null, null, null),
            input("currency", DecisionValueType.STRING, null, null, "CURRENCY"),
            input("merchandise_amount", DecisionValueType.NUMBER, NumericUnit.MONEY, "currency", null),
            input("discount_amount", DecisionValueType.NUMBER, NumericUnit.MONEY, "currency", null),
            input("net_amount", DecisionValueType.NUMBER, NumericUnit.MONEY, "currency", null),
            input("industry_code", DecisionValueType.STRING, null, null, null),
            input("mcc_code", DecisionValueType.STRING, null, null, null),
            input("line_count", DecisionValueType.NUMBER, NumericUnit.SCALAR, null, null)
        )),
        nodeResult("CHECKOUT_PREPARE"),
        // 支付尝试评估的可配置输入；IP、卡指纹与 User-Agent 只供引擎计算累计与名单，不开放给规则字面量。
        new Contract("PAYMENT_ATTEMPT", 1, "PAYMENT_ATTEMPT_RISK", VariableSource.INPUT, null, false, List.of(
            new Field("client_id", DecisionValueType.STRING, true, DataClassification.INTERNAL, CONDITIONS, null, null,
                "CLIENT_ID"),
            input("entity_no", DecisionValueType.STRING, null, null, null),
            input("payment_method_code", DecisionValueType.STRING, null, null, null),
            input("capture_mode", DecisionValueType.STRING, null, null, null),
            input("amount", DecisionValueType.NUMBER, NumericUnit.MONEY, "currency", null),
            input("currency", DecisionValueType.STRING, null, null, "CURRENCY"),
            input("industry_code", DecisionValueType.STRING, null, null, null),
            input("mcc_code", DecisionValueType.STRING, null, null, null),
            optional("consumer_country_code"),
            optional("card_bin")
        )),
        nodeResult("PAYMENT_ATTEMPT_RISK")
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

    /** 可选的字符串输入，支付方未提供时为空。 */
    static Field optional(String key) {
        return new Field(key, DecisionValueType.STRING, false, DataClassification.INTERNAL, CONDITIONS, null, null, null);
    }

    /** 规则集节点结果合同；每个支持流程的决策点各有一份。 */
    static Contract nodeResult(String pointCode) {
        return new Contract("RULE_SET_NODE_RESULT", 1, pointCode, VariableSource.NODE_OUTPUT, "RULE_SET", false, List.of(
            new Field("matched", DecisionValueType.BOOLEAN, true, DataClassification.INTERNAL,
                Set.of(VariableUsage.FLOW_CONDITION), null, null, null),
            new Field("decision", DecisionValueType.STRING, false, DataClassification.INTERNAL,
                Set.of(VariableUsage.FLOW_CONDITION), null, null, null)));
    }

    /** 合同字段只描述来源能提供的事实，不携带变量管理值。 */
    public record Field(String fieldKey, DecisionValueType valueType, boolean required, DataClassification dataClassification,
        Set<VariableUsage> allowedUsages, NumericUnit numericUnit, String currencyFieldKey, String semanticRole) { }

    /**
     * 一份准确的取数合同
     * <li>旧合同（legacy）只用于读取已有变量与配置，不进入创作目录，不能新建变量。</li>
     */
    public record Contract(String contractCode, int contractVersion, String pointCode, VariableSource sourceType,
        String nodeType, boolean legacy, List<Field> fields) { }
}
