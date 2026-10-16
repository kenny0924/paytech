package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientApiCredentialQueryResult;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientApiCredentialRepository;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientApiCredentialRequest;

import lombok.AllArgsConstructor;

/**
 * 查询本工作区 API 凭证：credentialId 为空返回列表，否则返回单个（不属于本工作区为 404）。
 * <li>会话、权限与凭证在同一只读快照内读取；不返回 PHC 校验值。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ListClientApiCredentialsProcessor
    extends AbstractProcessor<ClientApiCredentialRequest, ClientApiCredentialQueryResult> {
    private final Clock clock;
    private final ClientWorkspaceAccessSupport access;
    private final ClientApiCredentialRepository credentials;

    @Override
    protected ClientApiCredentialQueryResult processRequest(ClientApiCredentialRequest request) {
        return access.readTransaction().execute(status -> {
            var workspace = access.require(request.getAccountId(), request.getSessionId(),
                ClientDeveloperPermissions.API_CREDENTIAL_READ);
            var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            if (request.getCredentialId() == null) {
                return new ClientApiCredentialQueryResult(credentials.credentials(workspace.getTenantId(),
                    request.getStatus(), now), now);
            }
            var credential = credentials.credential(workspace.getTenantId(), request.getCredentialId(), now);
            if (credential == null) {
                throw new FailException(CommonErrorCode.NOT_FOUND);
            }
            return new ClientApiCredentialQueryResult(List.of(credential), now);
        });
    }
}
