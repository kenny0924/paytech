package com.paytech.risk.decision.service.processor;

import static com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter.entry;
import static com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter.typed;
import static com.paytech.risk.decision.domainservice.policy.VariableManagementPolicy.*;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.RiskVariableDefinition;
import com.paytech.risk.decision.core.dal.dataobject.RiskVariableMember;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.variable.RiskVariable.Update;
import com.paytech.risk.decision.domain.variable.UpdateVariableContext;
import com.paytech.risk.decision.domain.variable.VariableChange;
import com.paytech.risk.decision.domain.variable.VariableDetailResponse;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import com.paytech.risk.decision.domainservice.repository.RiskVariableMemberRepository;
import com.paytech.risk.decision.domainservice.service.VariableManagementService;
import com.paytech.risk.decision.service.processor.request.UpdateVariableRequest;

import lombok.AllArgsConstructor;

/**
 * 按根版本更新变量的名称、说明、常量当前值与状态
 * <li>变量资源锁覆盖本地事务；根以 FOR UPDATE 读取、依赖根共享锁定，因此 Context 在事务内构造。</li>
 * <li>版本冲突由 CAS 与更新行数最终确认。</li>
 */
@Component
@AllArgsConstructor
public class UpdateVariableProcessor extends AbstractLockProcessor<UpdateVariableRequest, VariableDetailResponse> {
    private final Clock clock;
    private final RiskVariableReader reader;
    private final RiskVariableMemberRepository members;
    private final VariableManagementService service;
    private final PlatformTransactionManager transactionManager;

    /** 与同一变量的其他写入口共用同一资源锁，等待超时返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(UpdateVariableRequest request) {
        return LockCommand.tryLocks(DecisionResourceLocks.variableKeys(request.getVariableCode()),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    @Override
    protected VariableDetailResponse processWithinLock(UpdateVariableRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        UpdateVariableContext context = transaction.execute(status -> {
            var prepared = buildContext(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.update(prepared);
            return prepared;
        });
        return context.result();
    }

    /** 锁定根与依赖、验证可变字段后一次构造完整 Context。 */
    UpdateVariableContext buildContext(UpdateVariableRequest request, Instant time) {
        Update command = request.getCommand();
        var root = reader.requiredRoot(request.getVariableCode(), "WRITE");
        root.verifyMutable(command.expectedVersion());
        var definition = entry(root).definition();
        Object value = currentValue(definition, command.currentValue());
        root.verifyUpdate(value, command.status());
        var lockedDependencies = reader.lockRoots(dependencies(definition));
        var change = new VariableChange(text(command.variableName(), 255, false), text(command.description(), 500, true),
            typed(definition.valueType(), value), command.status());
        var context =
            new UpdateVariableContext(root, change, lockedDependencies, reader.optionMembers(root, lockedDependencies),
                memberCount(root), previewMembers(root), request.getActorId(), time);
        context.verifyIntegrity(command.expectedVersion(), value);
        return context;
    }

    /** 集合返回当前成员总数，其他来源为空。 */
    Long memberCount(RiskVariableDefinition root) {
        if (root.sourceType() != VariableSource.COLLECTION) {
            return null;
        }
        return members.memberCount(root.variableCode(), null);
    }

    /** 集合返回最多 3 项成员预览，其他来源为空列表。 */
    List<RiskVariableMember> previewMembers(RiskVariableDefinition root) {
        if (root.sourceType() != VariableSource.COLLECTION) {
            return List.of();
        }
        return members.members(root.variableCode(), null, 3, 0, true);
    }
}
