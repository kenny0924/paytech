package com.paytech.risk.decision.domain.variable;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter;
import static com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter.*;
import static com.paytech.risk.decision.domainservice.policy.VariableManagementPolicy.*;
import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新变量结构与已锁定依赖的完整上下文。 */
@Getter
@AllArgsConstructor
public class CreateVariableContext {
    /** 本次实际创建或锁定的变量根。 */
    private final RiskVariableDefinition root;

    /** 共享锁定的真实依赖根。 */
    private final Map<String, RiskVariableDefinition> dependencies;

    /** 关联选项集合的当前读成员。 */
    private final List<RiskVariableMember> optionMembers;


    /** 新身份仅允许人工来源和非墓碑状态。 */
    public void verifyIntegrity() {
        root.verifyIntegrity();
        if (root.systemManagedFlag() || root.sourceType() == VariableSource.NODE_OUTPUT)
            throw failure(SYSTEM_VARIABLE_READ_ONLY, "系统定义和节点输出只能由工程合同登记");
        if (root.status() == VariableStatus.DELETED) throw invalid("创建状态只能为 ACTIVE 或 DISABLED");
        verifyDependencies(entry(root).currentValue());
    }

    /** 关联结构及选项范围只使用已加载事实，不在校验中访问数据库。 */
    public void verifyDependencies(Object value) {
        var definition = entry(root).definition();
        var entries = dependencies.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey, e -> entry(e.getValue())));
        VariableDefinitionValidator.dependencies(definition, entries);
        if (value != null && definition.allowedValuesVariableCode() != null) {
            String key = VariableValueNormalizer.key(definition.valueType(), value);
            if (optionMembers.stream().map(row -> member(definition.valueType(), row))
                .noneMatch(m -> key.equals(VariableValueNormalizer.key(definition.valueType(), m.value()))))
                throw failure(VARIABLE_DOMAIN_MISMATCH, "当前值不属于关联的可选集合");
        }
    }

    /** 从原根与预加载成员生成响应，成功保存后不重新查询根。 */
    public VariableDetailResponse result() {
        var options = dependencies.get(root.allowedValuesVariableCode());
        AllowedValues allowed = options == null ? null : new AllowedValues(options.variableCode(), options.variableName(),
            options.version(), optionMembers.size(), optionMembers.stream().limit(100)
                .map(row -> member(options.valueType(), row)).toList());
        return new VariableDetailResponse(new View(entry(root), root.sourceType() == VariableSource.COLLECTION ? 0L : null,
            List.of(), allowed), root.updateTime());
    }
}
