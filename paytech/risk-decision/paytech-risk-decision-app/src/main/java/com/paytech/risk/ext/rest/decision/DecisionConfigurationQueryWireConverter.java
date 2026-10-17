package com.paytech.risk.ext.rest.decision;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.decision.response.RejectionReasonReferenceResponseBody;
import com.paytech.risk.api.decision.response.RuleSetHistoryItemResponseBody;
import com.paytech.risk.api.decision.response.RuleSetReleaseReferenceResponseBody;
import com.paytech.risk.api.decision.response.RuleSetReleaseReferencesResponseBody;
import com.paytech.risk.api.decision.response.SubjectTagReferenceResponseBody;
import com.paytech.risk.decision.domain.query.DecisionConfigurationQueries;
import com.paytech.risk.decision.domain.query.RuleSetHistoryView;
import com.paytech.risk.decision.domain.query.RuleSetReleaseReferenceView;
import com.paytech.risk.decision.domain.query.RuleSetReleaseReferencesView;
/** 固定字段转换，不增加名称聚合或改写历史。 */
final class DecisionConfigurationQueryWireConverter {
    /** 仅作为模型或转换方法的命名空间，不允许创建实例。 */
    private DecisionConfigurationQueryWireConverter() { }
    /** 将历史领域页转换为 API 字段，ID 使用字符串且时间保持 UTC 微秒。 */
    static PagingResponseBody<RuleSetHistoryItemResponseBody> history(PagingResponseBody<RuleSetHistoryView> page) {
        return page.map(DecisionConfigurationQueryWireConverter::historyItem);
    }

    /** 转换历史摘要，不改写该版本保存时的名称、原因和时间。 */
    static RuleSetHistoryItemResponseBody historyItem(RuleSetHistoryView view) {
        var response = new RuleSetHistoryItemResponseBody();
        response.setRuleSetId(Long.toString(view.getRuleSetId()));
        response.setRuleSetVersionId(Long.toString(view.getRuleSetVersionId()));
        response.setPointCode(view.getPointCode());
        response.setRuleSetCode(view.getRuleSetCode());
        response.setRuleSetName(view.getRuleSetName());
        response.setVersionNo(view.getVersionNo());
        response.setStatus(view.getStatus().getCode());
        response.setIsCurrent(view.isCurrent());
        response.setCreatedBy(Long.toString(view.getCreatedBy()));
        response.setChangeReason(view.getChangeReason());
        response.setCreateTime(DecisionDictionaryWireConverter.time(view.getCreateTime()));
        return response;
    }
    /** 转换发布引用分页与全根使用标志，不从页面筛选重新推导 inUse。 */
    static RuleSetReleaseReferencesResponseBody releases(RuleSetReleaseReferencesView view) {
        var response = new RuleSetReleaseReferencesResponseBody();
        response.setRuleSetId(Long.toString(view.getRuleSetId()));
        response.setInUse(view.isInUse());
        response.setReferences(view.getReferences().map(DecisionConfigurationQueryWireConverter::releaseReference));
        return response;
    }

    /** 转换精确发布引用，ID 使用字符串，可空时间保留 null。 */
    static RuleSetReleaseReferenceResponseBody releaseReference(RuleSetReleaseReferenceView view) {
        var response = new RuleSetReleaseReferenceResponseBody();
        response.setReleaseId(Long.toString(view.getReleaseId()));
        response.setReleaseNo(view.getReleaseNo());
        response.setEnvironmentCode(view.getEnvironmentCode().getCode());
        response.setStatus(view.getStatus().getCode());
        response.setPointCode(view.getPointCode());
        response.setPointVersionId(Long.toString(view.getPointVersionId()));
        response.setPointVersionNo(view.getPointVersionNo());
        response.setRuleSetVersionId(Long.toString(view.getRuleSetVersionId()));
        response.setVersionNo(view.getVersionNo());
        response.setActivatedTime(DecisionDictionaryWireConverter.time(view.getActivatedTime()));
        response.setSupersededTime(DecisionDictionaryWireConverter.time(view.getSupersededTime()));
        return response;
    }
    /** 转换标签保存项引用，保留删除状态、当前指针和活动环境。 */
    static PagingResponseBody<SubjectTagReferenceResponseBody> references(
        PagingResponseBody<DecisionConfigurationQueries.DictionaryReferenceView> page) {
        return page.map(DecisionConfigurationQueryWireConverter::tagReference);
    }

