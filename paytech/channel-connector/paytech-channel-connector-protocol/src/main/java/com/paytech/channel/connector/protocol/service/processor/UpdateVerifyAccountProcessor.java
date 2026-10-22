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
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyAccount;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelVerifyConfigService;

import lombok.AllArgsConstructor;
import com.paytech.channel.connector.protocol.service.processor.request.SaveVerifyAccountRequest;

/** V04 按当前版本整体修改验证账号：配置锁内锁定渠道与账号行，校验后条件更新并递增版本。 */
@Component
@AllArgsConstructor
public class UpdateVerifyAccountProcessor extends AbstractLockProcessor<SaveVerifyAccountRequest, ExternalApiMutation> {

    private final VerifySupport verify;
    private final ChannelConfigChangeService changeService;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelVerifyAccountRepository accounts;
    private final ChannelVerifyCaseRepository cases;
    private final ChannelVerifyConfigService service;
    private final ExternalApiConfigurationProperties properties;

    @Override
    protected LockCommand lockCommand(SaveVerifyAccountRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(SaveVerifyAccountRequest request) {
        return transactions.write(() -> {
            Channel integration = children.lockChannel(request.getIntegrationCode());
            ChannelVerifyAccount row = support.requireFound(accounts.lock(integration.getChannelId(),
                request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            support.requireValid(verify.accountIssues(request.getParameters(), request.getCredentials()));
            var before = new ChannelVerifyAccount(row.getVerifyAccountId(), row.getChannelId(),
                row.getAccountCode(), row.getAccountName(), row.getChannelCode(), row.getChannelAccountId(),
                row.getEntityNo(), row.getParameters(), row.getCredentials(), row.getVersion(), row.getCreatedBy(),
                row.getUpdatedBy(), row.getCreateTime(), row.getUpdateTime());
            var now = support.now();
            service.updateAccount(row, request.getName(), request.getChannelCode(), request.getChannelAccountId(),
                request.getEntityNo(), verify.mapJson(request.getParameters()), verify.mapJson(request.getCredentials()),
                request.getActorId(), now);
            String objectCode = integration.getChannelCode() + "/" + row.getAccountCode();
            var changes = CreateVerifyAccountProcessor.changes(before, row, codec);
            changeService.record(support.changed(ChangeTargetType.VERIFY_ACCOUNT, row.getVerifyAccountId(), ChangeType.UPDATE,
                request.getActorId(), objectCode, before.getVersion(), row.getVersion(), changes,
                request.getReason(), now));
            return new ExternalApiMutation(CreateVerifyAccountProcessor.OBJECT_TYPE, objectCode, row.getVersion());
        });
    }
}
