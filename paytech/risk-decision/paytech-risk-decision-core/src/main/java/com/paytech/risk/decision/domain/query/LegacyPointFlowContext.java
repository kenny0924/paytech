package com.paytech.risk.decision.domain.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Edge;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Node;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Reference;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 旧顺序图的原始事实，只进行内存校验和投影。 */
@Getter
@AllArgsConstructor
public class LegacyPointFlowContext {

    /** 真实所属决策点。 */
    private final DecisionPoint point;

    /** 准确旧点配置版本。 */
    private final DecisionPointVersion version;

    /** 按原序号读取的绑定。 */
    private final List<DecisionPointRuleSet> bindings;

    /** 原绑定必须完整连续；缺失、不属于该版本或重复均不能形成有效图。 */
    public void verifyBindings() {
        if (bindings.size() > 200 || version.versionNo() < 1) { throw corrupt(); }
        Set<Long> seen = new HashSet<>();
        for (int index = 0; index < bindings.size(); index++) {
            DecisionPointRuleSet binding = bindings.get(index);
            if (binding.bindingId() <= 0 || !seen.add(binding.bindingId())
                || binding.pointVersionId() != version.pointVersionId() || binding.sequenceNo() != index + 1) {
                throw corrupt();
            }
        }
    }

    /** 已验证的准确引用按绑定原顺序展示，不创建或保存新流程。 */
    public LegacyPointFlowView toView(List<Reference> references) {
        var nodes = new ArrayList<Node>();
        var edges = new ArrayList<Edge>();
        nodes.add(new Node("legacy_start", "START", "开始", 0, 0, null, null, null, null));
        String previous = "legacy_start";
        for (int index = 0; index < references.size(); index++) {
            Reference ref = references.get(index);
            nodes.add(new Node(ref.nodeKey(), "RULE_SET", ref.ruleSetName(), (index + 1) * 300, 0,
                ref.ruleSetVersionId(), null, null, null));
            edges.add(new Edge("legacy_edge_" + (index + 1), previous, ref.nodeKey(), "UNCONDITIONAL", 0,
                "顺序 " + (index + 1), null));
            previous = ref.nodeKey();
        }
        nodes.add(new Node("legacy_default", "END", "原默认决定", (bindings.size() + 1) * 300, 0,
            null, version.defaultDecision().getCode(), null, null));
        edges.add(new Edge("legacy_default_edge", previous, "legacy_default", "UNCONDITIONAL", 0, "原默认决定", null));
        return new LegacyPointFlowView(version.pointVersionId(), point.pointCode(), point.pointName(), version.versionNo(),
            version.inputContractCode(), version.inputContractVersion(), version.defaultDecision().getCode(),
            version.evaluationStrategy().getCode(), version.failureStrategy().getCode(),
            List.copyOf(nodes), List.copyOf(edges), List.copyOf(references));
    }

    /** 只报告损坏，不返回部分顺序图。 */
    private static ErrorException corrupt() { return new ErrorException("流程持久化配置不完整或身份不一致"); }
}
