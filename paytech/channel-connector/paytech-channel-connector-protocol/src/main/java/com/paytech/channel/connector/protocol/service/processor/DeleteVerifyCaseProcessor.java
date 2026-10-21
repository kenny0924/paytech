package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyCase;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.service.IntegrationVerifyConfigService;

import lombok.AllArgsConstructor;
import com.paytech.channel.connector.protocol.service.processor.request.DeleteIntegrationChildRequest;

/** V10 按当前版本删除验证用例；运行记录不删除，按运行时快照展示。 */
@Component
@AllArgsConstructor
public class DeleteVerifyCaseProcessor extends AbstractLockProcessor<DeleteIntegrationChildRequest, ExternalApiMutation> {

    private final VerifySupport verify;
    private final VerifyCaseChecks checks;
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
            IntegrationVerifyCase row = support.requireFound(cases.lock(integration.getIntegrationId(), request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            IntegrationVerifyAccount account = accounts.byId(row.getVerifyAccountId());
            int version = row.getVersion();
            service.deleteCase(row);
            String objectCode = integration.getIntegrationCode() + "/" + row.getCaseCode();
            var changes = VerifyCaseChecks.changes(row, account == null ? null : account.getAccountCode(), null, null,
                codec);
            changeService.record(support.changed(ChangeTargetType.VERIFY_CASE, row.getVerifyCaseId(), ChangeType.DELETE,
                request.getActorId(), objectCode, version, version, changes, request.getReason(), support.now()));
            return new ExternalApiMutation(CreateVerifyCaseProcessor.OBJECT_TYPE, objectCode, version);
        });
    }
}
