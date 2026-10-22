package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.channel.ChannelApi;
import com.paytech.channel.connector.api.channel.request.ChannelEndpointBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelEndpointsRequestBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.AbstractChannelConfigApiTest;
import com.paytech.channel.connector.test.builder.ChannelConfigRequestBuilder;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.mocker.ExternalApiMocker;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * 渠道出站地址整组替换：版本 CAS、被原子 API 引用的地址不能删除（409）、地址静态规则与私网 / localhost 拦截（422，字段位置到行）、
 * 部署放行网段（测试配置 127.0.0.1/32）、变更记录 CHANNEL / CONFIGURATION 只记地址摘要、关闭渠道拒绝。
 *
 * @see ChannelApi#updateChannelEndpoints(String, String, UpdateChannelEndpointsRequestBody)
 */
class ChannelEndpointsApiTest extends AbstractChannelConfigApiTest {
    private static final String PUBLIC_URL = "https://gateway.example.test/api";

    @Autowired
    private ExternalApiMocker externalApi;

    private Channel channel;

    /** 本类公共：未配置出站地址的 DRAFT 支付渠道 NI。 */
    @BeforeEach
    void setUpClassMocks() {
        channel = data.channel("NI", ChannelStatus.DRAFT);
    }

    /** 首次配置：版本 1 → 2，地址按提交顺序保存（sandbox 缺省 false），同事务写入 CONFIGURATION 变更记录与摘要。 */
    @Test
    void test_configureEndpoints() {
        var response = put(ChannelConfigRequestBuilder.endpoints(1,
            ChannelConfigRequestBuilder.endpoint("MAIN", PUBLIC_URL, null),
            ChannelConfigRequestBuilder.endpoint("SANDBOX", "https://sandbox.example.test/api", true)))
            .assertSuccess(200);

        Map<String, Object> row = testMapper.selectChannel(channel.getChannelId());
        var change = changes(ChangeTargetType.CHANNEL, channel.getChannelId()).getFirst();
        assertAll(() -> assertEquals(2, response.json().path("version").asInt()),
            () -> assertEquals(2, response.json().path("endpoints").size()),
            () -> assertEquals("MAIN", response.json().path("endpoints").get(0).path("endpointCode").asString()),
            () -> assertEquals(false, response.json().path("endpoints").get(0).path("sandbox").asBoolean()),
            () -> assertEquals(true, response.json().path("endpoints").get(1).path("sandbox").asBoolean()),
            () -> assertEquals(2, ((Number)row.get("version")).intValue()),
            () -> assertTrue(String.valueOf(row.get("endpoints")).contains(PUBLIC_URL)),
            () -> assertChange(change, ChangeType.CONFIGURATION.getCode(), null, null),
            () -> assertTrue(String.valueOf(change.get("change_detail")).contains("ENDPOINTS_DIGEST")),
            () -> assertFalse(String.valueOf(change.get("change_detail")).contains(PUBLIC_URL), "变更记录不得含地址原文"));
        get(CHANNEL_PATH + "/" + channel.getChannelId()).assertSuccess(200).assertText("version", "2");
    }

    /** 连续两次修改：后一条记录的地址前摘要等于前一条的后摘要，不受数据库 JSON 文本格式影响。 */
    @Test
    void test_endpointsDigestChained() {
        put(ChannelConfigRequestBuilder.endpoints(1,
            ChannelConfigRequestBuilder.endpoint("MAIN", PUBLIC_URL, null),
            ChannelConfigRequestBuilder.endpoint("SANDBOX", "https://sandbox.example.test/api", true))).assertSuccess(200);
        put(ChannelConfigRequestBuilder.endpoints(2,
            ChannelConfigRequestBuilder.endpoint("MAIN", "https://moved.example.test/api", null))).assertSuccess(200);

        List<JsonNode> digests = changes(ChangeTargetType.CHANNEL, channel.getChannelId()).stream()
            .map(row -> objectMapper.readTree(String.valueOf(row.get("change_detail"))))
            .sorted(Comparator.comparingInt(detail -> detail.path("beforeVersion").asInt()))
            .map(detail -> detail.path("changes").get(0)).toList();
        assertAll(() -> assertEquals(2, digests.size()),
            () -> assertEquals(digests.get(0).path("after").asString(), digests.get(1).path("before").asString()),
            () -> assertFalse(digests.get(1).path("before").asString().equals(digests.get(1).path("after").asString())));
    }

    /** 版本不一致 409，不写库与变更记录。 */
    @Test
    void test_versionConflict() {
        assertConflict(put(ChannelConfigRequestBuilder.endpoints(2,
            ChannelConfigRequestBuilder.endpoint("MAIN", PUBLIC_URL, null))));

        assertEquals(1, ((Number)testMapper.selectChannel(channel.getChannelId()).get("version")).intValue());
        assertEquals(0, testMapper.countConfigChangeRows());
    }

