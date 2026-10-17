package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import java.time.Instant;

import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.TenantProjected;

/** 来源投影只记录稳定身份及授权效果，不保存来源 Payload 或个人信息。 */
public final class TenantProjectionAuditFacts {
    private TenantProjectionAuditFacts() {}

    /** 来源位置已经冻结到结果对象，审计事实不再依赖可变持久化对象。 */
    public static TenantProjected projected(TenantProjectionResult result, boolean created, boolean activated, Instant now) {
        return new TenantProjected(new Identity(Long.toString(result.getRequestedByAccountId()), result.getTenantId()),
            new Target("ACCESS_TENANT", Long.toString(result.getTenantId())), result.getOwnerRefId(), result.getMemberId(),
            result.getSourceVersion(), result.getOwnerStatus().getCode(), created, activated, now);
    }
}
