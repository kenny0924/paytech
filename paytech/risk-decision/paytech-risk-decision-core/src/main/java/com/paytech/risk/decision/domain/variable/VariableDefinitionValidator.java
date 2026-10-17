package com.paytech.risk.decision.domain.variable;

import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.*;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import java.util.Map;
import java.util.Objects;

/** 纯结构和依赖投影校验；数据库读取和锁由变量 Service 提供。 */
public final class VariableDefinitionValidator {
    /** 无外部状态的纯规则入口。 */
    private VariableDefinitionValidator() { }

    /** 验证创建后不可变的映射形状；同一能力允许运营登记任意新编码别名。 */
    public static void validate(Definition definition) {
        code(definition.variableCode());
        if (definition.sourceType() == null || definition.valueType() == null || definition.formatCode() == null
            || definition.usages().isEmpty() && !(definition.systemManaged() && definition.sourceType() == VariableSource.COLLECTION)) { throw invalid("变量来源、类型、格式和用途必填"); }
        if (definition.pointCode() != null && !DecisionSourceContracts.supports(definition.pointCode())) {
            throw invalid("决策点尚未支持");
        }
        boolean string = definition.valueType() == DecisionValueType.STRING;
        if (string == (definition.formatCode() == VariableFormat.NONE)) { throw invalid("格式与值类型不相容"); }
        validateSource(definition);
        validateUnit(definition);
        if (definition.sourceType() == VariableSource.COLLECTION && definition.allowedValuesVariableCode() != null) {
            throw invalid("集合不能继续关联可选值集合");
        }
    }

    /** 验证准确输入或节点输出合同及最大用途，禁止任意字段路径和伪造来源。 */
    static void validateSource(Definition d) {
        if (d.sourceType() == VariableSource.INPUT || d.sourceType() == VariableSource.NODE_OUTPUT) {
            var contract = DecisionSourceContracts.find(d);
            if (contract == null) { throw invalid("来源合同未支持或版本不匹配"); }
            var field = contract.fields().stream().filter(f -> f.fieldKey().equals(d.sourceField())).findFirst().orElse(null);
            if (field == null || field.valueType() != d.valueType() || !field.allowedUsages().containsAll(d.usages())
                || field.numericUnit() != d.numericUnit()) { throw invalid("来源字段类型、量纲或用途超出合同"); }
            return;
        }
        if (d.sourceContractCode() != null || d.sourceContractVersion() != null || d.sourceField() != null
            || d.usages().contains(VariableUsage.ACTION_PARAMETER)) { throw invalid("常量和集合不允许来源映射或动作主体用途"); }
        if (d.featureBacked()) { validateFeatureSource(d); return; }
        if (d.featureVersionId() != null) { throw invalid("只有实时累计和名单命中变量关联 Feature Version"); }
    }

    /**
     * 实时累计与名单命中变量只服务于运行期计算 Feature 的决策点（支付尝试、结账），由 Feature 计算取值
     *
     * @param d 变量结构；Feature Version 在创建时由服务端分配，持久化后必须非空
     */
    static void validateFeatureSource(Definition d) {
        var descriptor = d.pointCode() == null ? null
            : com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor.find(d.pointCode()).orElse(null);
        if (descriptor == null || !descriptor.featureVariablesSupported()) { throw invalid("实时累计与名单命中变量必须属于支持 Feature 计算的决策点"); }
        if (d.allowedValuesVariableCode() != null || d.formatCode() != VariableFormat.NONE) { throw invalid("Feature 变量不关联可选值且格式为 NONE"); }
        if (d.sourceType() == VariableSource.LIST && d.valueType() != DecisionValueType.BOOLEAN) { throw invalid("名单命中变量必须为 BOOLEAN"); }
        if (d.sourceType() == VariableSource.VELOCITY && d.valueType() != DecisionValueType.NUMBER) { throw invalid("实时累计变量必须为 NUMBER"); }
    }