    /** 仍被原子 API 引用的地址代码不能删除：409，原因列出引用它的原子 API；保留该地址时可以修改其他地址。 */
    @Test
    void test_referencedEndpointKept() {
        Channel configured = externalApi.channel("NI");
        externalApi.createOrderApi(configured);

        put(ChannelConfigRequestBuilder.endpoints(2, ChannelConfigRequestBuilder.endpoint("OTHER", PUBLIC_URL, null)))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode())
            .assertDetailContains(ExternalApiSamples.CREATE_ORDER);
        put(ChannelConfigRequestBuilder.endpoints(2,
            ChannelConfigRequestBuilder.endpoint(ExternalApiSamples.ENDPOINT, "https://moved.example.test/api", null),
            ChannelConfigRequestBuilder.endpoint("OTHER", PUBLIC_URL, null))).assertSuccess(200);

        assertEquals(3, ((Number)testMapper.selectChannel(channel.getChannelId()).get("version")).intValue());
    }

    /** 回环、私网、链路本地、组播 IP 字面量与 localhost 被拦截（422，字段位置到行）；放行网段 127.0.0.1/32 内的地址允许。 */
    @Test
    void test_addressGuard() {
        for (String blocked : List.of("http://10.1.2.3/api", "https://192.168.0.10", "https://169.254.169.254/latest",
            "https://localhost:8443", "https://127.0.0.2/api", "https://[::1]/api", "https://224.0.0.1")) {
            assertValidationError(put(ChannelConfigRequestBuilder.endpoints(1,
                ChannelConfigRequestBuilder.endpoint("MAIN", PUBLIC_URL, null),
                ChannelConfigRequestBuilder.endpoint("BAD", blocked, null)))).assertFieldError("endpoints[1].baseUrl");
        }
        assertEquals(0, testMapper.countConfigChangeRows());

        put(ChannelConfigRequestBuilder.endpoints(1, ChannelConfigRequestBuilder.endpoint("MOCK",
            "http://127.0.0.1:18080/channel", null))).assertSuccess(200);
    }

    /** 静态规则：地址代码重复、协议不允许、带查询串、超过 16 项均 422。 */
    @Test
    void test_staticRules() {
        assertValidationError(put(ChannelConfigRequestBuilder.endpoints(1,
            ChannelConfigRequestBuilder.endpoint("MAIN", PUBLIC_URL, null),
            ChannelConfigRequestBuilder.endpoint("MAIN", "https://other.example.test", null))))
            .assertFieldError("endpoints[1].endpointCode");
        assertValidationError(put(ChannelConfigRequestBuilder.endpoints(1,
            ChannelConfigRequestBuilder.endpoint("MAIN", "ftp://files.example.test", null))));
        assertValidationError(put(ChannelConfigRequestBuilder.endpoints(1,
            ChannelConfigRequestBuilder.endpoint("MAIN", "https://gateway.example.test/api?key=x", null))));
        var tooMany = new ChannelEndpointBody[17];
        for (int index = 0; index < tooMany.length; index++) {
            tooMany[index] = ChannelConfigRequestBuilder.endpoint("E" + index, PUBLIC_URL, null);
        }
        assertValidationError(put(ChannelConfigRequestBuilder.endpoints(1, tooMany)));

        assertEquals(0, testMapper.countConfigChangeRows());
    }

    /** 关闭的渠道拒绝修改出站地址；缺少操作者请求头 400；渠道不存在 400。 */
    @Test
    void test_rejected() {
        Channel closed = data.channel("GEIDEA", ChannelStatus.CLOSED);

        command(HttpMethod.PUT, path(closed), ChannelConfigRequestBuilder.endpoints(1,
            ChannelConfigRequestBuilder.endpoint("MAIN", PUBLIC_URL, null))).assertBusinessRejected("渠道已关闭");
        command(HttpMethod.PUT, path(channel), null, ChannelConfigRequestBuilder.endpoints(1,
            ChannelConfigRequestBuilder.endpoint("MAIN", PUBLIC_URL, null))).assertStatus(400);
        command(HttpMethod.PUT, CHANNEL_PATH + "/1/endpoints", ChannelConfigRequestBuilder.endpoints(1,
            ChannelConfigRequestBuilder.endpoint("MAIN", PUBLIC_URL, null))).assertBusinessRejected("渠道不存在");

        assertEquals(0, testMapper.countConfigChangeRows());
    }

    /** 以标准操作者替换本类渠道的出站地址。 */
    private ChannelCostResponseChecker put(UpdateChannelEndpointsRequestBody body) {
        return command(HttpMethod.PUT, path(channel), body);
    }

    /** 出站地址路径。 */
    private static String path(Channel target) {
        return CHANNEL_PATH + "/" + target.getChannelId() + "/endpoints";
    }
}
