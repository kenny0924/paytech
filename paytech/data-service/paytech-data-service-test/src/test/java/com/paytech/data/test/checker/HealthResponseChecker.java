package com.paytech.data.test.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Actuator 健康响应断言。
 *
 * @author Claude
 */
public final class HealthResponseChecker {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JsonNode body;

    private HealthResponseChecker(JsonNode body) {
        this.body = body;
    }

    /**
     * 读取一次健康响应，先断言 HTTP 200。
     *
     * @param result 原始响应
     * @return Checker
     */
    public static HealthResponseChecker of(EntityExchangeResult<byte[]> result) {
        assertEquals(HttpStatus.OK, result.getStatus(), "健康检查 HTTP 状态");
        return new HealthResponseChecker(JSON.readTree(result.getResponseBody()));
    }

    /**
     * 断言整体状态。
     *
     * @param status 期望状态，如 {@code UP}
     * @return 本 Checker
     */
    public HealthResponseChecker status(String status) {
        assertEquals(status, body.path("status").asString(), "健康检查整体状态");
        return this;
    }

    /**
     * 断言组件存在且状态匹配。
     *
     * @param component 组件名
     * @param status 期望状态
     * @return 本 Checker
     */
    public HealthResponseChecker component(String component, String status) {
        JsonNode node = body.path("components").path(component);
        assertTrue(node.isObject(), "健康组件不存在: " + component + "，实际: " + body);
        assertEquals(status, node.path("status").asString(), "健康组件状态: " + component);
        return this;
    }

    /**
     * 断言组件未注册。
     *
     * @param component 组件名
     * @return 本 Checker
     */
    public HealthResponseChecker noComponent(String component) {
        assertFalse(body.path("components").has(component), "健康组件不应注册: " + component + "，实际: " + body);
        return this;
    }
}
