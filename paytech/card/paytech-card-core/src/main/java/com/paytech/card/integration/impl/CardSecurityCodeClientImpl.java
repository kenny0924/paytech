package com.paytech.card.integration.impl;

import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.card.integration.CardSecurityCodeClient;

import lombok.AllArgsConstructor;

/**
 * 以 Redis 暂存加密 CVV；Key 由 Foundation 自动加服务前缀。
 *
 * <p>
 * 领取使用 GETDEL，同一 CVV 并发领取只有一方取得。Redis 不可用时失败关闭，不回退到其他介质。
 */
@Component
@AllArgsConstructor
public class CardSecurityCodeClientImpl implements CardSecurityCodeClient {
    private final StringRedisTemplate redisTemplate;

    /** 写入并设置存活时间。 */
    @Override
    public void save(long paymentCardInstrumentId, String encryptedSecurityCode, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(paymentCardInstrumentId), encryptedSecurityCode, ttl);
        } catch (DataAccessException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
    }

    /** 原子取出并删除。 */
    @Override
    public String claim(long paymentCardInstrumentId) {
        try {
            return redisTemplate.opsForValue().getAndDelete(key(paymentCardInstrumentId));
        } catch (DataAccessException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
    }

    /** CVV 的 Redis Key，不含卡值。 */
    public static String key(long paymentCardInstrumentId) {
        return "card:cvv:" + paymentCardInstrumentId;
    }
}
