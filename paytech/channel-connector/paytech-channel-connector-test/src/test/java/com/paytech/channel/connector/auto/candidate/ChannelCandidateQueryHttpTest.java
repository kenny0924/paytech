package com.paytech.channel.connector.auto.candidate;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.channel.connector.api.ChannelCapabilityCodes;
import com.paytech.channel.connector.api.candidate.ChannelCandidateQueryApi;
import com.paytech.channel.connector.api.candidate.request.QueryMidCandidatesRequestBody;
import com.paytech.channel.connector.api.candidate.response.QueryMidCandidatesResponseBody;
import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.ChannelConnectorMockTestBase;
import com.paytech.channel.connector.test.builder.ChannelRequestBuilder;
import com.paytech.channel.connector.test.builder.MidCapabilityBuilder;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;

/**
 * MID 候选查询的 HTTP 合同：能力代码只在 channel-connector 解析（D06），返回支持的渠道操作与金额上下限，ID 与金额为十进制字符串。
 *
 * @see ChannelCandidateQueryApi#queryMidCandidates(QueryMidCandidatesRequestBody)
 */
class ChannelCandidateQueryHttpTest extends ChannelConnectorMockTestBase {
    @LocalServerPort
    private int port;

    @Autowired
    private ChannelTestMapper mapper;

    private Fixture fixture;

    /** 本类公共：OE 00001 的启用三元组与 CARD/AED PAY 能力。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.activeFixture();
    }

    /** ACTION.* 为 true 的操作按代码升序，AMOUNT.MIN/MAX 原样精度返回；不读取 Vault。 */
    @Test
    void test_candidateProjection() {
        data.capabilityDetail(fixture, "ACTION.AUTHORIZE", true);
        data.amountBounds(fixture, new BigDecimal("1.50"), new BigDecimal("2000"));

        var candidates = query(ChannelRequestBuilder.candidatesBody()).getCandidates();

        var candidate = candidates.getFirst();
        assertAll(() -> assertEquals(1, candidates.size()),
            () -> assertEquals(fixture.getChannel().getChannelId().toString(), candidate.getChannelId()),
            () -> assertEquals(fixture.getAccount().getChannelAccountId().toString(), candidate.getChannelAccountId()),
            () -> assertEquals(fixture.getMid().getMidId().toString(), candidate.getMidId()),
            () -> assertEquals(List.of(ChannelOperation.AUTHORIZE.getCode(), ChannelOperation.PAY.getCode()),
                candidate.getSupportedOperations()),
            () -> assertEquals(0, new BigDecimal("1.50").compareTo(new BigDecimal(candidate.getMinAmount()))),
            () -> assertEquals(0, new BigDecimal("2000").compareTo(new BigDecimal(candidate.getMaxAmount()))));
        vault.assertNoCalls();
    }

    /** 明确关闭的动作不进入支持操作，候选仍返回（由 Payment 路由淘汰）；未声明的限额为空。 */
    @Test
    void test_disabledActionNotSupported() {
        data.capability(fixture, "VISA", "AE", "AED", "ACTION.PAY", false);

        var candidate = query(ChannelRequestBuilder.candidatesBody()).getCandidates().getFirst();

        assertAll(() -> assertEquals(List.of(), candidate.getSupportedOperations()),
            () -> assertNull(candidate.getMinAmount()), () -> assertNull(candidate.getMaxAmount()));
    }

    /**
     * 抽取共用可用 MID 读取（REQ-02 T1）后的对照：同 OE 两个账号下的多个 MID，候选按 MID 读取顺序逐字段与重构前口径一致
     * <li>关闭的 MID、关闭的渠道账号下的 MID 不成为候选；每个候选只投影本 MID 命中的能力。</li>
     */
    @Test
    void test_multipleAccountsProjection() {
        var secondAccount = data.account(fixture.getChannel().getChannelId(), "00001", "C".repeat(43));
        var second = data.mid(fixture.getChannel().getChannelId(), secondAccount.getChannelAccountId(), "00001");
        data.capability(second, MidCapabilityBuilder.template().scheme("VISA"),
            ChannelCapabilityCodes.ACTION_PREFIX + ChannelOperation.AUTHORIZE.getCode(), true);
        var closed = data.mid(fixture.getChannel().getChannelId(), secondAccount.getChannelAccountId(), "00001",
            ChannelStatus.CLOSED);
        data.capability(closed, MidCapabilityBuilder.template(), ChannelCapabilityCodes.ACTION_PREFIX + ChannelOperation.PAY.getCode(),
            true);
        var suspendedAccount = data.account(fixture.getChannel().getChannelId(), "00001", "D".repeat(43), ChannelStatus.SUSPENDED);
        var orphan = data.mid(fixture.getChannel().getChannelId(), suspendedAccount.getChannelAccountId(), "00001");
        data.capability(orphan, MidCapabilityBuilder.template(), ChannelCapabilityCodes.ACTION_PREFIX + ChannelOperation.PAY.getCode(),
            true);

        var candidates = query(ChannelRequestBuilder.candidatesBody()).getCandidates();

        assertAll(() -> assertEquals(List.of(fixture.getMid().getMidId().toString(), second.getMidId().toString()),
                candidates.stream().map(candidate -> candidate.getMidId()).toList()),
            () -> assertEquals(List.of(fixture.getAccount().getChannelAccountId().toString(),
                secondAccount.getChannelAccountId().toString()), candidates.stream().map(c -> c.getChannelAccountId()).toList()),
            () -> assertEquals(List.of(fixture.getChannel().getChannelCode(), fixture.getChannel().getChannelCode()),
                candidates.stream().map(c -> c.getChannelCode()).toList()),
            () -> assertEquals(List.of(ChannelOperation.PAY.getCode()), candidates.get(0).getSupportedOperations()),
            () -> assertEquals(List.of(ChannelOperation.AUTHORIZE.getCode()), candidates.get(1).getSupportedOperations()),
            () -> assertNull(candidates.get(1).getMinAmount()), () -> assertNull(candidates.get(1).getThreeDsMode()));
        vault.assertNoCalls();
    }

    /** 配置损坏返回 500，不伪装成空候选；请求维度非法返回 422。 */
    @Test
    void test_brokenConfigurationRejected() {
        var invalid = ChannelRequestBuilder.candidatesBody();
        invalid.getCriteria().setCountryCode("*");
        mapper.midEntity("00002");

        assertAll(() -> assertEquals(500, status(ChannelRequestBuilder.candidatesBody())),
            () -> assertEquals(422, status(invalid)));
    }

    /** 经真实 HTTP 入口查询；4xx/5xx 使测试失败。 */
    QueryMidCandidatesResponseBody query(QueryMidCandidatesRequestBody body) {
        return client().post().uri("/channel-connector/v1/mid-candidates").contentType(MediaType.APPLICATION_JSON)
            .body(body).retrieve().body(QueryMidCandidatesResponseBody.class);
    }

    /** 查询并返回 HTTP 状态。 */
    int status(QueryMidCandidatesRequestBody body) {
        try {
            return client().post().uri("/channel-connector/v1/mid-candidates").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }

    /** JDK 客户端：不自动重试 POST。 */
    RestClient client() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port)
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient())).build();
    }
}
