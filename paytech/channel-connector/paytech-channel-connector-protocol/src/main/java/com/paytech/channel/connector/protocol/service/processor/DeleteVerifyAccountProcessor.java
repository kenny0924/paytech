package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.core.enums.ConfigurationField;
import com.paytech.channel.connector.protocol.domain.ConfigurationFieldChange;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.service.IntegrationVerifyConfigService;

import lombok.AllArgsConstructor;
import com.paytech.channel.connector.protocol.service.processor.request.DeleteIntegrationChildRequest;

/** V05 按当前版本删除验证账号：仍被验证用例引用时拒绝。 */
@Component
@AllArgsConstructor
public class DeleteVerifyAccountProcessor extends AbstractLockProcessor<DeleteIntegrationChildRequest, ExternalApiMutation> {

    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationVerifyAccountRepository accounts;
    private final IntegrationVerifyCaseRepository cases;
    private final IntegrationVerifyConfigService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(DeleteIntegrationChildRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(DeleteIntegrationChildRequest request) {
        return transactions.write(() -> {
            Integration integration = children.lockIntegration(request.getIntegrationCode());
            IntegrationVerifyAccount row = support.requireFound(accounts.lock(integration.getIntegrationId(),
                request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            long users = cases.countByAccount(row.getVerifyAccountId());
            if (users > 0) {
                throw new BizCheckFailException("验证账号仍被 " + users + " 个验证用例引用，不能删除：" + row.getAccountCode());
            }
            int version = row.getVersion();
            service.deleteAccount(row);
            String objectCode = integration.getIntegrationCode() + "/" + row.getAccountCode();
            var changes = CreateVerifyAccountProcessor.changes(row, null, codec);
            changeService.record(support.changed(ChangeTargetType.VERIFY_ACCOUNT, row.getVerifyAccountId(), ChangeType.DELETE,
                request.getActorId(), objectCode, version, version, changes, request.getReason(), support.now()));
            return new ExternalApiMutation(CreateVerifyAccountProcessor.OBJECT_TYPE, objectCode, version);
        });
    }
}
