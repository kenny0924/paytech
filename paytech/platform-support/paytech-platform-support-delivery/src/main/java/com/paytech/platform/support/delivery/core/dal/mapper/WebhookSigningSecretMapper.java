package com.paytech.platform.support.delivery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookSigningSecret;

/** Webhook 签名密钥单表 SQL 绑定。 */
public interface WebhookSigningSecretMapper {

    /** 按轮换序号读取端点的全部密钥。 */
    List<WebhookSigningSecret> findEndpoint(@Param("endpointId") long endpointId);

    /** 读取一组端点在给定时刻仍可签名的密钥。 */
    List<WebhookSigningSecret> findSigning(@Param("endpointIds") List<Long> endpointIds, @Param("now") Instant now);

    /** 插入新密钥。 */
    int insert(WebhookSigningSecret item);

    /** 按旧版本把 ACTIVE 密钥退役并设定签名截止时间。 */
    int retire(@Param("item") WebhookSigningSecret item, @Param("validUntil") Instant validUntil,
        @Param("now") Instant now);

    /** 按旧版本把仍在宽限内的 RETIRED 密钥截止时间提前到 now。 */
    int expire(@Param("item") WebhookSigningSecret item, @Param("now") Instant now);
}
