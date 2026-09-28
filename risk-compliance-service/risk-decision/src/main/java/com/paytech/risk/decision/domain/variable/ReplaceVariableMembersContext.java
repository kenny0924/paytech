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

/** 变量根本地写入的完整上下文。 */
@Getter
@AllArgsConstructor
public class ReplaceVariableMembersContext {
    /** 本次实际创建或锁定的变量根。 */
    private final RiskVariableDefinition root;

    /** 已规范化的完整新成员批次。 */
    private final List<RiskVariableMember> members;

    /** 可信操作者。 */
    private final long actorId;

    /** UTC 微秒命令时间。 */
    private final Instant commandTime;


    /** 版本和系统身份验证不触发任何外部读取。 */
    public void verifyIntegrity(long expectedVersion) {
        root.verifyMutable(expectedVersion);
        root.verifyCollection();
        root.verifyOperatorManaged();
    }

    /** 仅生成确实变化的写入字段，不复制根身份与版本。 */
    public VariableChange change() {
        return new VariableChange(root.variableName(), root.description(), typed(root.valueType(), entry(root).currentValue()), root.status());
    }

    /** 成功后投影已经原位更新的根，事务失败时不会生成结果。 */
    public Entry result() { return entry(root); }
}
