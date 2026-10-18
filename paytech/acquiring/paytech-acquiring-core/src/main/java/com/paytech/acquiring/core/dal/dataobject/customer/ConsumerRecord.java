package com.paytech.acquiring.core.dal.dataobject.customer;

import java.time.Instant;

/** Customer Checkout 本库持久化投影；禁止跨 HTTP 暴露。 */
public record ConsumerRecord(
        long consumerContextId,
        long orderId,
        String emailVaultToken,
        String consumerProfileVaultToken,
        String emailDomain,
        String billingCountryCode,
        String billingRegionCode,
        String billingCity,
        long version,
        Instant createTime,
        Instant updateTime) {}
