package com.paytech.platform.support.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCommands.TargetWrite;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayTargetRequestBody;
import com.paytech.platform.support.service.processor.request.CreateGatewayTargetRequest;

import lombok.AllArgsConstructor;

/**
 * 新建 Gateway Target，固定保存为 DISABLED。
 * <li>持 Catalog 资源锁后在 SERIALIZABLE 事务内授权并保存；重复创建由唯一编码拒绝。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateGatewayTargetProcessor extends AbstractLockProcessor<CreateGatewayTargetRequest, ConfigurationMutation> {
    private final OperationsTransactionExecutor transactions;
    private final GatewayConfigurationManagementService management;

    /** 与 Catalog 其他写入口共用原注解锁的完整逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(CreateGatewayTargetRequest request) {
        return LockCommand.tryLocks(GatewayConfigurationLocks.CATALOG_KEYS, GatewayConfigurationLocks.WAIT_TIME,
            GatewayConfigurationLocks.LEASE_TIME);
    }

    @Override
    protected ConfigurationMutation processWithinLock(CreateGatewayTargetRequest request) {
        CreateGatewayTargetRequestBody body = request.getBody();
        TargetWrite command = new TargetWrite(body.targetServiceCode(), body.channel(), body.discoveryServiceName(), 0,
            body.reason());
        return GatewayConfigurationWrites.serializableWrite(transactions,
            () -> management.saveTarget(request.getActorId(), request.getSessionId(), command));
    }
}
