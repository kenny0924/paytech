package com.paytech.client.console.bff.integration.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.core.ClientRefreshCookie;
import com.paytech.client.console.bff.domain.enums.WorkspaceRestrictionReason;
import com.paytech.client.console.bff.integration.PlatformAuthenticationClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.SessionGrant;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.LoginRequestBody;
import com.paytech.platform.support.api.authentication.request.RefreshRequestBody;
import com.paytech.platform.support.api.authentication.request.WorkspaceContextRequestBody;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationResponseBody;
import com.paytech.platform.support.api.authentication.response.ClientWorkspaceResponseBody;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import lombok.AllArgsConstructor;

/**
 * 调用 Platform CLIENT 认证接口并校验回执
 * <li>会话签发结果必须带上下文、非空 Access Token、格式正确的 Refresh Token 及未来的 Refresh 到期时间，否则按依赖不可用。</li>
 * <li>Refresh Cookie 有效期取 Refresh 剩余时间，最长 7 天。</li>
 */
@Component
@AllArgsConstructor
public class PlatformAuthenticationClientImpl implements PlatformAuthenticationClient {
    /** Refresh Cookie 最长有效期（秒）。 */
    static final long MAX_REFRESH_SECONDS = 604800;

    /** 单个账号最多返回的工作区数量。 */
    static final int MAX_WORKSPACES = 50;

    private static final String ID = "[1-9][0-9]{0,18}";

    private final ClientAuthenticationApi platform;

    /** 登录并校验签发结果。 */
    @Override
    public SessionGrant login(LoginRequestBody body) {
        return grant(platform.login(body));
    }

    /** 轮换会话并校验签发结果。 */
    @Override
    public SessionGrant refresh(String refreshToken) {
        return grant(platform.refresh(new RefreshRequestBody(refreshToken)));
    }

    /** 退出；会话已失效视为已经退出，其他失败原样抛出。 */
    @Override
    public void logout(ClientActor actor, String refreshToken) {
        try {
            platform.logout(actor.getAccountId(), actor.getSessionId(), new RefreshRequestBody(refreshToken));
        } catch (FailException failure) {
            if (failure.getErrorCode() != ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID) {
                throw failure;
            }
        }
    }

    /** 切换到工作区并校验签发结果。 */
    @Override
    public SessionGrant selectWorkspace(ClientActor actor, String refreshToken, String memberId) {
        return grant(platform.selectWorkspace(actor.getAccountId(), actor.getSessionId(),
            new WorkspaceContextRequestBody(refreshToken, memberId)));
    }

    /** 切回账号上下文并校验签发结果。 */
    @Override
    public SessionGrant selectAccountContext(ClientActor actor, String refreshToken) {
        return grant(platform.selectAccountContext(actor.getAccountId(), actor.getSessionId(), new RefreshRequestBody(refreshToken)));
    }

    /** 回读当前会话上下文，原样返回 Platform 事实。 */
    @Override
    public AccountContextResponseBody context(ClientActor actor) {
        return platform.context(actor.getAccountId(), actor.getSessionId());
    }

    /** 读取并校验工作区成员列表；成员 ID 不得重复。 */
    @Override
    public List<ClientWorkspaceResponseBody> workspaces(ClientActor actor) {
        var value = platform.workspaces(actor.getAccountId(), actor.getSessionId());
        if (value == null || value.getItems() == null || value.getItems().size() > MAX_WORKSPACES) {
            throw new ServiceUnavailableException();
        }
        var members = new HashSet<String>();
        for (var workspace : value.getItems()) {
            if (!validWorkspace(workspace) || !members.add(workspace.getMemberId())) {
                throw new ServiceUnavailableException();
            }
        }
        return value.getItems();
    }

    /** 三个 ID 为规范正整数；可访问时无原因码，受限时原因码必须是公开原因。 */
    static boolean validWorkspace(ClientWorkspaceResponseBody workspace) {
        if (workspace == null || !canonicalId(workspace.getMemberId()) || !canonicalId(workspace.getTenantId())
            || !canonicalId(workspace.getClientId()) || workspace.isAccessible() != (workspace.getReasonCode() == null)) {
            return false;
        }
        return workspace.getReasonCode() == null || WorkspaceRestrictionReason.fromCode(workspace.getReasonCode()) != null;
    }

    /** 非空且为规范正整数格式。 */
    static boolean canonicalId(String value) {
        return value != null && value.matches(ID);
    }

    /** 校验 Platform 签发结果并计算 Refresh Cookie 有效期。 */
    static SessionGrant grant(AuthenticationResponseBody grant) {
        if (grant == null || grant.getContext() == null || grant.getAccessToken() == null || grant.getAccessToken().isBlank()
            || grant.getRefreshToken() == null || !grant.getRefreshToken().matches(ClientRefreshCookie.TOKEN_PATTERN)) {
            throw new ServiceUnavailableException();
        }
        return new SessionGrant(grant.getContext(), grant.getAccessToken(), grant.getExpiresAt(), grant.getRefreshToken(),
            refreshMaxAgeSeconds(grant.getRefreshExpiresAt()));
    }

    /** Refresh 剩余秒数，最长 7 天；无法解析或已到期按依赖不可用。 */
    static long refreshMaxAgeSeconds(String refreshExpiresAt) {
        long seconds;
        try {
            seconds = Math.min(MAX_REFRESH_SECONDS, Duration.between(Instant.now(), Instant.parse(refreshExpiresAt)).getSeconds());
        } catch (RuntimeException invalid) {
            throw new ServiceUnavailableException();
        }
        if (seconds <= 0) {
            throw new ServiceUnavailableException();
        }
        return seconds;
    }
}
