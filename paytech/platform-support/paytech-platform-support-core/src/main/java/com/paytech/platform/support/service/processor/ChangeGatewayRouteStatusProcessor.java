package com.paytech.platform.support.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteStatusCommand;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteStatusResult;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayRouteStatusRequestBody;
import com.paytech.platform.support.service.processor.request.ChangeGatewayRouteStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 启停 Gateway Route。
 * <li>持 Catalog 资源锁后规范化命令，在 SERIALIZABLE 事务内授权、校验版本并保存；事务内无外部调用。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeGatewayRouteStatusProcessor
    extends AbstractLockProcessor<ChangeGatewayRouteStatusRequest, RouteStatusResult> {
    private final OperationsTransactionExecutor transactions;
    private final GatewayConfigurationManagementService management;

    /** 与 Catalog 其他写入口共用原注解锁的完整逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(ChangeGatewayRouteStatusRequest request) {
        return LockCommand.tryLocks(GatewayConfigurationLocks.CATALOG_KEYS, GatewayConfigurationLocks.WAIT_TIME,
            GatewayConfigurationLocks.LEASE_TIME);
    }

    @Override
    protected RouteStatusResult processWithinLock(ChangeGatewayRouteStatusRequest request) {
        RouteStatusCommand command = GatewayConfigurationWrites.normalize(command(request));
        return GatewayConfigurationWrites.serializableWrite(transactions, () -> management.changeRouteStatus(command));
    }

    /** 请求体为空时以 0 版本交给规范化拒绝。 */
    RouteStatusCommand command(ChangeGatewayRouteStatusRequest request) {
        ChangeGatewayRouteStatusRequestBody body = request.getBody();
        return new RouteStatusCommand(request.getActorId(), request.getSessionId(), request.getRouteCode(),
            body == null ? null : body.status(), body == null ? 0 : body.expectedVersion(), body == null ? null : body.reason());
    }
}
