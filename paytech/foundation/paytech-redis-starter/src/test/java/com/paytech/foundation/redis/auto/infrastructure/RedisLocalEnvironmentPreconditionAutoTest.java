package com.paytech.foundation.redis.auto.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.redis.test.infrastructure.RedisLocalEnvironmentPrecondition;

/**
 * 本地 Redis 前置检查的提示文案：从 {@code paytech/foundation} 出发定位仓库级 local-environment。
 *
 * @see RedisLocalEnvironmentPrecondition#setupInstruction()
 */
class RedisLocalEnvironmentPreconditionAutoTest {

    @Test
    void setupInstructionLoadsTheRepositoryEnvironmentAndMapsTheCanonicalPassword() {
        assertThat(RedisLocalEnvironmentPrecondition.setupInstruction()).contains("From paytech/foundation run:")
            .contains("set -a").contains("source ../../local-environment/.env.local").contains("set +a")
            .contains("export PAYTECH_REDIS_PASSWORD=\"${PAYTECH_REDIS_PASSWORD:-$MOR_REDIS_PASSWORD}\"")
            .contains("docker compose --env-file ../../local-environment/.env.local")
            .contains("-f ../../local-environment/compose.yaml")
            .doesNotContain("paytech-foundation", "redis-password", "requirepass");
    }
}
