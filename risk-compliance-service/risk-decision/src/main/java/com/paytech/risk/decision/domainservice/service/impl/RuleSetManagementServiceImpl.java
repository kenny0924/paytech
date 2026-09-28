package com.paytech.risk.decision.domainservice.service.impl;

import org.springframework.stereotype.Service;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.rule.CreateRuleSetVersionContext;
import com.paytech.risk.decision.domain.rule.DeleteRuleSetContext;
import com.paytech.risk.decision.domain.rule.FreezeRuleSetVersionContext;
import com.paytech.risk.decision.domainservice.repository.DecisionPointVersionRepository;
import com.paytech.risk.decision.domainservice.repository.RiskRuleRepository;
import com.paytech.risk.decision.domainservice.repository.RuleActionParameterRepository;
import com.paytech.risk.decision.domainservice.repository.RuleActionRepository;
import com.paytech.risk.decision.domainservice.repository.RuleNodeRepository;
import com.paytech.risk.decision.domainservice.repository.RuleOperandRepository;
import com.paytech.risk.decision.domainservice.repository.RuleSetRepository;
import com.paytech.risk.decision.domainservice.repository.RuleSetVersionRepository;
import com.paytech.risk.decision.domainservice.service.RuleSetManagementService;
import lombok.AllArgsConstructor;

/** 只组合单表 Repository 的本地写入；上下文原对象贯穿保存和响应。 */
@Service
@AllArgsConstructor
public class RuleSetManagementServiceImpl implements RuleSetManagementService {
    private final RuleSetRepository roots;
    private final RiskRuleRepository rules;
    private final RuleNodeRepository nodes;
    private final RuleActionRepository actions;
    private final RuleOperandRepository operands;
    private final RuleSetVersionRepository versions;
    private final RuleActionParameterRepository parameters;
    private final DecisionPointVersionRepository pointVersions;

    /** 新版本和管理指针只能在同一事务提交。 */
    @Override
    public void create(CreateRuleSetVersionContext context) {
        var version = context.getRuleSetVersion();
        versions.insert(version);
        rules.insertAll(context.getRules());
        nodes.insertAll(context.getNodes());
        operands.insertAll(context.getOperands());
        actions.insertAll(context.getRuleActions());
        parameters.insertAll(context.getRuleActionParameters());
        if (context.getPreviousVersion() == null) roots.insertRuleSet(context.getRuleSet());
        else roots.advanceRuleSet(context.getRuleSet(), version.ruleSetVersionId(), version.createTime());
        if (context.getPreviousVersion() != null && context.getPreviousVersion().status() == RuleSetVersionStatus.DRAFT) {
            versions.retire(context.getPreviousVersion(), version.createTime());
            if (context.getPreviousCompanion() != null) pointVersions.retire(context.getPreviousCompanion(), version.createTime());
        }
    }

    /** 按规则版本、旧协议、管理根顺序完成本地 CAS。 */
    @Override
    public void freeze(FreezeRuleSetVersionContext context) {
        versions.freeze(context.getRuleSetVersion(), context.getActorAccountId(), context.getCommandTime());
        if (context.getCompanion() != null) pointVersions.freeze(context.getCompanion(), context.getCommandTime());
        roots.advanceRuleSet(context.getRuleSet(), context.getRuleSetVersion().ruleSetVersionId(), context.getCommandTime());
    }

    /** 删除不改变任何历史内容或规则版本状态。 */
    @Override
    public void delete(DeleteRuleSetContext context) {
        roots.deleteRuleSet(context.getRuleSet(), context.getActorAccountId(), context.getRuleSet().deletionTriggerId(),
            context.getCommandTime());
    }
}
