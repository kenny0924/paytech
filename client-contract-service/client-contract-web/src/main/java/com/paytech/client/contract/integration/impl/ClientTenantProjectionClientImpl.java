package com.paytech.client.contract.integration.impl;

import com.paytech.client.contract.integration.ClientTenantProjectionClient;
import com.paytech.client.contract.domain.onboarding.ClientProjectionModels.*;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.tenantprojection.TenantProjectionApi;
import com.paytech.platform.support.api.tenantprojection.request.ProvisionRequestBody;
import com.paytech.platform.support.api.tenantprojection.request.StatusRequestBody;
import com.paytech.platform.support.api.tenantprojection.response.ProjectionResponseBody;

/** HTTP 协议和 Problem 解码复用 Foundation；此处只处理 PLT-I17 业务语义。 */
public final class ClientTenantProjectionClientImpl implements ClientTenantProjectionClient {
    private final TenantProjectionApi api;
    public ClientTenantProjectionClientImpl(TenantProjectionApi api) { this.api = api; }
    @Override public Position find(long clientId) {
        try { return convert(api.projection("CLIENT", Long.toString(clientId))); }
        catch (FailException failure) {
            if (failure.getCode().equals("NOT_FOUND")) { return null; }
            throw failure;
        }
    }
    @Override public Position project(Source source) {
        var response = source.sourceVersion() == 1
            ? api.provision(new ProvisionRequestBody("CLIENT", Long.toString(source.clientId()), Long.toString(source.applicantId()), 1, source.payloadHash()))
            : api.projectStatus(new StatusRequestBody("CLIENT", Long.toString(source.clientId()), Long.toString(source.applicantId()), source.sourceVersion(), source.payloadHash(), source.status()));
        return convert(response);
    }
    private Position convert(ProjectionResponseBody body) {
        if (body == null || !"CLIENT".equals(body.getTenantType()) || body.getSourceVersion() < 1
            || body.getPayloadHash() == null || !body.getPayloadHash().matches("[a-f0-9]{64}")
            || body.getOwnerStatus() == null
            || !java.util.Set.of("PENDING", "ACTIVE", "SUSPENDED", "TERMINATED").contains(body.getOwnerStatus())) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return new Position(id(body.getOwnerRefId()), id(body.getRequestedByAccountId()), id(body.getTenantId()), id(body.getMemberId()),
            body.getSourceVersion(), body.getOwnerStatus(), body.getPayloadHash());
    }
    private long id(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) { throw new NumberFormatException(); }
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) { throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, invalid); }
    }
}
