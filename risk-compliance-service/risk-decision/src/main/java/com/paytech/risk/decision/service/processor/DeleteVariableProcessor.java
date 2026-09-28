package com.paytech.risk.decision.service.processor;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.variable.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import com.paytech.risk.decision.domainservice.repository.RiskVariableMemberRepository;
import com.paytech.risk.decision.domainservice.service.*;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.service.processor.request.*;
import static com.paytech.risk.decision.domainservice.policy.VariableManagementPolicy.*;
import static com.paytech.risk.decision.domainservice.policy.VariableQueryPolicy.pattern;
import static com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter.*;
import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.*;
import com.paytech.risk.decision.domainservice.policy.VariableQueryPolicy;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;

/** 变量资源锁覆盖本地事务，资格与关系由 Owner 在锁内重新确认。 */
@Component
@AllArgsConstructor
public class DeleteVariableProcessor extends AbstractLockProcessor<DeleteVariableRequest, Entry> {
    private final Clock clock;
    private final RiskVariableReader reader;
    private final VariableManagementService service;
    private final PlatformTransactionManager transactionManager;
    private final RedisResourceLockProperties lockProperties;
    private final RiskVariableReferenceService references;

    /** 保留完整 decision:variable 锁键、RLock 及现有等待与租约配置。 */
    @Override protected LockCommand lockCommand(DeleteVariableRequest request) {
        return LockCommand.tryLocks(List.of("decision:variable:" + request.getVariableCode()),
            Duration.ofNanos(lockProperties.requiredWaitNanos()), Duration.ofNanos(lockProperties.requiredLeaseNanos()));
    }

    /** 本地保存成功才从原 Context 生成响应，失败不推进其他步骤。 */
    @Override protected Entry processWithinLock(DeleteVariableRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        DeleteVariableContext context = transaction.execute(status -> {
            var prepared = prepareContext(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.delete(prepared);
            return prepared;
        });
        return context.result();
    }

    /** 读取事实、验证原输入后一次构造完整 Context。 */
    DeleteVariableContext prepareContext(DeleteVariableRequest request, Instant time) {
        // 首次数据库操作必须是目标根 FOR UPDATE，等待结束后才建立引用读取快照。
        var root = reader.requiredRoot(request.getVariableCode(), "WRITE");
        root.verifyMutable(request.getExpectedVersion());
        root.verifyOperatorManaged();
        if (references.hasReferences(root.variableCode())) throw failure(VARIABLE_IN_USE, "变量存在当前或历史引用，不能删除");
        var context = new DeleteVariableContext(root, request.getActorId(), time);
        context.verifyIntegrity(request.getExpectedVersion());
        return context;
    }
}
