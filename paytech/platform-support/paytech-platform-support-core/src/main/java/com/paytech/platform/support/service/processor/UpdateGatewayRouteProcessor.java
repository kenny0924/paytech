package com.paytech.platform.support.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCommands.Guard;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCommands.Header;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCommands.RouteConfiguration;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCommands.RouteWrite;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayRouteRequestBody;
import com.paytech.platform.support.service.processor.request.UpdateGatewayRouteRequest;

import lombok.AllArgsConstructor;

/**
 * 全量更新 Gateway Route。
 * <li>身份来自 Path，版本必须为正数；固定 Channel 和状态从事务内当前记录取得。</li>
 * <li>Header、Query 与 Guard 子项按请求顺序规范化；持 Catalog 资源锁后在 SERIALIZABLE 事务内授权并保存完整聚合。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateGatewayRouteProcessor extends AbstractLockProcessor<UpdateGatewayRouteRequest, ConfigurationMutation> {
    private final OperationsTransactionExecutor transactions;
    private final GatewayConfigurationManagementService management;

    /** 与 Catalog 其他写入口共用原注解锁的完整逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(UpdateGatewayRouteRequest request) {
        return LockCommand.tryLocks(GatewayConfigurationLocks.CATALOG_KEYS, GatewayConfigurationLocks.WAIT_TIME,
            GatewayConfigurationLocks.LEASE_TIME);
    }

    @Override
    protected ConfigurationMutation processWithinLock(UpdateGatewayRouteRequest request) {
        UpdateGatewayRouteRequestBody r = request.getBody();
        GatewayConfigurationWrites.requireVersion(r.expectedVersion());
        RouteWrite command = new RouteWrite(request.getRouteCode(), null, configuration(r), r.expectedVersion(), r.reason());
        return GatewayConfigurationWrites.serializableWrite(transactions,
            () -> management.saveRoute(request.getActorId(), request.getSessionId(), command));
    }

    /** 请求体转为路由配置；权限 ID 按十进制主键解析。 */
    RouteConfiguration configuration(UpdateGatewayRouteRequestBody r) {
        return new RouteConfiguration(r.httpMethod(), r.externalPathTemplate(), r.downstreamPathTemplate(),
            r.targetServiceCode(), r.securityProfileCode(), r.authorizationMode(),
            GatewayConfigurationWrites.permissionId(r.permissionId()), r.requiredScopeCode(), r.requestCookieMode(),
            r.responseCookieMode(), r.idempotencyMode(), r.bodyLimitBytes(), r.bodyReceiveTimeoutMillis(),
            r.responseTimeoutMillis(), r.headers().stream().map(h -> new Header(h.headerName(), h.headerUse())).toList(),
            r.queryParameterNames(), r.guardRules().stream().map(g -> new Guard(g.ruleType(), g.dimension(),
                g.capacity(), g.refillTokens(), g.refillPeriodMillis(), g.concurrencyLimit())).toList());
    }
}
