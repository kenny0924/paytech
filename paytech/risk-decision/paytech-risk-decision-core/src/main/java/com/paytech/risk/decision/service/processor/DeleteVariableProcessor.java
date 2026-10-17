package com.paytech.risk.decision.service.processor;

import static com.paytech.risk.decision.domainservice.policy.VariableManagementPolicy.failure;
import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.VARIABLE_IN_USE;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.domain.variable.DeleteVariableContext;
import com.paytech.risk.decision.domain.variable.RiskVariable.Entry;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import com.paytech.risk.decision.domainservice.service.RiskVariableReferenceService;
import com.paytech.risk.decision.domainservice.service.VariableManagementService;
import com.paytech.risk.decision.service.processor.request.DeleteVariableRequest;

import lombok.AllArgsConstructor;

/**
 * 在无任何当前或历史引用时逻辑删除变量，保留墓碑与永久编码
 * <li>变量资源锁覆盖本地事务；事务内首个数据库操作必须是目标根 FOR UPDATE，之后才复核引用，避免与规则、流程保存竞争时漏判。</li>
 */
@Component
@AllArgsConstructor
public class DeleteVariableProcessor extends AbstractLockProcessor<DeleteVariableRequest, Entry> {
    private final Clock clock;
    private final RiskVariableReader reader;
    private final VariableManagementService service;
    private final RiskVariableReferenceService references;
    private final PlatformTransactionManager transactionManager;

    /** 与同一变量的其他写入口共用同一资源锁，等待超时返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(DeleteVariableRequest request) {
        return LockCommand.tryLocks(DecisionResourceLocks.variableKeys(request.getVariableCode()),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    @Override
    protected Entry processWithinLock(DeleteVariableRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        DeleteVariableContext context = transaction.execute(status -> {
            var prepared = buildContext(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.delete(prepared);
            return prepared;
        });
        return context.result();
    }

    /** 锁定根、复核全部版本引用后一次构造完整 Context。 */
    DeleteVariableContext buildContext(DeleteVariableRequest request, Instant time) {
        var root = reader.requiredRoot(request.getVariableCode(), "WRITE");
        root.verifyMutable(request.getExpectedVersion());
        root.verifyOperatorManaged();
        if (references.hasReferences(root.variableCode())) {
            throw failure(VARIABLE_IN_USE, "变量存在当前或历史引用，不能删除");
        }
        var context = new DeleteVariableContext(root, request.getActorId(), time);
        context.verifyIntegrity(request.getExpectedVersion());
        return context;
    }
}
