package com.paytech.gateway.auto.web;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.gateway.web.GatewayPaths;

class GatewayPathsAutoTest {

    @Test
    void test_operationsAuthPrefixExtendsOperationsApiPrefix() {
        Assertions.assertAll(() -> Assertions.assertEquals("/api/operations", GatewayPaths.OPERATIONS_API_PREFIX),
            () -> Assertions.assertEquals(GatewayPaths.OPERATIONS_API_PREFIX + "/auth/v1",
                GatewayPaths.OPERATIONS_AUTH_PREFIX));
    }
}
