package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import com.paytech.platform.support.accesscontrol.core.configuration.ClientAuthenticationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Tokens;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Issue;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientSessionRules;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAccountView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientSigningKeyRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerJwksService;
import com.paytech.platform.support.accesscontrol.integration.signing.HumanAuthenticationCryptography;
import tools.jackson.databind.ObjectMapper;

/** CUSTOMER 签名配置与 Operations 隔离；不在此方法开启数据库写事务。 */
@Component
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientTokenPreparation {
    private final Clock clock;
    private final IdGenerator ids;
    private final ObjectMapper json;
    private final ClientSigningKeyRepository repository;
    private final CustomerJwksService jwks;
    private final HumanAuthenticationCryptography crypto;
    private final String issuer;

    /** 绑定经配置校验的 CUSTOMER issuer 和独立密码学端口。 */
    public ClientTokenPreparation(Clock clock, IdGenerator ids, ObjectMapper json,
        ClientSigningKeyRepository repository, CustomerJwksService jwks,
        @Qualifier("clientAuthenticationCryptography") HumanAuthenticationCryptography crypto,
        ClientAuthenticationProperties properties) {
        this.clock = clock;
        this.ids = ids;
        this.json = json;
        this.repository = repository;
        this.jwks = jwks;
        this.crypto = crypto;
        this.issuer = properties.getIssuer();
    }

    /** 先验证当前可发布公钥，再通过 CUSTOMER 密钥端口签名。 */
    public Issue prepare(ClientAccountView context, int epoch, int credentialVersion, Instant absolute, int generation) {
        Instant now = clock.instant();
        var key = repository.signingKey();
        if (key == null || key.notBefore().isAfter(now) || !now.isBefore(absolute)
            || jwks.queryCurrentJwks().stream().noneMatch(k -> k.kid().equals(key.kid()))) {
            throw new ServiceUnavailableException();
        }
        String raw = ClientSessionRules.newRefreshToken();
        var hmac = crypto.versionedHmac("client-refresh-v1", raw);
        Instant expires = now.plusSeconds(900).isBefore(absolute) ? now.plusSeconds(900) : absolute;
        var claims = new LinkedHashMap<String,Object>();
        claims.put("iss", issuer);
        claims.put("aud", List.of("client-console"));
        claims.put("sub", Long.toString(context.accountId()));
        claims.put("sid", Long.toString(context.sessionId()));
        claims.put("jti", Long.toString(ids.nextId()));
        claims.put("token_use", "ACCESS");
        claims.put("realm", "CUSTOMER");
        claims.put("channel", "CLIENT");
        claims.put("actor_type", "PLATFORM_USER");
        claims.put("context_type", context.contextType());
        claims.put("iat", now.getEpochSecond());
        claims.put("nbf", now.getEpochSecond());
        claims.put("exp", expires.getEpochSecond());
        if ("TENANT".equals(context.contextType())) {
            claims.put("tenant_id", Long.toString(context.tenantId()));
            claims.put("tenant_member_id", Long.toString(context.memberId()));
            claims.put("tenant_type", "CLIENT");
        }
        String jwt = crypto.sign(key.keyRef(), key.kid(), key.publicJwk(), json.writeValueAsString(claims));
        var response = new Tokens(context, jwt, expires, raw, absolute);
        return new Issue(context.accountId(), context.sessionId(), context.contextType(), context.tenantId(),
            context.memberId(), epoch, credentialVersion, now,
            ClientSessionRules.idleExpiry(now, absolute), absolute, generation, ids.nextId(),
            ClientSessionRules.lookup(raw), hmac.digest(), hmac.keyVersion(), key.id(), key.version(), response);
    }
}
