package com.paytech.operations.console.bff.integration.impl;

import com.paytech.operations.console.bff.service.model.RiskVariableModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import com.paytech.operations.console.bff.integration.RiskVariableClient;
import com.paytech.risk.api.decision.RiskVariableApi;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/** 统一发现及错误协议转发，身份一致性在公开页面合同前校验。 */
@Component
@AllArgsConstructor
public final class RiskVariableClientImpl implements RiskVariableClient {
    private final RiskVariableApi api;

    private static <T> T validated(Object body, java.util.function.Supplier<T> call) {
        try { return call.get(); }
        catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(body, failure);
        }
    }
    @Override public VariableManagementContextResponseBody getAuthoringContext(OperatorContext context, String pointCode) {
        var response = api.getAuthoringContext(context.accountId(), context.sessionId(), pointCode);
        RiskVariableResponsePolicy.require(response != null);
        return RiskVariableConverter.local(response);
    }
    @Override public VariableListResponseBody listVariables(OperatorContext context, String keyword, String sourceType, String valueType, String status, String pointCode, String usage, String nodeType, String codes, Integer page, Integer size) {
        var response = api.listVariables(context.accountId(), context.sessionId(), keyword, sourceType, valueType, status, pointCode, usage, nodeType, codes, page, size);
        RiskVariableResponsePolicy.require(response != null);
        RiskVariableResponsePolicy.list(response, codes, page, size, sourceType, valueType, status);
        return RiskVariableConverter.local(response);
    }
    @Override public VariableDetailResponseBody getVariable(OperatorContext context, String variableCode) {
        var response = api.getVariable(context.accountId(), context.sessionId(), variableCode);
        RiskVariableResponsePolicy.require(response != null);
        RiskVariableResponsePolicy.equal(variableCode, response.getVariableCode());
        return RiskVariableConverter.local(response);
    }
    @Override public VariableDetailResponseBody createVariable(OperatorContext context, CreateVariableRequestBody body) {
        var response = validated(body, () -> api.createVariable(context.accountId(), context.sessionId(), RiskVariableConverter.owner(body)));
        RiskVariableResponsePolicy.require(response != null);
        RiskVariableResponsePolicy.equal(body.variableCode(), response.getVariableCode());
        RiskVariableResponsePolicy.equal("1", response.getVersion());
        return RiskVariableConverter.local(response);
    }
    @Override public VariableDetailResponseBody updateVariable(OperatorContext context, String variableCode, UpdateVariableRequestBody body) {
        var response = validated(body, () -> api.updateVariable(context.accountId(), context.sessionId(), variableCode, RiskVariableConverter.owner(body)));
        RiskVariableResponsePolicy.require(response != null);
        RiskVariableResponsePolicy.equal(variableCode, response.getVariableCode());
        RiskVariableResponsePolicy.nextVersion(body.expectedVersion(), response.getVersion());
        return RiskVariableConverter.local(response);
    }
    @Override public DeleteVariableResponseBody deleteVariable(OperatorContext context, String variableCode, String expectedVersion) {
        var response = api.deleteVariable(context.accountId(), context.sessionId(), variableCode, expectedVersion);
        RiskVariableResponsePolicy.require(response != null);
        RiskVariableResponsePolicy.equal(variableCode, response.getVariableCode());
        RiskVariableResponsePolicy.nextVersion(expectedVersion, response.getVersion());
        RiskVariableResponsePolicy.equal("DELETED", response.getStatus());
        return RiskVariableConverter.local(response);
    }
    @Override public VariableMembersResponseBody listMembers(OperatorContext context, String variableCode, String keyword, Integer page, Integer size, String expectedVersion) {
        var response = api.listMembers(context.accountId(), context.sessionId(), variableCode, keyword, page, size, expectedVersion);
        RiskVariableResponsePolicy.require(response != null);
        RiskVariableResponsePolicy.equal(variableCode, response.getVariableCode());
        RiskVariableResponsePolicy.members(response, page, size, expectedVersion);
        return RiskVariableConverter.local(response);
    }
    @Override public ReplaceMembersResponseBody replaceMembers(OperatorContext context, String variableCode, ReplaceMembersRequestBody body) {
        var response = validated(body, () -> api.replaceMembers(context.accountId(), context.sessionId(), variableCode, RiskVariableConverter.owner(body)));
        RiskVariableResponsePolicy.require(response != null);
        RiskVariableResponsePolicy.equal(variableCode, response.getVariableCode());
        RiskVariableResponsePolicy.nextVersion(body.expectedVersion(), response.getVersion());
        return RiskVariableConverter.local(response);
    }
    @Override public VariableReferencesResponseBody getReferences(OperatorContext context, String variableCode, String scope, String cursor, Integer size) {
        var response = api.getReferences(context.accountId(), context.sessionId(), variableCode, scope, cursor, size);
        RiskVariableResponsePolicy.require(response != null);
        RiskVariableResponsePolicy.references(response, variableCode, size);
        return RiskVariableConverter.local(response);
    }
}
