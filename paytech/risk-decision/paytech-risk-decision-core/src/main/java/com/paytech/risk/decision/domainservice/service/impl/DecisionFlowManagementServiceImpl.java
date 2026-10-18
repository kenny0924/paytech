package com.paytech.risk.decision.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.risk.decision.domain.flow.DeleteFlowContext;
import com.paytech.risk.decision.domain.flow.SaveFlowContext;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowEdgeRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowEndOutputRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowNodeRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRootRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowVersionRepository;
import com.paytech.risk.decision.domainservice.service.DecisionFlowManagementService;

import lombok.AllArgsConstructor;

/** 全图保存参加外层短事务，任一步失败均不留下半张图。 */
@Service
@AllArgsConstructor
public class DecisionFlowManagementServiceImpl implements DecisionFlowManagementService {
    private final DecisionFlowEdgeRepository edges;
    private final DecisionFlowEndOutputRepository outputs;
    private final DecisionFlowNodeRepository nodes;
    private final DecisionFlowRootRepository roots;
    private final DecisionFlowVersionRepository versions;

    /** 初次创建先保存完整身份；编辑最后以旧根 CAS 切换到新版本。 */
    @Override
    public void save(SaveFlowContext context) {
        if (context.isNewFlow()) { roots.insert(context.getRoot()); }
        versions.insert(context.getVersion());
        context.getNodes().forEach(nodes::insert);
        context.getEdges().forEach(edges::insert);
        context.getOutputs().forEach(outputs::insert);
        if (!context.isNewFlow()) { roots.advance(context.getRoot(), context.getVersion()); }
    }

    /** Repository 在数据库条件更新成功后同步 Context 中的原根。 */
    @Override
    public void delete(DeleteFlowContext context) {
        roots.delete(context.getRoot(), context.getActorAccountId(), context.getDeletedTime());
    }
}
