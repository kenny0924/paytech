package com.paytech.risk.decision.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.risk.decision.domain.release.CreateTestReleaseContext;
import com.paytech.risk.decision.domain.release.ValidateTestReleaseContext;
import com.paytech.risk.decision.domainservice.repository.DecisionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.ReleaseAuditEventRepository;
import com.paytech.risk.decision.domainservice.service.DecisionReleaseService;

import lombok.AllArgsConstructor;

/** 仅保存 Context 中已核验的本地事实，各阶段参与 Processor 的同一个短事务。 */
@Service
@AllArgsConstructor
public class DecisionReleaseServiceImpl implements DecisionReleaseService {
    private final DecisionReleaseRepository releases;
    private final ReleaseAuditEventRepository audits;

    /** 根与初始事件必须一起成功。 */
    @Override
    public void create(CreateTestReleaseContext context) {
        releases.insert(context.getRelease());
        audits.insert(context.getInitialStateChange());
    }

    /** 根 CAS 成功后追加开始事件；失败时整体回滚。 */
    @Override
    public void startValidation(ValidateTestReleaseContext context) {
        releases.startValidation(context.getRelease(), context.getStarted().eventTime());
        audits.insert(context.getStarted());
    }

    /** 八项检查与拒绝事件在同一事务保存，通过时保持 VALIDATING/version=2。 */
    @Override
    public void completeValidation(ValidateTestReleaseContext context) {
        audits.insertChecks(context.getChecks());
        if (context.getRejected() != null) {
            releases.rejectValidation(context.getRelease(), context.getRejected().eventTime());
            audits.insert(context.getRejected());
        }
    }
}
