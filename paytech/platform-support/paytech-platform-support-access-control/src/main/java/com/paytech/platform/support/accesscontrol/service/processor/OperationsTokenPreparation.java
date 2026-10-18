package com.paytech.platform.support.accesscontrol.service.processor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.PreparedTokens;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.TokenResult;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsSigningKeyRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsJwksService;
import com.paytech.platform.support.accesscontrol.integration.signing.OperationsTokenCryptography;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.enums.OperationsContextType;

import lombok.AllArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/** 事务外生成刷新秘密与预签 Access；本组件不保存会话或刷新代。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsTokenPreparation {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IdGenerator ids;
    private final ObjectMapper json;
    private final OperationsJwksService jwks;
    private final Clock clock;
    private final OperationsTokenCryptography crypto;
    private final OperationsSigningKeyRepository repository;

    /** 在事务外准备刷新 HMAC 和 Access 签名，不保存候选结果。 */
    PreparedTokens prepare(OperationsAccount account, OperationsPassword password, OperationsSession session,
        int generation, Instant now) {
        OperationsSigningKey key = repository.signingKey();
        if (key == null || key.notBefore().isAfter(now)) {
            throw new ServiceUnavailableException();
        }
        jwks.queryCurrentJwks();
        String lookup = randomValue(16);
        String secret = randomValue(32);
        String rawRefresh = lookup + "." + secret;
        var hmac = versionedHmac("operations-refresh-v1", rawRefresh);
        OperationsRefresh refresh = new OperationsRefresh(ids.nextId(), session.id(), generation, lookup,
            hmac.digest(), hmac.keyVersion(), OperationsRefreshStatus.ISSUED, session.absoluteExpireTime());
        String jwt = crypto.sign(key.keyRef(), key.kid(), key.publicJwk(), payload(account, session, now));
        TokenResult response = new TokenResult(jwt, "Bearer", 900, rawRefresh,
            Math.max(0, session.absoluteExpireTime().getEpochSecond() - clock.instant().getEpochSecond()),
            password.forceChange(), account.type() == OperationsAccountType.BOOTSTRAP_ADMIN && !password.forceChange());
        return new PreparedTokens(refresh, response);
    }

    /** 构造固定 Operations 最小 Claims，不放入邮箱或权限。 */
    String payload(OperationsAccount account, OperationsSession session, Instant now) {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("iss", "https://platform-support.local.test");
        claims.put("sub", Long.toString(account.id()));
        claims.put("aud", List.of("operations-console"));
        claims.put("token_use", "ACCESS");
        claims.put("sid", Long.toString(session.id()));
        claims.put("jti", Long.toString(ids.nextId()));
        claims.put("realm", "OPERATIONS");
        claims.put("channel", "OPERATIONS");
        claims.put("actor_type", "PLATFORM_USER");
        claims.put("context_type", session.contextType().getCode());
        claims.put("iat", now.getEpochSecond());
        claims.put("nbf", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(900).getEpochSecond());
        if (session.contextType() == OperationsContextType.TENANT) {
            claims.put("tenant_id", session.tenantId().toString());
            claims.put("tenant_type", "INTERNAL");
            claims.put("tenant_member_id", session.memberId().toString());
        }
        return json.writeValueAsString(claims);
    }

    /** 以相同 HMAC Key 版本和恒定时间比较证明刷新秘密。 */
    boolean matchesRefresh(String raw, OperationsRefresh refresh) {
        try {
            var hmac = versionedHmac("operations-refresh-v1", raw);
            if (refresh.keyVersion() != hmac.keyVersion()) {
                return false;
            }
            return java.security.MessageDigest.isEqual(
                hmac.digest().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                refresh.secretHmac().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException(exception);
        }
    }

    /** 保留签名依赖不可用语义及原异常原因。 */
    OperationsTokenCryptography.VersionedHmac versionedHmac(String purpose, String value) {
        try {
            return crypto.versionedHmac(purpose, value);
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException(exception);
        }
    }

    /** 生成原协议所需的高熵、无填充 URL-safe 随机段。 */
    String randomValue(int length) {
        byte[] value = new byte[length];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

}
