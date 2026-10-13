package com.paytech.risk.decision.service.processor;

import static com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter.row;
import static com.paytech.risk.decision.domainservice.policy.VariableManagementPolicy.*;
import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.SYSTEM_VARIABLE_READ_ONLY;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.decision.domain.variable.CreateVariableContext;
import com.paytech.risk.decision.domain.variable.RiskVariable.Create;
import com.paytech.risk.decision.domain.variable.RiskVariable.Entry;
import com.paytech.risk.decision.domain.variable.VariableDefinitionValidator;
import com.paytech.risk.decision.domain.variable.VariableDetailResponse;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import com.paytech.risk.decision.domainservice.service.VariableManagementService;
import com.paytech.risk.decision.service.processor.request.CreateVariableRequest;

import lombok.AllArgsConstructor;

/**
 * 创建运营维护的变量
 * <li>变量资源锁覆盖本地事务；依赖根需在同一事务内共享锁定，防止保存前被停用或删除，因此 Context 在事务内构造。</li>
 * <li>编码唯一性由数据库唯一索引最终保护，冲突整体回滚。</li>
 */
@Component
@AllArgsConstructor
public class CreateVariableProcessor extends AbstractLockProcessor<CreateVariableRequest, VariableDetailResponse> {
    private final Clock clock;
    private final RiskVariableReader reader;
    private final VariableManagementService service;
    private final DecisionIdentifierGenerator identifiers;
    private final PlatformTransactionManager transactionManager;

    /** 定义结构、来源和初始状态只依赖输入，在加锁前一次校验。 */
    @Override
    protected void validate(CreateVariableRequest request) {
        Create command = request.getCommand();
        if (command.definition() == null) {
            throw invalid("变量定义不能为空");
        }
        VariableDefinitionValidator.validate(command.definition());
        if (command.definition().systemManaged() || command.definition().sourceType() == VariableSource.NODE_OUTPUT) {
            throw failure(SYSTEM_VARIABLE_READ_ONLY, "系统定义和节点输出只能由工程合同登记");
        }
        if (command.status() == null || command.status() == VariableStatus.DELETED) {
            throw invalid("创建状态只能为 ACTIVE 或 DISABLED");
        }
    }

    /** 与同一变量的其他写入口共用同一资源锁，等待超时返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(CreateVariableRequest request) {
        return LockCommand.tryLocks(
            DecisionResourceLocks.variableKeys(request.getCommand().definition().variableCode()),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    @Override
    protected VariableDetailResponse processWithinLock(CreateVariableRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        CreateVariableContext context = transaction.execute(status -> {
            var prepared = buildContext(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.create(prepared);
            return prepared;
        });
        return context.result();
    }

    /** 锁定依赖并一次构造完整 Context。 */
    CreateVariableContext buildContext(CreateVariableRequest request, Instant time) {
        Create command = request.getCommand();
        var lockedDependencies = reader.lockRoots(dependencies(command.definition()));
        var root = row(identifiers.nextIdentifier(), new Entry(command.definition(), text(command.variableName(), 255, false),
            text(command.description(), 500, true), currentValue(command.definition(), command.currentValue()), command.status(),
            1, request.getActorId(), request.getActorId(), time, time));
        var context =
            new CreateVariableContext(root, lockedDependencies, reader.optionMembers(root, lockedDependencies));
        context.verifyIntegrity();
        return context;
    }
}
