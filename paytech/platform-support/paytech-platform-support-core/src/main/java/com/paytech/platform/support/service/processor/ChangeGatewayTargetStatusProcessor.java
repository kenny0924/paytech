package com.paytech.platform.support.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetStatusCommand;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetStatusResult;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayTargetStatusRequestBody;
import com.paytech.platform.support.service.processor.request.ChangeGatewayTargetStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 启停 Gateway Target。
 * <li>持 Catalog 资源锁后规范化命令，在 SERIALIZABLE 事务内授权、校验版本并保存；事务内无外部调用。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeGatewayTargetStatusProcessor
    extends AbstractLockProcessor<ChangeGatewayTargetStatusRequest, TargetStatusResult> {
    private final OperationsTransactionExecutor transactions;
    private final GatewayConfigurationManagementService management;

    /** 与 Catalog 其他写入口共用原注解锁的完整逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(ChangeGatewayTargetStatusRequest request) {
        return LockCommand.tryLocks(GatewayConfigurationLocks.CATALOG_KEYS, GatewayConfigurationLocks.WAIT_TIME,
            GatewayConfigurationLocks.LEASE_TIME);
    }

    @Override
    protected TargetStatusResult processWithinLock(ChangeGatewayTargetStatusRequest request) {
        TargetStatusCommand command = GatewayConfigurationWrites.normalize(command(request));
        return GatewayConfigurationWrites.serializableWrite(transactions, () -> management.changeTargetStatus(command));
    }

    /** 请求体为空时以 0 版本交给规范化拒绝。 */
    TargetStatusCommand command(ChangeGatewayTargetStatusRequest request) {
        ChangeGatewayTargetStatusRequestBody body = request.getBody();
        return new TargetStatusCommand(request.getActorId(), request.getSessionId(), request.getTargetServiceCode(),
            body == null ? null : body.status(), body == null ? 0 : body.expectedVersion(), body == null ? null : body.reason());
    }
}
