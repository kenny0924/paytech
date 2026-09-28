package com.paytech.risk.decision.domain.query;

import java.util.List;

import com.paytech.risk.decision.domain.flow.DecisionFlow.Edge;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Node;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Reference;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 旧点配置只读顺序图，不产生新 Flow 身份。 */
@Getter
@AllArgsConstructor
public class LegacyPointFlowView {

    /** 旧点配置准确版本 ID。 */
    private final long pointVersionId;

    /** 所属决策点编码。 */
    private final String pointCode;

    /** 决策点名称。 */
    private final String pointName;

    /** 所选版本的业务版本号。 */
    private final int versionNo;

    /** 所选版本的输入合同编码。 */
    private final String inputContractCode;

    /** 所选版本的输入合同版本。 */
    private final int inputContractVersion;

    /** 原版本默认决定。 */
    private final String defaultDecision;

    /** 原版本评估策略。 */
    private final String evaluationStrategy;

    /** 原版本失败策略。 */
    private final String failureStrategy;

    /** 按实际绑定构造的节点。 */
    private final List<Node> nodes;

    /** 按实际顺序构造的连线。 */
    private final List<Edge> edges;

    /** 图内准确规则引用及当前管理资格。 */
    private final List<Reference> ruleSetReferences;
}
