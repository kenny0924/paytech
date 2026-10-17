package com.paytech.client.master.service.handler;

import java.time.Duration;
import com.paytech.client.master.domain.onboarding.ClientProjectionModels.*;
import com.paytech.client.master.domainservice.service.ClientProjectionSourceService;
import com.paytech.client.master.integration.ClientTenantProjectionClient;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.compensation.api.*;

/** CCT 本地事实到 Platform PLT-I17 的有界恢复；不访问表单、Risk 或 Vault。 */
public final class ClientTenantProjectionHandler implements CompensationEventHandler {
    public static final String EVENT_TYPE = "CLIENT_TENANT_PROJECTION";
    private final ClientProjectionSourceService sources;
    private final ClientTenantProjectionClient platform;
    public ClientTenantProjectionHandler(ClientProjectionSourceService sources, ClientTenantProjectionClient platform) {
        this.sources = sources; this.platform = platform;
    }
    /** 纯工厂：只能与原 CLIENT BCR 明细在同一本地事务登记。 */
    public static CompensationEventRequest buildEvent(long changeVersionId) {
        if (changeVersionId <= 0) { throw new IllegalArgumentException("Invalid Client history identity"); }
        return new CompensationEventRequest(Long.toString(changeVersionId), EVENT_TYPE, "-");
    }
    @Override public String eventType() { return EVENT_TYPE; }
    @Override public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(25), 12, Duration.ofSeconds(5), Duration.ofMinutes(30), 2, 0.2);
    }
    @Override public CompensationHandlerResult handle(CompensationEvent event) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("CLIENT_PROJECTION_TRANSACTION_ACTIVE", "Projection execution must run outside a transaction");
        }
        try {
            if (!event.eventKey().matches("[1-9][0-9]{0,18}") || event.payloadJson() != null) { return invalid(); }
            Source source = sources.source(Long.parseLong(event.eventKey()));
            Position position = platform.find(source.clientId());
            if (position != null && position.sourceVersion() >= source.sourceVersion()) { return verify(source, position); }
            if (position != null) {
                Source previous = sources.sourceAt(source.clientId(), position.sourceVersion());
                if (!matches(previous, position)) { return conflict(); }
            }
            return verify(source, platform.project(source));
        } catch (IllegalArgumentException corruptSource) { return invalid(); }
        catch (FailException failure) {
            if ("TENANT_PROJECTION_STALE".equals(failure.getCode())) { return retry(); }
            return CompensationHandlerResult.terminalFailure("CLIENT_PROJECTION_REJECTED", "Platform rejected the Client projection");
        } catch (RuntimeException unavailable) {
            // 不将上游正文、SQL 原文或未知异常信息复制到任务错误摘要。
            return retry();
        }
    }
    private CompensationHandlerResult verify(Source source, Position position) {
        if (position == null || position.sourceVersion() < source.sourceVersion()) { return conflict(); }
        Source exact = position.sourceVersion() == source.sourceVersion() ? source
            : sources.sourceAt(source.clientId(), position.sourceVersion());
        return matches(exact, position) ? CompensationHandlerResult.success() : conflict();
    }
    private boolean matches(Source source, Position position) {
        return source.clientId() == position.clientId() && source.applicantId() == position.applicantId()
            && source.sourceVersion() == position.sourceVersion() && source.status().equals(position.status())
            && source.payloadHash().equals(position.payloadHash()) && position.tenantId() > 0 && position.memberId() > 0;
    }
    private CompensationHandlerResult invalid() {
        return CompensationHandlerResult.terminalFailure("CLIENT_PROJECTION_SOURCE_INVALID", "Client projection source requires investigation");
    }
    private CompensationHandlerResult conflict() {
        return CompensationHandlerResult.terminalFailure("CLIENT_PROJECTION_CONFLICT", "Platform projection differs from the immutable CCT source");
    }
    private CompensationHandlerResult retry() {
        return CompensationHandlerResult.retryableFailure("CLIENT_PROJECTION_UNAVAILABLE", "Client projection outcome requires recovery");
    }
}
