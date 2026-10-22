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

/**
 * V03 创建验证账号（沙箱验证技术方案第 3.1、5 节）：全模块配置锁内锁定渠道，在一个本地事务中完成
 * 参数与凭据校验、编码检查与保存。凭据是用户决定的非敏感沙箱材料，变更记录只记摘要。
 */
@Component
@AllArgsConstructor
public class CreateVerifyAccountProcessor extends AbstractLockProcessor<SaveVerifyAccountRequest, ExternalApiMutation> {

    /** 每个渠道的验证账号上限。 */
    static final int MAX_ACCOUNTS = 50;

    static final String OBJECT_TYPE = "INTEGRATION_VERIFY_ACCOUNT";

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
            support.requireValid(verify.accountIssues(request.getParameters(), request.getCredentials()));
            long channelId = integration.getChannelId();
            if (accounts.allByChannel(channelId).size() >= MAX_ACCOUNTS) {
                throw new BizCheckFailException("每个渠道最多 " + MAX_ACCOUNTS + " 个验证账号");
            }
            if (accounts.byCode(channelId, request.getCode()) != null) {
                throw new BizCheckFailException("验证账号编码已经存在：" + request.getCode());
            }
            long actorId = request.getActorId();
            var now = support.now();
            var row = new ChannelVerifyAccount(support.nextId(), channelId, request.getCode(),
                request.getName(), request.getChannelCode(), request.getChannelAccountId(), request.getEntityNo(),
                verify.mapJson(request.getParameters()), verify.mapJson(request.getCredentials()), 1, actorId,
                actorId, now, now);
            service.createAccount(row);
            String objectCode = integration.getChannelCode() + "/" + row.getAccountCode();
            var changes = changes(null, row, codec);
            changeService.record(support.changed(ChangeTargetType.VERIFY_ACCOUNT, row.getVerifyAccountId(),
                ChangeType.CREATE, actorId, objectCode, 0, 1, changes, request.getReason(), now));
            return new ExternalApiMutation(OBJECT_TYPE, objectCode, 1);
        });
    }

    /** 账号变更：编码、名称、渠道，参数含身份的摘要，凭据只记摘要。 */
    static List<ConfigurationFieldChange> changes(ChannelVerifyAccount before, ChannelVerifyAccount after,
        ExternalApiConfigurationCodec codec) {
        return List.of(
            new ConfigurationFieldChange(ConfigurationField.CODE, before == null ? null : before.getAccountCode(),
                after == null ? null : after.getAccountCode()),
            new ConfigurationFieldChange(ConfigurationField.DISPLAY_NAME, before == null ? null : before.getAccountName(),
                after == null ? null : after.getAccountName()),
            new ConfigurationFieldChange(ConfigurationField.CHANNEL, before == null ? null : before.getChannelCode(),
                after == null ? null : after.getChannelCode()),
            new ConfigurationFieldChange(ConfigurationField.PARAMETERS_DIGEST, digest(before, false, codec),
                digest(after, false, codec)),
            new ConfigurationFieldChange(ConfigurationField.DEFINITION_DIGEST, digest(before, true, codec),
                digest(after, true, codec)));
    }

    static String digest(ChannelVerifyAccount row, boolean credentials, ExternalApiConfigurationCodec codec) {
        if (row == null) {
            return null;
        }
        return credentials ? codec.sha256(row.getCredentials()) : codec.sha256(row.getChannelAccountId() + "\n"
            + row.getEntityNo() + "\n" + row.getParameters());
    }
}
