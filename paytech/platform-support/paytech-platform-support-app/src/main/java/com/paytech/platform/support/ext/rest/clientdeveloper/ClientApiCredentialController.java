package com.paytech.platform.support.ext.rest.clientdeveloper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.CreateClientApiCredentialProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ListClientApiCredentialsProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.ListClientApiScopesProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.RevokeClientApiCredentialProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.RotateClientApiCredentialProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientApiCredentialRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientContextRequest;
import com.paytech.platform.support.api.clientapicredential.ClientApiCredentialApi;
import com.paytech.platform.support.api.clientapicredential.request.CreateClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.request.RevokeClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.request.RotateClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialSecretResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialsResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiScopesResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;

import lombok.AllArgsConstructor;

/** 私网工作区自助 API 凭证入口；协议转换后委托用例，一次性密钥响应不缓存。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientApiCredentialController implements ClientApiCredentialApi {
    private final ClientDeveloperConverter converter;
    private final ListClientApiCredentialsProcessor listProcessor;
    private final ListClientApiScopesProcessor scopesProcessor;
    private final CreateClientApiCredentialProcessor createProcessor;
    private final RotateClientApiCredentialProcessor rotateProcessor;
    private final RevokeClientApiCredentialProcessor revokeProcessor;

    /** 凭证列表。 */
    @Override
    public ClientApiCredentialsResponseBody credentials(String accountId, String sessionId, String status) {
        var result = listProcessor.process(query(accountId, sessionId, null, converter.credentialStatus(status)));
        return new ClientApiCredentialsResponseBody(result.credentials().stream()
            .map(credential -> converter.toCredential(credential, result.asOf())).toList());
    }

    /** 单个凭证。 */
    @Override
    public ClientApiCredentialResponseBody credential(String accountId, String sessionId, String credentialId) {
        var result = listProcessor.process(query(accountId, sessionId, converter.pathId(credentialId), null));
        return converter.toCredential(result.credentials().getFirst(), result.asOf());
    }

    /** Scope 目录。 */
    @Override
    public ClientApiScopesResponseBody scopes(String accountId, String sessionId) {
        return converter.toScopes(scopesProcessor.process(new ClientContextRequest(converter.session(accountId),
            converter.session(sessionId))));
    }

    /** 创建；201 且不缓存。 */
    @Override
    @Audit(action = "CLIENT_API_CREDENTIAL_CREATE", resourceType = "API_CREDENTIAL", fallbackResourceId = "NEW")
    public ResponseEntity<ClientApiCredentialSecretResponseBody> create(String accountId, String sessionId,
        CreateClientApiCredentialRequestBody request) {
        var result = createProcessor.process(new ClientApiCredentialRequest(converter.session(accountId),
            converter.session(sessionId), null, null, request.getName(), request.getScopeCodes(),
            request.getValidityDays(), 0, 0));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(converter.toSecret(result));
    }

    /** 轮换；201 且不缓存。 */
    @Override
    @Audit(action = "CLIENT_API_CREDENTIAL_ROTATE", resourceType = "API_CREDENTIAL", resourceIdParameter = "credentialId")
    public ResponseEntity<ClientApiCredentialSecretResponseBody> rotate(String accountId, String sessionId,
        String credentialId, RotateClientApiCredentialRequestBody request) {
        var result = rotateProcessor.process(new ClientApiCredentialRequest(converter.session(accountId),
            converter.session(sessionId), converter.pathId(credentialId), null, null, null, 0,
            request.getExpectedVersion(), request.getGraceMinutes()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(converter.toSecret(result));
    }

    /** 撤销。 */
    @Override
    @Audit(action = "CLIENT_API_CREDENTIAL_REVOKE", resourceType = "API_CREDENTIAL", resourceIdParameter = "credentialId")
    public ClientApiCredentialResponseBody revoke(String accountId, String sessionId, String credentialId,
        RevokeClientApiCredentialRequestBody request) {
        var result = revokeProcessor.process(new ClientApiCredentialRequest(converter.session(accountId),
            converter.session(sessionId), converter.pathId(credentialId), null, null, null, 0,
            request.getExpectedVersion(), 0));
        return converter.toCredential(result.credentials().getFirst(), result.asOf());
    }

    /** 查询请求。 */
    ClientApiCredentialRequest query(String accountId, String sessionId, Long credentialId, String status) {
        return new ClientApiCredentialRequest(converter.session(accountId), converter.session(sessionId), credentialId,
            status, null, null, 0, 0, 0);
    }
}
