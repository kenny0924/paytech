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

/**
 * V08 创建验证用例（沙箱验证技术方案第 4、5 节）：全模块配置锁内锁定渠道，在一个本地事务中复核权限、
 * 确认账号存在、静态校验步骤（标准 API 已由本渠道实现、模板变量、期望）并保存。
 */
@Component
@AllArgsConstructor
public class CreateVerifyCaseProcessor extends AbstractLockProcessor<SaveVerifyCaseRequest, ExternalApiMutation> {

    /** 每个渠道的验证用例上限。 */
    static final long MAX_CASES = 200;

    static final String OBJECT_TYPE = "INTEGRATION_VERIFY_CASE";

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
            ChannelVerifyAccount account = checks.account(channelId, request.getAccountCode());
            support.requireValid(checks.issues(integration.getChannelCode(), account, request.getSteps()));
            if (cases.count(channelId, null) >= MAX_CASES) {
                throw new BizCheckFailException("每个渠道最多 " + MAX_CASES + " 个验证用例");
            }
            if (cases.byCode(channelId, request.getCode()) != null) {
                throw new BizCheckFailException("验证用例编码已经存在：" + request.getCode());
            }
            long actorId = request.getActorId();
            var now = support.now();
            var row = new ChannelVerifyCase(support.nextId(), channelId, request.getCode(), request.getName(),
                request.getDescription(), account.getVerifyAccountId(), verify.text(request.getSteps()), 1, actorId,
                actorId, now, now);
            service.createCase(row);
            String objectCode = integration.getChannelCode() + "/" + row.getCaseCode();
            var changes = VerifyCaseChecks.changes(null, null, row, account.getAccountCode(), codec);
            changeService.record(support.changed(ChangeTargetType.VERIFY_CASE, row.getVerifyCaseId(), ChangeType.CREATE,
                actorId, objectCode, 0, 1, changes, request.getReason(), now));
            return new ExternalApiMutation(OBJECT_TYPE, objectCode, 1);
        });
    }
}
