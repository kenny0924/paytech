package com.paytech.operations.console.bff.integration.impl;

import com.paytech.operations.console.bff.service.model.risklist.AddRiskListEntryRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.CreateRiskListRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.DeactivateRiskListEntryRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.RiskListEntryResponseBody;
import com.paytech.operations.console.bff.service.model.risklist.RiskListResponseBody;
import com.paytech.operations.console.bff.service.model.riskrelease.CreateExecutionReleaseRequestBody;
import com.paytech.operations.console.bff.service.model.riskrelease.ExecutionReleaseItemResponseBody;
import com.paytech.operations.console.bff.service.model.riskrelease.ExecutionReleaseResponseBody;
import com.paytech.operations.console.bff.service.model.riskrelease.StartShadowRunRequestBody;
import com.paytech.risk.decision.api.response.ExecutionReleaseListItemResponseBody;
import com.paytech.risk.decision.api.response.ShadowRun;
import com.paytech.risk.decision.api.response.StaticCheck;

/** 名单与执行发布的 Owner 合同与页面合同之间的逐字段转换。 */
final class RiskControlConverter {
    private RiskControlConverter() { }

    /** 转换新建名单请求。 */
    static com.paytech.risk.decision.api.control.request.CreateRiskListRequestBody owner(CreateRiskListRequestBody r) {
        var value = new com.paytech.risk.decision.api.control.request.CreateRiskListRequestBody();
        value.setListCode(r.getListCode());
        value.setListName(r.getListName());
        value.setSubjectType(r.getSubjectType());
        value.setPurpose(r.getPurpose());
        return value;
    }

    /** 转换加入成员请求；主体原值只在此处传递给 Owner 请求。 */
    static com.paytech.risk.decision.api.control.request.AddRiskListEntryRequestBody owner(AddRiskListEntryRequestBody r) {
        var value = new com.paytech.risk.decision.api.control.request.AddRiskListEntryRequestBody();
        value.setSubjectValue(r.getSubjectValue());
        value.setEffectiveFrom(r.getEffectiveFrom());
        value.setEffectiveTo(r.getEffectiveTo());
        value.setReason(r.getReason());
        return value;
    }

    /** 转换失效成员请求。 */
    static com.paytech.risk.decision.api.control.request.DeactivateRiskListEntryRequestBody owner(DeactivateRiskListEntryRequestBody r) {
        var value = new com.paytech.risk.decision.api.control.request.DeactivateRiskListEntryRequestBody();
        value.setReason(r.getReason());
        return value;
    }

    /** 转换名单。 */
    static RiskListResponseBody local(com.paytech.risk.decision.api.control.response.RiskListResponseBody r) {
        var value = new RiskListResponseBody();
        value.setListId(r.getListId());
        value.setListCode(r.getListCode());
        value.setListName(r.getListName());
        value.setSubjectType(r.getSubjectType());
        value.setPurpose(r.getPurpose());
        value.setActive(r.getActive());
        return value;
    }

    /** 转换名单成员；Owner 响应本身只有脱敏键。 */
    static RiskListEntryResponseBody local(com.paytech.risk.decision.api.control.response.RiskListEntryResponseBody r) {
        var value = new RiskListEntryResponseBody();
        value.setEntryId(r.getEntryId());
        value.setListId(r.getListId());
        value.setSubjectType(r.getSubjectType());
        value.setMaskedSubjectKey(r.getMaskedSubjectKey());
        value.setSourceType(r.getSourceType());
        value.setStatus(r.getStatus());
        value.setEffectiveFrom(r.getEffectiveFrom());
        value.setEffectiveTo(r.getEffectiveTo());
        value.setReason(r.getReason());
        value.setCreatedBy(r.getCreatedBy());
        value.setDeactivatedBy(r.getDeactivatedBy());
        value.setDeactivatedReason(r.getDeactivatedReason());
        value.setCreateTime(r.getCreateTime());
        return value;
    }

