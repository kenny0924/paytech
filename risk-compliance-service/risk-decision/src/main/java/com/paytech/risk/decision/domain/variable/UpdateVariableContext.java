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

/** 变量编辑的原根、待写内容及关联事实。 */
@Getter
@AllArgsConstructor
public class UpdateVariableContext {
    /** 本次实际创建或锁定的变量根。 */
    private final RiskVariableDefinition root;

    /** 本次确认的可变字段。 */
    private final VariableChange change;

    /** 共享锁定的真实依赖根。 */
    private final Map<String, RiskVariableDefinition> dependencies;

    /** 关联选项集合的当前读成员。 */
    private final List<RiskVariableMember> optionMembers;

    /** 原集合成员计数；非集合为空。 */
    private final Long memberCount;

    /** 原集合的有限成员预览。 */
    private final List<RiskVariableMember> previewMembers;

    /** 可信操作者。 */
    private final long actorId;

    /** UTC 微秒命令时间。 */
    private final Instant commandTime;


    /** 最终版本和依赖判断都基于本次锁定的原始对象。 */
    public void verifyIntegrity(long expectedVersion, Object value) {
        root.verifyMutable(expectedVersion);
        root.verifyUpdate(value, change.getStatus());
        verifyDependencies(value);
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
        return new VariableDetailResponse(new View(entry(root), memberCount,
            previewMembers.stream().map(row -> member(root.valueType(), row)).toList(), allowed), root.updateTime());
    }
}
