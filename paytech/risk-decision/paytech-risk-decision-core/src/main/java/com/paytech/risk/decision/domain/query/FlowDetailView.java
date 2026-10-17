package com.paytech.risk.decision.domain.query;

import java.time.Instant;
import java.util.List;

import com.paytech.risk.decision.domain.flow.DecisionFlow.Graph;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Reference;
import com.paytech.risk.decision.domain.variable.RiskVariable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 本模块查询结果，不对 Web 暴露持久化对象。 */
@Getter
@AllArgsConstructor
public class FlowDetailView {

    /** 流程管理根 ID。 */
    private final long flowId;

    /** 所属决策点编码。 */
    private final String pointCode;

    /** 流程永久编码。 */
    private final String flowCode;

    /** 当前管理根状态。 */
    private final String flowStatus;

    /** 当前配置版本 ID。 */
    private final long currentFlowVersionId;

    /** 当前最高业务版本号。 */
    private final int currentVersionNo;

    /** 管理根修订号。 */
    private final long version;

    /** 所选准确版本 ID。 */
    private final long flowVersionId;

    /** 所选版本的业务版本号。 */
    private final int versionNo;

    /** 准确版本保存的流程名称。 */
    private final String flowName;

    /** 所选版本的输入合同编码。 */
    private final String inputContractCode;

    /** 所选版本的输入合同版本。 */
    private final int inputContractVersion;

    /** 所选版本保存人。 */
    private final long createdBy;

    /** 所选版本保存时间。 */
    private final Instant createTime;

    /** 所选版本的变更原因。 */
    private final String changeReason;

    /** 准确版本的不可变图。 */
    private final Graph graph;

    /** 图内准确规则引用及当前管理资格。 */
    private final List<Reference> ruleSetReferences;

    /** 与历史图处于同一快照的共享变量当前值。 */
    private final RiskVariable.Catalog catalog;
}
