package com.paytech.channel.connector.protocol.service.processor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.protocol.core.configuration.ExternalApiConfigurationProperties;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.core.enums.ConfigurationField;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domain.ConfigurationFieldChange;
import com.paytech.channel.connector.protocol.domain.EndpointDefinition;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.UpdateChannelEndpointsContext;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelApiRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationEndpointValidator;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateChannelEndpointsRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 整组替换渠道出站地址（原接入方修改的出站地址部分）
 * <li>与原子 API 写入共用外部 API 配置锁：锁内读取的地址引用在提交前不会变化，仍被原子 API 引用的地址代码不能删除（409）。</li>
 * <li>事务内锁定渠道行、核对版本（不一致 409）后条件更新并递增版本；同事务写入 CHANNEL / CONFIGURATION 变更记录，只记地址摘要。</li>
 * <li>地址静态规则（数量、代码唯一、协议、URL 形状、被拦截的 IP 字面量与 localhost）不通过时 422，字段位置为 endpoints[i].*。</li>
 */
@Component
@AllArgsConstructor
public class UpdateChannelEndpointsProcessor
    extends AbstractLockProcessor<UpdateChannelEndpointsRequest, ChannelIdentity> {
    private final ChannelService channelService;
    private final ChannelApiRepository channelApis;
    private final ExternalApiConfigurationCodec codec;
    private final ChannelRepository channelRepository;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelConfigChangeService changeService;
    private final IntegrationEndpointValidator validator;
    private final ExternalApiConfigurationProperties properties;

    /** 外部 API 配置锁：与原子 API 的出站地址引用写入互斥。 */
    @Override
    protected LockCommand lockCommand(UpdateChannelEndpointsRequest request) {
        return LockCommand.tryLock(ExternalApiLocks.LOCK_KEY,
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 校验并替换出站地址，写入变更记录。 */
    @Override
    protected ChannelIdentity processWithinLock(UpdateChannelEndpointsRequest request) {
        // 静态校验（与渠道当前状态无关）
        support.requireValid(validator.validate(request.getEndpoints()));
        String endpointsJson = codec.endpointsJson(request.getEndpoints());

        // 锁行、核对版本与引用后本地保存
        return transactions.write(() -> {
            Channel channel = lockOpenChannel(request.getChannelId());
            support.requireVersion(channel.getVersion(), request.getExpectedVersion());
            requireReferencedEndpointsKept(channel, request.getEndpoints());
            Instant now = support.now();
            String before = channel.getEndpoints();
            int beforeVersion = channel.getVersion();
            channelService.updateChannelEndpoints(new UpdateChannelEndpointsContext(channel, endpointsJson,
                request.getActorId(), now));
            changeService.record(support.changed(ChangeTargetType.CHANNEL, channel.getChannelId(),
                ChangeType.CONFIGURATION, request.getActorId(), channel.getChannelCode(), beforeVersion,
                channel.getVersion(), List.of(new ConfigurationFieldChange(ConfigurationField.ENDPOINTS_DIGEST,
                    endpointsDigest(before), codec.sha256(endpointsJson))), null, now));
            return ChannelIdentity.of(channel, request.getEndpoints());
        });
    }

    /** 已保存地址的摘要：先解析再按写入时的格式序列化，避免数据库 JSON 文本格式（键序、空格）使前后摘要不可比。 */
    String endpointsDigest(String savedEndpoints) {
        if (savedEndpoints == null) {
            return null;
        }
        return codec.sha256(codec.endpointsJson(codec.endpoints(savedEndpoints)));
    }

    /** 锁定未关闭的渠道行；不存在或已关闭业务拒绝。 */
    Channel lockOpenChannel(long channelId) {
        Channel channel = channelRepository.lockById(channelId);
        if (channel == null) {
            throw new BizCheckFailException("渠道不存在: " + channelId);
        }
        if (channel.getChannelStatus() == ChannelStatus.CLOSED) {
            throw new BizCheckFailException("渠道已关闭，不能修改出站地址: " + channelId);
        }
        return channel;
    }

    /** 新地址列表缺少仍被原子 API 引用的代码时拒绝（409），原因中列出引用方。 */
    void requireReferencedEndpointsKept(Channel channel, List<EndpointDefinition> endpoints) {
        Set<String> kept = endpoints.stream().map(EndpointDefinition::getEndpointCode).collect(Collectors.toSet());
        var broken = channelApis.endpointReferences(channel.getChannelId()).stream()
            .filter(reference -> reference.getEndpointCode() != null && !kept.contains(reference.getEndpointCode()))
            .map(reference -> reference.getApiCode() + "（" + reference.getEndpointCode() + "）").toList();
        if (!broken.isEmpty()) {
            throw new ConcurrentRequestException("出站地址仍被原子 API 引用，不能删除: " + String.join("、", broken));
        }
    }
}
