package com.paytech.platform.support.delivery.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookSigningSecret;
import com.paytech.platform.support.delivery.core.dal.mapper.WebhookSigningSecretMapper;
import com.paytech.platform.support.delivery.core.enums.WebhookSecretStatus;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookSigningSecretRepository;

import lombok.RequiredArgsConstructor;

/** 单表访问及写入行数保护，不自行创建事务；Vault Token 不进入异常信息。 */
@Repository
@RequiredArgsConstructor
public class WebhookSigningSecretRepositoryImpl implements WebhookSigningSecretRepository {

    private final WebhookSigningSecretMapper mapper;

    /** 按轮换序号读取端点的全部密钥。 */
    @Override
    public List<WebhookSigningSecret> findEndpoint(long endpointId) {
        return mapper.findEndpoint(endpointId);
    }

    /** 读取一组端点在给定时刻仍可签名的密钥；空集合直接返回空列表。 */
    @Override
    public List<WebhookSigningSecret> findSigning(List<Long> endpointIds, Instant now) {
        if (endpointIds.isEmpty()) {
            return List.of();
        }
        return mapper.findSigning(endpointIds, now);
    }

    /** 插入新密钥。 */
    @Override
    public void insert(WebhookSigningSecret item) {
        if (mapper.insert(item) != 1) {
            throw new ErrorException("Webhook 签名密钥插入失败");
        }
    }

    /** 按旧版本把 ACTIVE 密钥退役；成功后同步原对象。 */
    @Override
    public void retire(WebhookSigningSecret item, Instant validUntil, Instant now) {
        if (mapper.retire(item, validUntil, now) != 1) {
            throw new ConcurrentRequestException("Webhook 签名密钥已被轮换，请刷新后重试");
        }
        item.setStatus(WebhookSecretStatus.RETIRED);
        item.setSigningSlot(null);
        item.setValidUntil(validUntil);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }

    /** 按旧版本把宽限中的 RETIRED 密钥立即截止；成功后同步原对象。 */
    @Override
    public void expire(WebhookSigningSecret item, Instant now) {
        if (mapper.expire(item, now) != 1) {
            throw new ConcurrentRequestException("Webhook 签名密钥已被轮换，请刷新后重试");
        }
        item.setValidUntil(now);
        item.setVersion(item.getVersion() + 1);
        item.setUpdateTime(now);
    }
}
