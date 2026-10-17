package com.paytech.platform.support.service.processor.internalauth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Attribute;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Event;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.enums.AuditActorType;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditWriter;
import com.paytech.platform.support.core.configuration.InternalTokenIssuerProperties;
import com.paytech.platform.support.core.internalauth.InternalTokenKeyRing;
import com.paytech.platform.support.service.processor.internalauth.request.IssueInternalTokenRequest;
import com.paytech.platform.support.service.processor.internalauth.response.IssueInternalTokenResponse;


/**
 * 内部调用 Token 签发（D26）：白名单授权 → 内存签名 → 审计 → 返回。
 * <li>不开数据库事务：签名在内存完成，审计写入使用审计模块自己的独立事务；本流程没有其他本地写入。</li>
 * <li>每次签发与每次拒绝都写一条 WORKLOAD 审计；审计写入失败时不返回 Token（失败关闭）。</li>
 * <li>不做业务防重：每次请求签发新 Token（新 jti），调用方自行缓存到过期前。</li>
 * <li>时间取 {@value #CLOCK} 墙钟：各服务的 Foundation 校验器按系统 UTC 时间判定 iat / exp，不能使用可替换的业务时钟。</li>
 */
@Component
@ConditionalOnProperty(name = "internal-token.issuer.enabled", havingValue = "true")
public class IssueInternalTokenProcessor
    extends AbstractProcessor<IssueInternalTokenRequest, IssueInternalTokenResponse> {

    /** 审计动作。 */
    static final String AUDIT_ACTION = "INTERNAL_TOKEN_ISSUE";

    /** 审计资源类型；成功时资源 ID 为 jti。 */
    static final String AUDIT_RESOURCE_TYPE = "INTERNAL_TOKEN";

    /** 签发墙钟 Bean 名。 */
    public static final String CLOCK = "internalTokenClock";

    private final Clock clock;
    private final InternalTokenKeyRing keyRing;
    private final OperationsAuditWriter auditWriter;
    private final InternalTokenIssuerProperties properties;

    /** 注入签发墙钟与密钥、审计、配置。 */
    public IssueInternalTokenProcessor(@Qualifier(CLOCK) Clock clock, InternalTokenKeyRing keyRing,
        OperationsAuditWriter auditWriter, InternalTokenIssuerProperties properties) {
        this.clock = clock;
        this.keyRing = keyRing;
        this.auditWriter = auditWriter;
        this.properties = properties;
    }

    @Override
    protected IssueInternalTokenResponse processRequest(IssueInternalTokenRequest request) {
        // 调用方授权
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (!properties.permits(request.getCallerService(), request.getTargetService(), request.getPurpose())) {
            audit(request, Result.DENIED, "DENIED", now);
            throw new BizCheckFailException("调用方未获准签发该目标服务或 Purpose 的内部 Token: "
                + request.getCallerService() + " -> " + request.getTargetService() + ", purpose=" + request.getPurpose());
        }

        // 内存签名
        InternalTokenClaims claims = new InternalTokenClaims(request.getCallerService(), request.getTargetService(),
            properties.getInstanceId(), request.getPurpose(), UUID.randomUUID().toString(), now,
            now.plus(properties.getTokenLifetime()));
        String token = keyRing.sign(claims);

        // 审计后返回
        audit(request, Result.SUCCEEDED, claims.jwtId(), now);
        return new IssueInternalTokenResponse(token, claims.expiresAt());
    }

    /** 写一条签发审计：主体为调用方服务，属性只含目标服务与 Purpose，不含 Token。 */
    void audit(IssueInternalTokenRequest request, Result result, String resourceId, Instant occurredAt) {
        auditWriter.write(new Event(new Identity(request.getCallerService(), null, AuditActorType.WORKLOAD),
            AUDIT_ACTION, new Target(AUDIT_RESOURCE_TYPE, resourceId), result,
            List.of(new Attribute("target_service", request.getTargetService()),
                new Attribute("purpose", request.getPurpose())),
            request.getRequestId(), occurredAt));
    }
}
