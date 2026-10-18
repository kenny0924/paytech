package com.paytech.platform.support.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCommands.TargetWrite;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayTargetRequestBody;
import com.paytech.platform.support.service.processor.request.UpdateGatewayTargetRequest;

import lombok.AllArgsConstructor;

/**
 * 更新 Gateway Target 的服务发现名。
 * <li>身份来自 Path，版本必须为正数，不能隐式转为创建。</li>
 * <li>持 Catalog 资源锁后在 SERIALIZABLE 事务内授权、校验版本并保存。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateGatewayTargetProcessor extends AbstractLockProcessor<UpdateGatewayTargetRequest, ConfigurationMutation> {
    private final OperationsTransactionExecutor transactions;
    private final GatewayConfigurationManagementService management;

    /** 与 Catalog 其他写入口共用原注解锁的完整逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(UpdateGatewayTargetRequest request) {
        return LockCommand.tryLocks(GatewayConfigurationLocks.CATALOG_KEYS, GatewayConfigurationLocks.WAIT_TIME,
            GatewayConfigurationLocks.LEASE_TIME);
    }

    @Override
    protected ConfigurationMutation processWithinLock(UpdateGatewayTargetRequest request) {
        UpdateGatewayTargetRequestBody body = request.getBody();
        GatewayConfigurationWrites.requireVersion(body.expectedVersion());
        TargetWrite command = new TargetWrite(request.getTargetServiceCode(), null, body.discoveryServiceName(),
            body.expectedVersion(), body.reason());
        return GatewayConfigurationWrites.serializableWrite(transactions,
            () -> management.saveTarget(request.getActorId(), request.getSessionId(), command));
    }
}
