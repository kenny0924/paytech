package com.paytech.foundation.redis.test.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedisLocalEnvironmentPreconditionAutoTest {

    @Test
    void setupInstructionLoadsTheRepositoryEnvironmentAndMapsTheCanonicalPassword() {
        assertThat(RedisLocalEnvironmentPrecondition.setupInstruction()).contains("set -a")
            .contains("source ../local-environment/.env.local").contains("set +a")
            .contains("export PAYTECH_REDIS_PASSWORD=\"${PAYTECH_REDIS_PASSWORD:-$MOR_REDIS_PASSWORD}\"")
            .contains("docker compose --env-file ../local-environment/.env.local")
            .doesNotContain("redis-password", "requirepass");
    }
}