    /** NUMBER 必须明确量纲；金额的固定币种和币种变量恰好选择一种。 */
    static void validateUnit(Definition d) {
        if (d.valueType() != DecisionValueType.NUMBER) {
            if (d.numericUnit() != null || d.currencyCode() != null || d.currencyVariableCode() != null) {
                throw invalid("非 NUMBER 不能填写量纲或币种");
            }
            return;
        }
        if (d.numericUnit() == null) { throw invalid("NUMBER 必须指定量纲"); }
        if (d.numericUnit() == NumericUnit.SCALAR) {
            if (d.currencyCode() != null || d.currencyVariableCode() != null) { throw invalid("普通数值不能填写币种"); }
            return;
        }
        if ((d.currencyCode() == null) == (d.currencyVariableCode() == null)) { throw invalid("金额必须选择一种币种来源"); }
        if (d.currencyCode() != null && !d.currencyCode().matches("[A-Z]{3}")) { throw invalid("固定币种须为三位大写代码"); }
        if (d.currencyVariableCode() != null) { code(d.currencyVariableCode()); }
    }

    /** 当前依赖须启用，类型和适用范围覆盖消费者；输入金额必须绑定其真实币种字段。 */
    public static void dependencies(Definition d, Map<String, Entry> dependencies) {
        if (d.allowedValuesVariableCode() != null) {
            Entry options = required(dependencies, d.allowedValuesVariableCode());
            if (options.definition().sourceType() != VariableSource.COLLECTION || options.definition().valueType() != d.valueType()
                || !covers(options.definition().pointCode(), d.pointCode())) { throw invalid("可选值须为同类型且覆盖适用范围的集合"); }
        }
        if (d.currencyVariableCode() != null) { validateCurrency(d, required(dependencies, d.currencyVariableCode()).definition()); }
        if (d.sourceType() == VariableSource.INPUT && d.numericUnit() == NumericUnit.MONEY && d.currencyVariableCode() == null) {
            throw invalid("输入金额必须绑定同一合同提供的币种变量");
        }
    }

    /** 币种别名按准确合同字段匹配，不按编码或展示名猜测。 */
    static void validateCurrency(Definition d, Definition currency) {
        var contract = DecisionSourceContracts.find(currency);
        var field = contract == null ? null : contract.fields().stream().filter(f -> f.fieldKey().equals(currency.sourceField())).findFirst().orElse(null);
        if (currency.sourceType() != VariableSource.INPUT || currency.valueType() != DecisionValueType.STRING
            || currency.formatCode() != VariableFormat.UPPER_ALPHA_3 || !Objects.equals(d.pointCode(), currency.pointCode())
            || field == null || !"CURRENCY".equals(field.semanticRole())) { throw invalid("币种来源须为同决策点的真实币种输入"); }
        if (d.sourceType() == VariableSource.INPUT) {
            var source = DecisionSourceContracts.find(d);
            var amount = source.fields().stream().filter(f -> f.fieldKey().equals(d.sourceField())).findFirst().orElseThrow();
            if (!source.contractCode().equals(currency.sourceContractCode()) || source.contractVersion() != currency.sourceContractVersion()
                || !Objects.equals(amount.currencyFieldKey(), currency.sourceField())) { throw invalid("输入金额和币种必须来自同一准确合同"); }
        }
    }

    /** 来源的全局范围或准确相同点可以覆盖消费者。 */
    public static boolean covers(String sourcePoint, String consumerPoint) {
        return sourcePoint == null || sourcePoint.equals(consumerPoint);
    }

    /** 依赖缺失或停用禁止新的保存，旧结构的展示另走历史投影。 */
    static Entry required(Map<String, Entry> dependencies, String code) {
        code(code);
        Entry entry = dependencies.get(code);
        if (entry == null) { throw new RiskVariableException(VARIABLE_NOT_FOUND, "依赖变量不存在：" + code); }
        if (entry.status() != VariableStatus.ACTIVE) { throw new RiskVariableException(VARIABLE_DISABLED, "依赖变量未启用：" + code); }
        return entry;
    }

    /** 编码只允许规范大写标识，不接受别名及自由文本。 */
    public static void code(String code) {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")) { throw invalid("变量编码格式无效"); }
    }

    /** 构造没有敏感值的结构校验结果。 */
    static RiskVariableException invalid(String message) { return new RiskVariableException(INVALID_VARIABLE_DEFINITION, message); }
}