    /** 规则 ID 在 HTTP 边界转换为字符串，历史名称直接沿用保存值。 */
    static SubjectTagReferenceResponseBody tagReference(DecisionConfigurationQueries.DictionaryReferenceView r) {
        var response = new SubjectTagReferenceResponseBody();
        response.setRuleSetId(Long.toString(r.ruleSetId()));
        response.setPointCode(r.pointCode());
        response.setRuleSetCode(r.ruleSetCode());
        response.setRuleSetName(r.ruleSetName());
        response.setRuleSetVersionId(Long.toString(r.ruleSetVersionId()));
        response.setVersionNo(r.versionNo());
        response.setRuleCode(r.ruleCode());
        response.setRuleName(r.ruleName());
        response.setReferenceKind(r.referenceKind().getCode());
        response.setActionSequenceNo(r.actionSequenceNo());
        response.setTargetBindingCode(r.targetBindingCode());
        response.setRuleSetStatus(r.ruleSetStatus().getCode());
        response.setIsCurrentConfiguration(r.isCurrentConfiguration());
        response.setActiveEnvironments(r.activeEnvironments().stream().map(e -> e.getCode()).toList());
        return response;
    }

    /** 将互斥的规则和流程 View 映射为固定字段合同。 */
    static PagingResponseBody<RejectionReasonReferenceResponseBody> reasonReferences(
        PagingResponseBody<DecisionConfigurationQueries.ReasonReferenceView> page) {
        return page.map(value -> switch (value) {
            case DecisionConfigurationQueries.RuleReasonReferenceView item -> ruleReasonReference(item.rule());
            case DecisionConfigurationQueries.FlowEndReasonReferenceView item -> flowReasonReference(item);
        });
    }

    /** 原因规则来源保留历史规则坐标，流程字段保持 null。 */
    static RejectionReasonReferenceResponseBody ruleReasonReference(
        DecisionConfigurationQueries.DictionaryReferenceView r) {
        var response = new RejectionReasonReferenceResponseBody();
        response.setRuleSetId(Long.toString(r.ruleSetId()));
        response.setPointCode(r.pointCode());
        response.setRuleSetCode(r.ruleSetCode());
        response.setRuleSetName(r.ruleSetName());
        response.setRuleSetVersionId(Long.toString(r.ruleSetVersionId()));
        response.setVersionNo(r.versionNo());
        response.setRuleCode(r.ruleCode());
        response.setRuleName(r.ruleName());
        response.setReferenceKind("REJECTION_REASON");
        response.setActionSequenceNo(null);
        response.setTargetBindingCode(null);
        response.setRuleSetStatus(r.ruleSetStatus().getCode());
        response.setIsCurrentConfiguration(r.isCurrentConfiguration());
        response.setActiveEnvironments(r.activeEnvironments().stream().map(e -> e.getCode()).toList());
        response.setSourceType("RULE_SET");
        return response;
    }

    /** 原因流程来源只输出流程和节点坐标，规则字段保持 null，活动环境为空列表。 */
    static RejectionReasonReferenceResponseBody flowReasonReference(
        DecisionConfigurationQueries.FlowEndReasonReferenceView r) {
        var response = new RejectionReasonReferenceResponseBody();
        response.setPointCode(r.pointCode());
        response.setReferenceKind("REJECTION_REASON");
        response.setIsCurrentConfiguration(r.isCurrentConfiguration());
        response.setActiveEnvironments(java.util.List.of());
        response.setSourceType("FLOW_END");
        response.setFlowId(Long.toString(r.flowId()));
        response.setFlowCode(r.flowCode());
        response.setFlowName(r.flowName());
        response.setFlowVersionId(Long.toString(r.flowVersionId()));
        response.setFlowVersionNo(r.flowVersionNo());
        response.setNodeKey(r.nodeKey());
        response.setNodeName(r.nodeName());
        response.setFlowStatus(r.flowStatus().getCode());
        return response;
    }
}
