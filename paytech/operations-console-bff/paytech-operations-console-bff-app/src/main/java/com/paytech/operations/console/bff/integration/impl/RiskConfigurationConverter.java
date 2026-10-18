package com.paytech.operations.console.bff.integration.impl;
import com.paytech.risk.decision.api.response.RuleSetReleaseReferenceResponseBody;
import com.paytech.operations.console.bff.service.model.RiskDictionaryModels.*;
import com.paytech.operations.console.bff.service.model.RiskConfigurationQueryModels.*;
/** Owner DTO 只在集成边界使用，输出独立本地合同。 */
final class RiskConfigurationConverter {
    /** 仅作为模型或转换方法的命名空间，不允许创建实例。 */
    private RiskConfigurationConverter() { }
    /** 将原因 Owner DTO 转换为 BFF 当前值模型，保持字段原意。 */
    static RejectionReasonResponseBody reason(com.paytech.risk.decision.api.response.RejectionReasonResponseBody r) {
        return new RejectionReasonResponseBody(r.getReasonCode(), r.getName(), r.getDescription(), r.getDefaultMessage(),
            r.getStatus(), r.getVersion(), r.getCreatedBy(), r.getUpdatedBy(), r.getCreateTime(), r.getUpdateTime());
    }
    /** 将标签 Owner DTO 转换为 BFF 当前值模型，不泄露 Owner 类型。 */
    static SubjectTagResponseBody tag(com.paytech.risk.decision.api.response.SubjectTagResponseBody r) {
        return new SubjectTagResponseBody(r.getTagCode(), r.getName(), r.getDescription(), r.getStatus(), r.getVersion(), r.getCreatedBy(), r.getUpdatedBy(), r.getCreateTime(), r.getUpdateTime());
    }
    /** 转换当前写入结果，不增加历史回执或自动恢复字段。 */
    static DictionaryWriteResponseBody write(com.paytech.risk.decision.api.response.DictionaryWriteResponseBody r) {
        return new DictionaryWriteResponseBody(r.getDictionaryType(), r.getCode(), r.getVersion(), r.getStatus(), r.getChangedBy(), r.getChangedTime());
    }
    /** 转换精确配置引用，保留根状态和当前版本指针语义。 */
    static DictionaryReference reference(com.paytech.risk.decision.api.response.SubjectTagReferenceResponseBody r) {
        return new DictionaryReference(r.getRuleSetId(), r.getPointCode(), r.getRuleSetCode(), r.getRuleSetName(), r.getRuleSetVersionId(), r.getVersionNo(), r.getRuleCode(), r.getRuleName(), r.getReferenceKind(), r.getActionSequenceNo(), r.getTargetBindingCode(), r.getRuleSetStatus(), r.getIsCurrentConfiguration(), r.getActiveEnvironments());
    }
    /** 转换历史摘要，保留精确版本 ID 及创建时信息。 */
    static RuleSetHistoryItem history(com.paytech.risk.decision.api.response.RuleSetHistoryItemResponseBody r) {
        return new RuleSetHistoryItem(r.getRuleSetId(), r.getRuleSetVersionId(), r.getPointCode(), r.getRuleSetCode(),
            r.getRuleSetName(), r.getVersionNo(), r.getStatus(), r.getIsCurrent(), r.getCreatedBy(), r.getChangeReason(), r.getCreateTime());
    }
    /** 转换发布引用的点版本与规则版本坐标。 */
    static ReleaseReference release(RuleSetReleaseReferenceResponseBody r) {
        return new ReleaseReference(r.getReleaseId(), r.getReleaseNo(), r.getEnvironmentCode(), r.getStatus(),
            r.getPointCode(), r.getPointVersionId(), r.getPointVersionNo(), r.getRuleSetVersionId(), r.getVersionNo(),
            r.getActivatedTime(), r.getSupersededTime());
    }
    /** 原因 Reader 同时保留规则及流程坐标，标签模型保持不变。 */
    static RejectionReasonReference reference(com.paytech.risk.decision.api.response.RejectionReasonReferenceResponseBody r) {
        return new RejectionReasonReference(r.getRuleSetId(), r.getPointCode(), r.getRuleSetCode(), r.getRuleSetName(), r.getRuleSetVersionId(),
            r.getVersionNo(), r.getRuleCode(), r.getRuleName(), r.getReferenceKind(), r.getActionSequenceNo(), r.getTargetBindingCode(), r.getRuleSetStatus(),
            r.getIsCurrentConfiguration(), r.getActiveEnvironments(), r.getSourceType(), r.getFlowId(), r.getFlowCode(), r.getFlowName(), r.getFlowVersionId(),
            r.getFlowVersionNo(), r.getNodeKey(), r.getNodeName(), r.getFlowStatus());
    }
}
