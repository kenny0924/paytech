package com.paytech.risk.decision.service.processor;

import static com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter.entry;
import static com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter.memberRow;
import static com.paytech.risk.decision.domainservice.policy.VariableManagementPolicy.normalizeMembers;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.domain.variable.ReplaceVariableMembersContext;
import com.paytech.risk.decision.domain.variable.RiskVariable.Entry;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import com.paytech.risk.decision.domainservice.service.VariableManagementService;
import com.paytech.risk.decision.service.processor.request.ReplaceVariableMembersRequest;

import lombok.AllArgsConstructor;

/**
 * 在根版本保护下整批替换集合成员
 * <li>变量资源锁覆盖本地事务；根以 FOR UPDATE 读取，根 CAS 与成员替换同一事务提交，任一失败不留下半批数据。</li>
 */
@Component
@AllArgsConstructor
public class ReplaceVariableMembersProcessor extends AbstractLockProcessor<ReplaceVariableMembersRequest, Entry> {
    private final Clock clock;
    private final RiskVariableReader reader;
    private final VariableManagementService service;
    private final DecisionIdentifierGenerator identifiers;
    private final PlatformTransactionManager transactionManager;

    /** 与同一变量的其他写入口共用同一资源锁，等待超时返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(ReplaceVariableMembersRequest request) {
        return LockCommand.tryLocks(DecisionResourceLocks.variableKeys(request.getVariableCode()), DecisionResourceLocks.WAIT_TIME,
            DecisionResourceLocks.LEASE_TIME);
    }

    @Override
    protected Entry processWithinLock(ReplaceVariableMembersRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        ReplaceVariableMembersContext context = transaction.execute(status -> {
            var prepared = buildContext(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.replaceMembers(prepared);
            return prepared;
        });
        return context.result();
    }

    /** 锁定根、按成员类型规范化整批输入后一次构造完整 Context。 */
    ReplaceVariableMembersContext buildContext(ReplaceVariableMembersRequest request, Instant time) {
        var root = reader.requiredRoot(request.getVariableCode(), "WRITE");
        root.verifyMutable(request.getExpectedVersion());
        root.verifyCollection();
        root.verifyOperatorManaged();
        var definition = entry(root).definition();
        var rows = normalizeMembers(definition, request.getMembers()).stream()
            .map(member -> memberRow(identifiers.nextIdentifier(), definition, member, time)).toList();
        var context = new ReplaceVariableMembersContext(root, rows, request.getActorId(), time);
        context.verifyIntegrity(request.getExpectedVersion());
        return context;
    }
}
