package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Deleted;
import com.paytech.risk.decision.domain.flow.DeleteFlowContext;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRootRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowVersionRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionFlowManagementService;
import com.paytech.risk.decision.service.processor.request.DeleteFlowRequest;

import lombok.AllArgsConstructor;

/** 按原流程身份完成整体删除，保留永久编码及全部不可变历史。 */
@Component
@AllArgsConstructor
public class DeleteFlowProcessor extends AbstractLockProcessor<DeleteFlowRequest, Deleted> {
    private final Clock clock;
    private final DecisionRuleSetRepository rules;
    private final DecisionFlowRootRepository roots;
    private final PlatformTransactionManager transactionManager;
    private final DecisionFlowManagementService service;
    private final DecisionFlowVersionRepository versions;

    /** 锁前解析不可变业务坐标，与保存共用完整逻辑 Key 和有限租约。 */
    @Override
    protected LockCommand lockCommand(DeleteFlowRequest request) {
        DecisionFlowRow root =
            roots.findById(request.getFlowId()).orElseThrow(DecisionConfigurationNotFoundException::new);
        DecisionPoint point = rules.findPoint(root.decisionPointId()).orElseThrow(() -> new ErrorException("流程决策点缺失"));
        return LockCommand.tryLocks(List.of("decision:flow:" + point.pointCode() + ":" + root.flowCode()),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    /** 锁内按原主键重新读取并重证头版本，删除结果来自同一根对象。 */
    @Override
    protected Deleted processWithinLock(DeleteFlowRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        return transaction.execute(status -> {
            DecisionFlowRow root =
                roots.lockById(request.getFlowId()).orElseThrow(DecisionConfigurationNotFoundException::new);
            DecisionFlowVersionRow head =
                versions.findById(root.currentFlowVersionId()).orElseThrow(() -> new ErrorException("流程当前版本缺失"));
            root.verifyHead(head);
            root.verifyDeleteVersion(request.getExpectedVersion());
            var context = new DeleteFlowContext(root, request.getActorAccountId(),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.delete(context);
            return context.result();
        });
    }
}
