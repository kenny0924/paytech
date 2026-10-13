package com.paytech.risk.auto.ext.http.logging;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.foundation.web.logging.WebLoggingProperties;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.probe.HttpLogProbe;

/**
 * Risk HTTP 访问日志的敏感边界
 * <li>生产 application.yml 的 metadata-only 前缀清单完整：材料、Client 入驻审核与管理、五组决策配置入口。</li>
 * <li>mock Profile 实际生效的清单与生产一致，其余用例的日志行为即生产行为。</li>
 * <li>命中前缀的请求只记录方法、脱敏路径与状态，Query 与 Body 原值不进入日志；未命中前缀的请求照常记录 Query，证明 Probe 有效。</li>
 *
 * @see com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter
 */
class WebLoggingBoundaryAutoTest extends MockTestBase {
    /** 请求体含个人信息、审核理由或决策配置原文的入口。 */
    private static final List<String> METADATA_ONLY_PREFIXES = List.of("/internal/due-diligence/material-requirements",
        "/client-onboarding-review", "/client-onboarding-admin", "/decision-dictionaries", "/decision-rule-sets",
        "/decision-releases", "/decision-flows", "/decision-variables");

    @Autowired
    private WebLoggingProperties loggingProperties;

    /** 生产配置与测试实际生效配置都恰好是这组前缀。 */
    @Test
    void test_normalFlow_prefixesMatchProduction() {
        assertEquals(METADATA_ONLY_PREFIXES, productionPrefixes(), "risk-web application.yml");
        assertEquals(METADATA_ONLY_PREFIXES, loggingProperties.metadataOnlyPathPrefixes(), "mock Profile 生效配置");
    }

    /** 命中前缀的请求无论最终状态如何，都只留下一条元数据日志。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("metadataOnlyPrefixes")
    void test_normalFlow_metadataOnly(String prefix) {
        String requestId = "risk-logging-" + UUID.randomUUID();
        try (HttpLogProbe probe = HttpLogProbe.start()) {
            http.exchange(HttpMethod.POST, prefix + "/logging-probe?probe=query-secret", headers(requestId),
                "{\"probe\":\"payload-secret\"}");

            List<Map<String, String>> events = probe.eventsOf(requestId);
            assertEquals(1, events.size(), "访问日志条数");
            Map<String, String> event = events.getFirst();
            assertAll(() -> assertEquals("POST", event.get("method")),
                () -> assertEquals(prefix + "/**", event.get("uri")),
                () -> assertTrue(event.containsKey("status"), "status"),
                () -> assertFalse(event.containsKey("query"), "不得记录 Query"),
                () -> assertFalse(event.containsKey("requestBody"), "不得记录请求体"),
                () -> assertFalse(event.containsKey("responseBody"), "不得记录响应体"),
                () -> assertFalse(probe.rendered().contains("query-secret"), "Query 原值泄露"),
                () -> assertFalse(probe.rendered().contains("payload-secret"), "Body 原值泄露"));
        }
    }

    /** 前缀只按路径段匹配：同名前缀加后缀的路径不属于敏感入口，Query 照常记录。 */
    @Test
    void test_edgeCase_unlistedPathKeepsFullLog() {
        String requestId = "risk-logging-" + UUID.randomUUID();
        try (HttpLogProbe probe = HttpLogProbe.start()) {
            http.get("/decision-flows-unlisted/logging-probe?probe=query-visible", headers(requestId));

            List<Map<String, String>> events = probe.eventsOf(requestId);
            assertEquals(1, events.size(), "访问日志条数");
            assertAll(() -> assertEquals("/decision-flows-unlisted/logging-probe", events.getFirst().get("uri")),
                () -> assertEquals("probe=query-visible", events.getFirst().get("query")));
        }
    }

    static List<String> metadataOnlyPrefixes() {
        return METADATA_ONLY_PREFIXES;
    }

    /** 读取 risk-web 打包的生产 application.yml，不经过任何 Profile 覆盖。 */
    private static List<String> productionPrefixes() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        List<String> prefixes = new ArrayList<>();
        for (int i = 0; properties.getProperty("web.logging.metadata-only-path-prefixes[" + i + "]") != null; i++) {
            prefixes.add(properties.getProperty("web.logging.metadata-only-path-prefixes[" + i + "]"));
        }
        return prefixes;
    }

    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }
}
