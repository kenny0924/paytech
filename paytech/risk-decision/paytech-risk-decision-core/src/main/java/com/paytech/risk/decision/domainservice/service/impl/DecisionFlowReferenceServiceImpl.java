package com.paytech.risk.decision.domainservice.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.RuleRejectReason;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Graph;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Issue;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Node;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Reference;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRepository;
import com.paytech.risk.decision.domainservice.service.DecisionFlowReferenceService;

import lombok.AllArgsConstructor;

/** 保留的历史 tuple 与新选择分别判断，不自动升级引用，也不改写历史文案。 */
@Service
@AllArgsConstructor
public class DecisionFlowReferenceServiceImpl implements DecisionFlowReferenceService {
    private final DecisionFlowRepository repository;

    /** 保存调用方已持有流程根锁，依次锁定本地引用后执行相同资格规则。 */
    @Override
    public List<Issue> validateForSave(long pointId, String pointCode, Graph graph, Graph base) {
        return validate(pointId, pointCode, graph, base, true);
    }

    /** 静态检查仅查询当前资格，不预留引用或版本。 */
    @Override
    public List<Issue> validateForQuery(long pointId, String pointCode, Graph graph, Graph base) {
        return validate(pointId, pointCode, graph, base, false);
    }

    /** 共享保留项判定，读取策略由两个明确入口选择。 */
    private List<Issue> validate(long pointId, String pointCode, Graph graph, Graph base, boolean lock) {
        Map<String, Node> previous = new HashMap<>();
        if (base != null) base.nodes().forEach(node -> previous.put(node.nodeKey(), node));
        List<Long> versionIds = graph.nodes().stream().filter(n -> "RULE_SET".equals(n.type()))
            .map(Node::ruleSetVersionId).distinct().sorted().toList();
        Map<Long, RuleSet> rootsByVersion = new LinkedHashMap<>();
        for (long id : versionIds) repository.ruleRoot(id).ifPresent(root -> rootsByVersion.put(id, root));
        if (lock) {
            Map<Long, RuleSet> locked = new HashMap<>();
            rootsByVersion.values().stream().map(RuleSet::getRuleSetId).distinct().sorted()
                .forEach(id -> locked.put(id, repository.shareRuleRoot(id)));
            rootsByVersion.replaceAll((id, root) -> locked.get(root.getRuleSetId()));
        }
        Map<Long, RuleSetVersion> versions = new HashMap<>();
        for (long id : versionIds) repository.ruleVersion(id, lock).ifPresent(v -> versions.put(id, v));
        // 共享锁顺序固定为规则根、准确版本、原因编码，与现有删除/退休及字典写入兼容。
        Map<String, RuleRejectReason> reasons = new HashMap<>();
        graph.nodes().stream().map(Node::reasonCode).filter(Objects::nonNull).distinct().sorted()
            .forEach(code -> repository.reason(code, lock).ifPresent(reason -> reasons.put(code, reason)));
        var issues = new ArrayList<Issue>();
        var contract = DecisionPointAuthoringDescriptor.require(pointCode).point();
        for (int index = 0; index < graph.nodes().size(); index++) {
            Node node = graph.nodes().get(index);
            Node old = previous.get(node.nodeKey());
            if ("RULE_SET".equals(node.type())) {
                RuleSet root = rootsByVersion.get(node.ruleSetVersionId());
                RuleSetVersion version = versions.get(node.ruleSetVersionId());
                boolean retained = old != null && "RULE_SET".equals(old.type())
                    && Objects.equals(old.ruleSetVersionId(), node.ruleSetVersionId());
                boolean usable = root != null && version != null && root.getStatus() == RuleSetManagementStatus.CONFIGURABLE
                    && root.getDecisionPointId() == pointId && version.decisionPointId() == pointId
                    && root.getRuleSetCode().equals(version.ruleSetCode())
                    && (version.status() == RuleSetVersionStatus.DRAFT || version.status() == RuleSetVersionStatus.FROZEN
                        || (retained && version.status() == RuleSetVersionStatus.RETIRED))
                    && sameContract(version, pointId, contract.inputContractCode(), contract.inputContractVersion());
                if (!usable) issues.add(issue(node, index, "ruleSetVersionId", "规则版本已删除、已退休或不属于当前输入合同，请重新选择"));
            }
            if ("DENY".equals(node.decisionCode())) {
                RuleRejectReason reason = reasons.get(node.reasonCode());
                boolean retained = old != null && "END".equals(old.type()) && "DENY".equals(old.decisionCode())
                    && Objects.equals(old.reasonCode(), node.reasonCode())
                    && Objects.equals(old.reasonMessage(), node.reasonMessage());
                if (reason == null || (!retained && reason.getStatus() != DictionaryStatus.ACTIVE))
                    issues.add(issue(node, index, "reasonCode", "拒绝原因未登记或已停用，请重新选择"));
            }
        }
        return List.copyOf(issues);
    }

    @Override
    public Reference describe(String nodeKey, long versionId, long pointId, String contractCode, int contractVersion) {
        RuleSetVersion version = repository.ruleVersion(versionId, false)
            .orElseThrow(() -> new ErrorException("已保存流程的规则版本缺失"));
        RuleSet root = repository.ruleRoot(versionId)
            .orElseThrow(() -> new ErrorException("已保存流程的规则集根缺失"));
        int count = repository.ruleCount(versionId);
        if (root.getRuleSetId() <= 0 || version.ruleSetVersionId() != versionId || root.getDecisionPointId() != pointId
            || version.decisionPointId() != pointId || !root.getRuleSetCode().equals(version.ruleSetCode())
            || version.versionNo() < 1 || count < 1 || count > 200
            || !sameContract(version, pointId, contractCode, contractVersion))
            throw new ErrorException("已保存流程的规则引用身份或输入合同损坏");
        return new Reference(nodeKey, root.getRuleSetId(), root.getRuleSetCode(), version.ruleSetName(), versionId,
            version.versionNo(), count, root.getStatus().getCode(), version.status().getCode());
    }

    private static boolean sameContract(RuleSetVersion rule, long pointId, String code, int version) {
        return rule.decisionPointId() == pointId && code.equals(rule.inputContractCode())
            && rule.inputContractVersion() == version;
    }

    private static Issue issue(Node node, int index, String field, String message) {
        return new Issue("REFERENCE_UNAVAILABLE", node.nodeKey(), null, "graph.nodes[" + index + "]." + field, message);
    }
}
