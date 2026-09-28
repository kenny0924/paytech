package com.paytech.gateway.security;

import java.util.Objects;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import com.paytech.gateway.security.VersionedHmacDigester.VersionedDigest;
import com.paytech.gateway.web.GatewayExchangeAttributes;

import reactor.core.publisher.Mono;

/** 只校验 Client 渠道身份，当前权限由 BFF 调用 Platform 复核。 */
public final class ClientChannelAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {
    private final VersionedHmacDigester digester;

    public ClientChannelAuthorizationManager(VersionedHmacDigester digester) {
        this.digester = Objects.requireNonNull(digester, "访问日志摘要器不可为空");
    }

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication.map(value -> hasClientPlatformIdentity(value, context))
            .map(granted -> (AuthorizationResult)new AuthorizationDecision(granted))
            .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private boolean hasClientPlatformIdentity(Authentication authentication, AuthorizationContext context) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return false;
        }
        recordAccessSubjectDigest(jwtAuthentication, context);
        Object channel = jwtAuthentication.getToken().getClaims().get("channel");
        Object actorType = jwtAuthentication.getToken().getClaims().get("actor_type");
        return "CLIENT".equals(channel) && "PLATFORM_USER".equals(actorType);
    }

    private void recordAccessSubjectDigest(JwtAuthenticationToken authentication, AuthorizationContext context) {
        VersionedDigest digest = digester.accessSubjectDigest(authentication.getToken().getSubject());
        context.getExchange().getAttributes().put(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST,
            digest.version() + ":" + digest.digest());
    }
}
