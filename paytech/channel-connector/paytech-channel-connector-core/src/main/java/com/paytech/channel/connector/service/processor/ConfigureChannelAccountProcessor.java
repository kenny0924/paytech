package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChangeType;
import com.paytech.channel.connector.domain.ChannelAccountConfigPayload;
import com.paytech.channel.connector.domain.ChannelAccountContext;
import com.paytech.channel.connector.domain.ChannelAccountDetailView;
import com.paytech.channel.connector.domain.ChannelConfigChanges;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.domainservice.service.ChannelAccountService;
import com.paytech.channel.connector.domainservice.service.PaymentChannelService;
import com.paytech.channel.connector.service.processor.request.ConfigureChannelAccountRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;

import lombok.AllArgsConstructor;

/**
 * 写入技术账号参数与凭据（Vault Protect + 本地短事务）
 * <li>锁 channel-account:{id}：读账号与渠道 → 未关闭 → 按与执行读取相同的规则校验键值 → 构造规范 JSON 与幂等键
 * channel-account-config.{id}.{sha256}（Vault 幂等键只允许 [A-Za-z0-9._~-]）→ 锁内、事务外 Protect（REFERENCE）→ 短事务以未关闭为条件写入 Token 与
 * CONFIGURATION 变更记录。同内容重放复用同一幂等键，Vault 返回同一 Token。</li>
 * <li>Vault 拒绝（FailException）与不可用（ErrorException）原样抛出，不写库；Protect 成功但写库失败时只记录账号 ID 并抛出，
 * 不重试，新 Token 成为孤儿（无 Retire 合同，登记遗留）。旧 Token 不回收。</li>
 * <li>参数与凭据不进入日志、异常信息或变更记录。</li>
 */
@Component
@AllArgsConstructor
public class ConfigureChannelAccountProcessor
    extends AbstractLockProcessor<ConfigureChannelAccountRequest, ChannelAccountDetailView> {
    private static final Logger log = LoggerFactory.getLogger(ConfigureChannelAccountProcessor.class);

    private final Clock clock;
    private final VaultClient vaultClient;
    private final IdGenerator idGenerator;
    private final ChannelMidRepository channelMidRepository;
    private final ChannelAccountService channelAccountService;
    private final PaymentChannelService paymentChannelService;

    /** 账号资源锁。 */
    @Override
    protected LockCommand lockCommand(ConfigureChannelAccountRequest request) {
        return LockCommand.tryLocks(List.of(ChannelConnectorLocks.channelAccount(request.getChannelAccountId())),
            ChannelConnectorLocks.CHANNEL_CONFIG_WAIT, ChannelConnectorLocks.CHANNEL_CONFIG_LEASE);
    }

    /** 只依赖输入的键值约束，与 ChannelAccountConfigReader.validateEntries 一致。 */
    @Override
    protected void validate(ConfigureChannelAccountRequest request) {
        checkEntries("参数", request.getParameters());
        checkEntries("凭据", request.getCredentials());
    }

    @Override
    protected ChannelAccountDetailView processWithinLock(ConfigureChannelAccountRequest request) {
        // 构建上下文
        ChannelAccountContext context = buildContext(request);
        ChannelAccountConfigPayload payload = ChannelAccountConfigPayload.of(context.getChannel(), context.getAccount(),
            request.getParameters(), request.getCredentials());
        if (payload.tooLarge()) {
            throw new BizCheckFailException("账号配置超过 32 KiB 上限: " + context.getAccount().getChannelAccountId());
        }

        // 事务外 Vault Protect
        String token = vaultClient.protect(new ProtectCommand(ChannelAccountConfigPayload.DATA_TYPE,
            TokenMode.REFERENCE, payload.value(), ChannelAccountConfigPayload.PURPOSE, payload.idempotencyKey())).token();

        // 本地保存
        saveToken(context, token);
        ChannelAccount account = context.getAccount();
        return new ChannelAccountDetailView(account, channelMidRepository.countOpenByAccount(account.getChannelAccountId()));
    }

    /** 读取账号与渠道并拒绝已关闭账号。 */
    ChannelAccountContext buildContext(ConfigureChannelAccountRequest request) {
        ChannelAccount account = channelAccountService.loadAccount(request.getChannelAccountId());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelAccountContext context = new ChannelAccountContext(account,
            paymentChannelService.loadChannel(account.getChannelId()), ChannelConfigChanges.of(idGenerator.nextId(),
                ChangeTargetType.CHANNEL_ACCOUNT, account.getChannelAccountId(), ChangeType.CONFIGURATION,
                request.getActorId(), now), now);
        if (context.closed()) {
            throw new BizCheckFailException("渠道技术账号已关闭，不能写入配置: " + account.getChannelAccountId());
        }
        return context;
    }

    /** 短事务写入 Token；失败时只记录账号 ID，原样抛出，不重试。 */
    void saveToken(ChannelAccountContext context, String token) {
        try {
            channelAccountService.updateVaultToken(context, token);
        } catch (RuntimeException failure) {
            log.warn("Vault Protect 已成功但账号配置引用写库失败，账号 {}", context.getAccount().getChannelAccountId());
            throw failure;
        }
    }

    /** 键 [A-Za-z][A-Za-z0-9_.-]{0,63}、值 1–8192 且非空白、最多 64 项；提示不含键值内容。 */
    static void checkEntries(String label, Map<String, String> entries) {
        if (entries == null || entries.size() > 64) {
            throw new InvalidParameterException("账号" + label + "集合缺失或超过 64 项");
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                || entry.getValue() == null || entry.getValue().isBlank() || entry.getValue().length() > 8192) {
                throw new InvalidParameterException("账号" + label + "名称或值不符合约束");
            }
        }
    }
}
