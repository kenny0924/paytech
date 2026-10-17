package com.paytech.platform.support.api.clientapicredential;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.clientapicredential.request.CreateClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.request.RevokeClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.request.RotateClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialSecretResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialsResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiScopesResponseBody;

import jakarta.validation.Valid;

/**
 * 工作区成员自助管理本工作区的 API 凭证；只供 Client BFF 私网调用
 * <li>租户只取自当前有效的 CLIENT 租户上下文会话，不接受调用方指定 Client 或租户。</li>
 * <li>读取需要 CLIENT_API_CREDENTIAL_READ，写入需要 CLIENT_API_CREDENTIAL_MANAGE；无权限返回 403 CLIENT_PERMISSION_DENIED。</li>
 * <li>按 ID 的操作不属于当前工作区时返回 404，不透露是否存在。</li>
 */
@HttpExchange("/client-api-credentials/v1")
public interface ClientApiCredentialApi {

    /**
     * 本工作区凭证列表
     *
     * @param status 可选 ACTIVE / REVOKED / EXPIRED；EXPIRED 按到期时间推导
     * @return 按创建倒序的凭证摘要，不含密钥与校验值
     */
    @GetExchange("/credentials")
    ClientApiCredentialsResponseBody credentials(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "status", required = false) String status);

    /** 单个凭证摘要。 */
    @GetExchange("/credentials/{credentialId}")
    ClientApiCredentialResponseBody credential(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("credentialId") String credentialId);

    /** 可授予的 Scope 目录。 */
    @GetExchange("/scopes")
    ClientApiScopesResponseBody scopes(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);

    /**
     * 创建凭证
     * <li>本工作区有效凭证最多 10 个；完整密钥只在本次 201 响应中出现一次。</li>
     */
    @PostExchange("/credentials")
    ResponseEntity<ClientApiCredentialSecretResponseBody> create(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @Valid @RequestBody CreateClientApiCredentialRequestBody request);

    /**
     * 轮换凭证
     * <li>新凭证继承名称、Scope 与有效期天数；旧凭证按宽限分钟数失效，0 表示立即撤销。</li>
     * <li>新密钥只在本次 201 响应中出现一次；轮换不受 10 个上限阻止。</li>
     */
    @PostExchange("/credentials/{credentialId}/rotation")
    ResponseEntity<ClientApiCredentialSecretResponseBody> rotate(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("credentialId") String credentialId,
        @Valid @RequestBody RotateClientApiCredentialRequestBody request);

    /** 撤销凭证；已撤销时返回原结果。 */
    @PostExchange("/credentials/{credentialId}/revocation")
    ClientApiCredentialResponseBody revoke(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("credentialId") String credentialId,
        @Valid @RequestBody RevokeClientApiCredentialRequestBody request);
}
