package com.paytech.operations.console.bff.integration.impl;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskExecutionReleaseClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.riskrelease.CreateExecutionReleaseRequestBody;
import com.paytech.operations.console.bff.service.model.riskrelease.ExecutionReleaseItemResponseBody;
import com.paytech.operations.console.bff.service.model.riskrelease.ExecutionReleaseResponseBody;
import com.paytech.operations.console.bff.service.model.riskrelease.StartShadowRunRequestBody;
import com.paytech.risk.decision.api.ExecutionReleaseManagementApi;

import lombok.AllArgsConstructor;

/** 单次调用 Risk 执行发布接口并核对回执身份；状态机、门槛与激活前置均由 Risk 判断。 */
@Component
@AllArgsConstructor
public class RiskExecutionReleaseClientImpl implements RiskExecutionReleaseClient {
    private final ExecutionReleaseManagementApi api;

    /** 发布分页；每项必须属于请求的决策点，筛选状态时状态一致。 */
    @Override
    public PagingResponseBody<ExecutionReleaseItemResponseBody> listExecutions(OperatorContext context, String pointCode,
        String status, int page, int size) {
        var response = api.listExecutions(context.accountId(), context.sessionId(), page, size, pointCode, status);
        RiskControlResponsePolicy.requirePage(response, page, size);
        for (var item : response.items()) {
            RiskControlResponsePolicy.require(item != null && pointCode.equals(item.getPointCode())
                && (status == null || status.equals(item.getStatus())));
        }
        return response.map(RiskControlConverter::local);
    }

    /** 创建发布；回执的决策点与决策流版本必须与请求一致。 */
    @Override
    public ExecutionReleaseResponseBody createExecution(OperatorContext context, CreateExecutionReleaseRequestBody body) {
        com.paytech.risk.decision.api.response.ExecutionReleaseResponseBody response;
        try {
            response = api.createExecution(context.accountId(), context.sessionId(), RiskControlConverter.owner(body));
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(body, failure);
        }
        RiskControlResponsePolicy.require(response != null && Objects.equals(body.getPointCode(), response.getPointCode())
            && Objects.equals(body.getFlowVersionId(), response.getFlowVersionId()));
        return RiskControlConverter.local(response);
    }

    /** 发布详情。 */
    @Override
    public ExecutionReleaseResponseBody getExecution(OperatorContext context, String releaseId) {
        return same(releaseId, api.getExecution(context.accountId(), context.sessionId(), releaseId));
    }

    /** 静态校验。 */
    @Override
    public ExecutionReleaseResponseBody validateExecution(OperatorContext context, String releaseId) {
        return same(releaseId, api.validateExecution(context.accountId(), context.sessionId(), releaseId));
    }

    /** 影子运行。 */
    @Override
    public ExecutionReleaseResponseBody startShadowRun(OperatorContext context, String releaseId, StartShadowRunRequestBody body) {
        com.paytech.risk.decision.api.response.ExecutionReleaseResponseBody response;
        try {
            response = api.startShadowRun(context.accountId(), context.sessionId(), releaseId, RiskControlConverter.owner(body));
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(body, failure);
        }
        return same(releaseId, response);
    }

    /** 激活。 */
    @Override
    public ExecutionReleaseResponseBody activateExecution(OperatorContext context, String releaseId) {
        return same(releaseId, api.activateExecution(context.accountId(), context.sessionId(), releaseId));
    }

    /** 回执发布 ID 必须与请求路径一致。 */
    private static ExecutionReleaseResponseBody same(String releaseId,
        com.paytech.risk.decision.api.response.ExecutionReleaseResponseBody response) {
        RiskControlResponsePolicy.require(response != null && Objects.equals(releaseId, response.getReleaseId()));
        return RiskControlConverter.local(response);
    }
}