    /** 转换创建执行发布请求。 */
    static com.paytech.risk.decision.api.request.CreateExecutionReleaseRequestBody owner(CreateExecutionReleaseRequestBody r) {
        var value = new com.paytech.risk.decision.api.request.CreateExecutionReleaseRequestBody();
        value.setPointCode(r.getPointCode());
        value.setFlowVersionId(r.getFlowVersionId());
        return value;
    }

    /** 转换影子运行请求。 */
    static com.paytech.risk.decision.api.request.StartShadowRunRequestBody owner(StartShadowRunRequestBody r) {
        var value = new com.paytech.risk.decision.api.request.StartShadowRunRequestBody();
        value.setSampleTarget(r.getSampleTarget());
        return value;
    }

    /** 转换执行发布列表项。 */
    static ExecutionReleaseItemResponseBody local(ExecutionReleaseListItemResponseBody r) {
        var value = new ExecutionReleaseItemResponseBody();
        value.setReleaseId(r.getReleaseId());
        value.setReleaseNo(r.getReleaseNo());
        value.setPointCode(r.getPointCode());
        value.setEnvironment(r.getEnvironment());
        value.setStatus(r.getStatus());
        value.setActive(r.isActive());
        value.setFlowId(r.getFlowId());
        value.setFlowCode(r.getFlowCode());
        value.setFlowVersionId(r.getFlowVersionId());
        value.setFlowVersionNo(r.getFlowVersionNo());
        value.setContentHash(r.getContentHash());
        value.setCreatedBy(r.getCreatedBy());
        value.setCreateTime(r.getCreateTime());
        value.setActivatedBy(r.getActivatedBy());
        value.setActivatedTime(r.getActivatedTime());
        value.setSupersededTime(r.getSupersededTime());
        return value;
    }

    /** 转换执行发布详情。 */
    static ExecutionReleaseResponseBody local(com.paytech.risk.decision.api.response.ExecutionReleaseResponseBody r) {
        var value = new ExecutionReleaseResponseBody();
        value.setReleaseId(r.getReleaseId());
        value.setReleaseNo(r.getReleaseNo());
        value.setPointCode(r.getPointCode());
        value.setFlowVersionId(r.getFlowVersionId());
        value.setEnvironment(r.getEnvironment());
        value.setStatus(r.getStatus());
        value.setVersion(r.getVersion());
        value.setContentHash(r.getContentHash());
        value.setActivatedTime(r.getActivatedTime());
        value.setSupersededTime(r.getSupersededTime());
        value.setChecks(r.getChecks().stream().map(RiskControlConverter::local).toList());
        value.setShadowRun(r.getShadowRun() == null ? null : local(r.getShadowRun()));
        return value;
    }

    /** 转换静态检查。 */
    static ExecutionReleaseResponseBody.StaticCheck local(StaticCheck r) {
        var value = new ExecutionReleaseResponseBody.StaticCheck();
        value.setCheckCode(r.getCheckCode());
        value.setCheckStatus(r.getCheckStatus());
        value.setErrorCode(r.getErrorCode());
        value.setErrorSummary(r.getErrorSummary());
        return value;
    }

    /** 转换影子运行汇总。 */
    static ExecutionReleaseResponseBody.ShadowRun local(ShadowRun r) {
        var value = new ExecutionReleaseResponseBody.ShadowRun();
        value.setShadowRunId(r.getShadowRunId());
        value.setBaselineReleaseId(r.getBaselineReleaseId());
        value.setStatus(r.getStatus());
        value.setSampleTarget(r.getSampleTarget());
        value.setSampleCount(r.getSampleCount());
        value.setAllowToReviewCount(r.getAllowToReviewCount());
        value.setAllowToDenyCount(r.getAllowToDenyCount());
        value.setDenyToAllowCount(r.getDenyToAllowCount());
        value.setActionChangeCount(r.getActionChangeCount());
        value.setFeatureMissingCount(r.getFeatureMissingCount());
        value.setFailedCount(r.getFailedCount());
        value.setTimedOutCount(r.getTimedOutCount());
        value.setP95Millis(r.getP95Millis());
        value.setP99Millis(r.getP99Millis());
        value.setCheckStatus(r.getCheckStatus());
        return value;
    }
}
