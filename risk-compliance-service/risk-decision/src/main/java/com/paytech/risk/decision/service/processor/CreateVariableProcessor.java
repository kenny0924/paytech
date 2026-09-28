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
public class CreateVariableProcessor extends AbstractLockProcessor<CreateVariableRequest, VariableDetailResponse> {
    private final Clock clock;
    private final RiskVariableReader reader;
    private final VariableManagementService service;
    private final PlatformTransactionManager transactionManager;
    private final DecisionIdentifierGenerator identifiers;
    private final RedisResourceLockProperties lockProperties;

    /** 保留完整 decision:variable 锁键、RLock 及现有等待与租约配置。 */
    @Override protected LockCommand lockCommand(CreateVariableRequest request) {
        if (request.getCommand().definition() == null) throw invalid("变量定义不能为空");
        VariableDefinitionValidator.code(request.getCommand().definition().variableCode());
        return LockCommand.tryLocks(List.of("decision:variable:" + request.getCommand().definition().variableCode()),
            Duration.ofNanos(lockProperties.requiredWaitNanos()), Duration.ofNanos(lockProperties.requiredLeaseNanos()));
    }

    /** 本地保存成功才从原 Context 生成响应，失败不推进其他步骤。 */
    @Override protected VariableDetailResponse processWithinLock(CreateVariableRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        CreateVariableContext context = transaction.execute(status -> {
            var prepared = prepareContext(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.create(prepared);
            return prepared;
        });
        return context.result();
    }

    /** 读取事实、验证原输入后一次构造完整 Context。 */
    CreateVariableContext prepareContext(CreateVariableRequest request, Instant time) {
        Create command = request.getCommand();
        VariableDefinitionValidator.validate(command.definition());
        if (command.definition().systemManaged() || command.definition().sourceType() == VariableSource.NODE_OUTPUT)
            throw failure(SYSTEM_VARIABLE_READ_ONLY, "系统定义和节点输出只能由工程合同登记");
        if (command.status() == null || command.status() == VariableStatus.DELETED) throw invalid("创建状态只能为 ACTIVE 或 DISABLED");
        var dependencies = reader.lockRoots(dependencies(command.definition()));
        var root = row(identifiers.nextIdentifier(), new Entry(command.definition(), text(command.variableName(), 255, false),
            text(command.description(), 500, true), currentValue(command.definition(), command.currentValue()), command.status(),
            1, request.getActorId(), request.getActorId(), time, time));
        var context = new CreateVariableContext(root, dependencies, reader.optionMembers(root, dependencies));
        context.verifyIntegrity();
        return context;
    }
}
