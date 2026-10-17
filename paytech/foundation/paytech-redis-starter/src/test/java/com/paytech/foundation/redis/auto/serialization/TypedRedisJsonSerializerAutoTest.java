package com.paytech.foundation.redis.auto.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.cache.TypedRedisJsonSerializer;
import com.paytech.foundation.redis.core.error.RedisErrorCode;
import com.paytech.foundation.redis.core.serialization.RedisJsonObjectMapperFactory;

class TypedRedisJsonSerializerAutoTest {

    private final TypedRedisJsonSerializer serializer =
        new TypedRedisJsonSerializer("payment-cache", CachedPayment.class, RedisJsonObjectMapperFactory.create());

    @Test
    void serializesPlainJsonAndRestoresDeclaredType() {
        CachedPayment payment = new CachedPayment("payment-1001", PaymentStatus.PROCESSING);

        byte[] serialized = serializer.serialize(payment);
        String json = new String(serialized, StandardCharsets.UTF_8);

        assertThat(json).isEqualTo("{\"paymentId\":\"payment-1001\",\"status\":\"payment_processing\"}")
            .doesNotContain("@class", "_type", CachedPayment.class.getName(), "PROCESSING");
        assertThat(serializer.deserialize(serialized)).isEqualTo(payment);
        assertThat(serializer.getTargetType()).isEqualTo(CachedPayment.class);
    }

    @Test
    void ignoresUnknownFieldsForRollingUpgrade() {
        byte[] futureJson = """
            {"paymentId":"payment-1001","status":"payment_processing","futureField":true}
            """.strip().getBytes(StandardCharsets.UTF_8);

        assertThat(serializer.deserialize(futureJson))
            .isEqualTo(new CachedPayment("payment-1001", PaymentStatus.PROCESSING));
    }

    @Test
    void rejectsValueThatDoesNotMatchRegisteredType() {
        assertThatThrownBy(() -> serializer.serialize("payment-1001")).isInstanceOf(FailException.class)
            .hasMessageContaining(CachedPayment.class.getSimpleName())
            .extracting(exception -> ((FailException)exception).getCode())
            .isEqualTo(RedisErrorCode.CACHE_VALUE_TYPE_MISMATCH.getCode());
    }

    @Test
    void rejectsNullValueExplicitly() {
        assertThatThrownBy(() -> serializer.serialize(null)).isInstanceOf(FailException.class)
            .hasMessage("Redis Cache[payment-cache]不允许写入null")
            .extracting(exception -> ((FailException)exception).getCode())
            .isEqualTo(RedisErrorCode.CACHE_VALUE_TYPE_MISMATCH.getCode());
    }

    private enum PaymentStatus implements CodeEnum {

        PROCESSING("payment_processing");

        private final String code;

        PaymentStatus(String code) {
            this.code = code;
        }

        @Override
        public String getCode() {
            return code;
        }
    }

    private record CachedPayment(String paymentId, PaymentStatus status) {
    }
}
