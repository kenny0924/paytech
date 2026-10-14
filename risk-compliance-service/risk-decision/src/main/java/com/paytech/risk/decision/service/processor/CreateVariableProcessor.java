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
import com.paytech.risk.control.domain.list.RiskListView;
import com.paytech.risk.control.service.RiskListMembershipService;
import com.paytech.risk.decision.domain.feature.FeatureHandlers;
import com.paytech.risk.decision.domain.feature.FeatureRows;
import com.paytech.risk.decision.domain.feature.FeatureSpec;
import com.paytech.risk.decision.domain.variable.RiskVariable.Definition;
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
    private final RiskListMembershipService listService;
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
        validateFeature(command);
    }

    /** Feature 参数与来源一一对应：实时累计、名单命中各自必填，其他来源必须为空。 */
    void validateFeature(Create command) {
        Definition definition = command.definition();
        FeatureSpec feature = command.feature();
        if (definition.sourceType() == VariableSource.VELOCITY && feature instanceof FeatureSpec.Velocity velocity) {
            FeatureHandlers.validate(velocity, definition);
            return;
        }
        if (definition.sourceType() == VariableSource.LIST && feature instanceof FeatureSpec.ListMembership list) {
            FeatureHandlers.validate(list, definition);
            return;
        }
        if (definition.featureBacked() || feature != null) {
            throw invalid("Feature 参数与变量来源不匹配");
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
        RiskListView list = requireList(request.getCommand().feature());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        CreateVariableContext context = transaction.execute(status -> {
            var prepared = buildContext(request, list, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.create(prepared);
            return prepared;
        });
        return context.result();
    }

    /** 名单命中变量引用的名单必须存在、启用且主体类型一致；其他来源不查询。 */
    RiskListView requireList(FeatureSpec feature) {
        if (!(feature instanceof FeatureSpec.ListMembership spec)) {
            return null;
        }
        RiskListView list = listService.findListByCode(spec.listCode())
            .orElseThrow(() -> invalid("名单不存在: " + spec.listCode()));
        if (!list.active() || list.subjectType() != spec.subjectType()) {
            throw invalid("名单已停用或主体类型不一致: " + spec.listCode());
        }
        return list;
    }

    /** 锁定依赖并一次构造完整 Context；Feature 变量同时生成不可变 Feature Version 与参数。 */
    CreateVariableContext buildContext(CreateVariableRequest request, RiskListView list, Instant time) {
        Create command = request.getCommand();
        Definition definition = command.definition().featureBacked()
            ? command.definition().withFeatureVersion(identifiers.nextIdentifier()) : command.definition();
        var lockedDependencies = reader.lockRoots(dependencies(definition));
        var root = row(identifiers.nextIdentifier(), new Entry(definition, text(command.variableName(), 255, false),
            text(command.description(), 500, true), currentValue(command.definition(), command.currentValue()), command.status(),
            1, request.getActorId(), request.getActorId(), time, time));
        var feature = FeatureRows.build(definition, root.variableName(), command.feature(), list, time);
        var context = new CreateVariableContext(root, lockedDependencies, reader.optionMembers(root, lockedDependencies),
            feature);
        context.verifyIntegrity();
        return context;
    }
}
