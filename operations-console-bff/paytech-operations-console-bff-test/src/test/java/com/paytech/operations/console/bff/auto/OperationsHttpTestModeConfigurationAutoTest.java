package com.paytech.operations.console.bff.auto;

import com.paytech.operations.console.bff.configuration.OperationsBrowserProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** HTTP 明文例外是启动配置约束，不能随请求或附加 Profile 生效。 */
class OperationsHttpTestModeConfigurationAutoTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "none|false|https://operations-console.local.test",
        "prod|false|https://operations-console.local.test",
        "test|false|https://operations-console.local.test",
        "test|true|http://localhost:1",
        "test|true|http://localhost:28080",
        "test|true|http://localhost:65535"
    })
    void test_onlyExplicitTestModeAllowsCanonicalLocalhostHttp(String profiles, boolean enabled, String origin) {
        assertDoesNotThrow(() -> boundary(profiles, enabled, origin));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "none|true|http://localhost:28080",
        "auto|true|http://localhost:28080",
        "local|true|http://localhost:28080",
        "prod|true|http://localhost:28080",
        "test,prod|true|http://localhost:28080",
        "test,auto|true|http://localhost:28080",
        "prod|true|https://operations-console.local.test",
        "test|false|http://localhost:28080",
        "test|true|https://localhost:28080",
        "test|true|http://127.0.0.1:28080",
        "test|true|http://localhost.example:28080",
        "test|true|http://localhost:0",
        "test|true|http://localhost:65536",
        "test|true|http://localhost",
        "test|true|http://localhost:028080",
        "test|true|http://localhost:28080/",
        "test|true|http://localhost:28080?debug=true",
        "test|true|http://localhost:28080#fragment",
        "test|true|http://user@localhost:28080"
    })
    void test_invalidProfileOriginOrPortFailsBeforeServingRequests(String profiles, boolean enabled, String origin) {
        assertThrows(IllegalArgumentException.class, () -> boundary(profiles, enabled, origin));
    }

    private OperationsBrowserProperties boundary(String profiles, boolean enabled, String origin) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("none".equals(profiles) ? new String[0] : profiles.split(","));
        OperationsBrowserProperties properties = new OperationsBrowserProperties(origin, enabled);
        properties.validateProfiles(environment.getActiveProfiles());
        return properties;
    }
}
