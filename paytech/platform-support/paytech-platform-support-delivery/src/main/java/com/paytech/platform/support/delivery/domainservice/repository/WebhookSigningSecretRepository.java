package com.paytech.platform.support.delivery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookSigningSecret;

/** WebhookSigningSecret 单表仓储。 */
public interface WebhookSigningSecretRepository {

    /** 按轮换序号读取端点的全部密钥。 */
    List<WebhookSigningSecret> findEndpoint(long endpointId);

    /** 读取一组端点在给定时刻仍可签名的密钥；空集合直接返回空列表。 */
    List<WebhookSigningSecret> findSigning(List<Long> endpointIds, Instant now);

    /** 插入新密钥；同一端点第二把 ACTIVE 由唯一键拒绝。 */
    void insert(WebhookSigningSecret item);

    /** 按旧版本把 ACTIVE 密钥退役；成功后同步原对象。 */
    void retire(WebhookSigningSecret item, Instant validUntil, Instant now);

    /** 按旧版本把宽限中的 RETIRED 密钥立即截止；成功后同步原对象。 */
    void expire(WebhookSigningSecret item, Instant now);
}
