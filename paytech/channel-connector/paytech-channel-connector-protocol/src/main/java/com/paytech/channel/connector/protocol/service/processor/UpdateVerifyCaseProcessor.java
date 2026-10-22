package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyCase;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelVerifyConfigService;

import lombok.AllArgsConstructor;
import com.paytech.channel.connector.protocol.service.processor.request.SaveVerifyCaseRequest;

/** V09 按当前版本整体修改验证用例：配置锁内锁定渠道与用例行，校验后条件更新；已有运行记录保留。 */
@Component
@AllArgsConstructor
public class UpdateVerifyCaseProcessor extends AbstractLockProcessor<SaveVerifyCaseRequest, ExternalApiMutation> {

    private final VerifySupport verify;
    private final VerifyCaseChecks checks;
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
    protected LockCommand lockCommand(SaveVerifyCaseRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    @Override
    protected ExternalApiMutation processWithinLock(SaveVerifyCaseRequest request) {
        return transactions.write(() -> {
            Channel integration = children.lockChannel(request.getIntegrationCode());
            long channelId = integration.getChannelId();
            ChannelVerifyCase row = support.requireFound(cases.lock(channelId, request.getCode()));
            support.requireVersion(row.getVersion(), request.getExpectedVersion());
            ChannelVerifyAccount account = checks.account(channelId, request.getAccountCode());
            support.requireValid(checks.issues(integration.getChannelCode(), account, request.getSteps()));
            var before = new ChannelVerifyCase(row.getVerifyCaseId(), row.getChannelId(), row.getCaseCode(),
                row.getCaseName(), row.getDescription(), row.getVerifyAccountId(), row.getStepsJson(), row.getVersion(),
                row.getCreatedBy(), row.getUpdatedBy(), row.getCreateTime(), row.getUpdateTime());
            ChannelVerifyAccount previous = accounts.byId(before.getVerifyAccountId());
            var now = support.now();
            service.updateCase(row, request.getName(), request.getDescription(), account.getVerifyAccountId(),
                verify.text(request.getSteps()), request.getActorId(), now);
            String objectCode = integration.getChannelCode() + "/" + row.getCaseCode();
            var changes = VerifyCaseChecks.changes(before, previous == null ? null : previous.getAccountCode(), row,
                account.getAccountCode(), codec);
            changeService.record(support.changed(ChangeTargetType.VERIFY_CASE, row.getVerifyCaseId(), ChangeType.UPDATE,
                request.getActorId(), objectCode, before.getVersion(), row.getVersion(), changes,
                request.getReason(), now));
            return new ExternalApiMutation(CreateVerifyCaseProcessor.OBJECT_TYPE, objectCode, row.getVersion());
        });
    }
}
