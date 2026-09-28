package com.paytech.risk.decision.domainservice.service;

import java.util.List;

import com.paytech.risk.decision.domain.flow.DecisionFlow.Graph;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Issue;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Reference;

/** 规则版本/原因引用资格由 Risk 决定；校验读与保存锁内重证共享同一规则。 */
public interface DecisionFlowReferenceService {
    /** 保存时按规则根、准确版本、原因编码的既有顺序获取共享锁并重证资格。 */
    List<Issue> validateForSave(long pointId, Graph graph, Graph base);
    /** 同一只读快照内检查当前资格，不获取共享行锁。 */
    List<Issue> validateForQuery(long pointId, Graph graph, Graph base);
    Reference describe(String nodeKey, long versionId, long pointId, String contractCode, int contractVersion);
}
