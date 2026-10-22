package com.paytech.channel.connector.auto.serviceexecution;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.api.enums.ExecuteStatus;
import com.paytech.channel.connector.api.enums.ServiceExecutionErrorCode;
import com.paytech.channel.connector.api.serviceexecution.ServiceChannelExecutionApi;
import com.paytech.channel.connector.api.serviceexecution.request.ExecuteServiceChannelRequestBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.AbstractServiceExecutionTest;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.builder.ServiceChannelSamples;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.mocker.VaultMocker;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.node.ObjectNode;

/**
 * 服务渠道通用执行入口（AC-05、AC-06、AC-10）：授权 / 渠道类型 / 渠道状态 / 调用方身份 / 账号的场景矩阵，未发出类失败的 Problem 映射，
 * 以及每次对外调用的访问日志。所有拒绝场景断言渠道 Mock 收到的请求数为 0、Vault 未被读取。
 *
 * @see ServiceChannelExecutionApi#execute(String, ExecuteServiceChannelRequestBody)
 */
class ServiceChannelExecutionTest extends AbstractServiceExecutionTest {
    private static final String GMAP = "GMAP";
    private static final String LOOKUP_URL = "/api/lookup";

    private StandardApi lookup;
    private Channel gmap;

    /** 本类公共：标准 API LOOKUP；ACTIVE 服务渠道 GMAP 以无凭据流程实现它，并授权 commerce-service 调用。 */
    @BeforeEach
    void setUpClassMocks() {
        lookup = lookupStandardApi();
        gmap = channelWithEndpoint(GMAP, ChannelType.SERVICE, ChannelStatus.ACTIVE);
        implementLookup(gmap, lookup);
        externalApi.grant(CALLER, gmap, lookup, ChannelCallGrantStatus.ACTIVE);
    }

    /** 已授权调用方：200 + SUCCESS 与标准 API 输出；渠道只收到一次请求；访问日志字段齐全且不含 URL、报文与输入值。 */
    @Test
    void test_grantedCallerSucceeds() {
        channel.respond("POST", LOOKUP_URL, 201, ServiceChannelSamples.captured("R-1"));

        var response = execute(CALLER, GMAP, ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("7001"))
            .assertSuccess(200);

        var request = channel.only();
        var lines = accessLog.lines();
        assertAll(() -> response.assertText("executeStatus", ExecuteStatus.SUCCESS.getCode()),
            () -> assertEquals("PAID", response.json().path("outputs").path("paymentStatus").asString()),
            () -> assertEquals("R-1", response.json().path("outputs").path("transactionRef").asString()),
            () -> assertTrue(request.body().contains("\"orderRef\":\"7001\""), request.body()),
            () -> assertEquals(1, lines.size(), "访问日志条数"),
            () -> assertTrue(lines.getFirst().contains("callerService=" + CALLER), lines.getFirst()),
            () -> assertTrue(lines.getFirst().contains("channelCode=" + GMAP), lines.getFirst()),
            () -> assertTrue(lines.getFirst().contains("channelType=SERVICE"), lines.getFirst()),
            () -> assertTrue(lines.getFirst().contains("standardApiCode=" + ServiceChannelSamples.LOOKUP), lines.getFirst()),
            () -> assertTrue(lines.getFirst().contains("apiCode=" + ServiceChannelSamples.LOOKUP_CALL), lines.getFirst()),
            () -> assertTrue(lines.getFirst().contains("executeStatus=SUCCESS"), lines.getFirst()),
            () -> assertTrue(lines.getFirst().contains("httpStatus=201"), lines.getFirst()),
            () -> assertTrue(lines.getFirst().contains("elapsedMs="), lines.getFirst()),
            () -> assertTrue(lines.getFirst().contains("requestId=" + REQUEST_ID), lines.getFirst()),
            () -> assertFalse(lines.getFirst().contains("7001"), "访问日志不得含输入值"),
            () -> assertFalse(lines.getFirst().contains("R-1"), "访问日志不得含响应报文"),
            () -> assertFalse(lines.getFirst().contains("/lookup"), "访问日志不得含 URL"));
        vault.assertNoCalls();
    }

    /** 渠道返回无法识别的状态：200 + ERROR、输出为空对象；访问日志记 ERROR 与渠道 HTTP 状态。 */
    @Test
    void test_unknownResultIsError() {
        channel.respond("POST", LOOKUP_URL, 500, "{\"message\":\"down\"}");

        execute(CALLER, GMAP, ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("7002")).assertSuccess(200)
            .assertText("executeStatus", ExecuteStatus.ERROR.getCode());

        assertAll(() -> assertEquals(1, channel.requests().size()),
            () -> assertTrue(accessLog.lines().getFirst().contains("executeStatus=ERROR"), accessLog.lines().getFirst()),
            () -> assertTrue(accessLog.lines().getFirst().contains("httpStatus=500"), accessLog.lines().getFirst()));
    }

    /** 调用方身份缺失或格式不符：400，不发出请求。 */
    @Test
    void test_callerIdentityRequired() {
        execute(null, GMAP, ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("1")).assertInvalidRequest();
        execute("Commerce_Service", GMAP, ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("1"))
            .assertInvalidRequest();

        assertNotSent();
    }

    /** 未授权：其他调用方、已授权调用方调用未授权的标准 API、授权已停用，均 403 CHANNEL_CALL_NOT_GRANTED 且不发出请求。 */
    @Test
    void test_notGranted() {
        StandardApi other = externalApi.standardApi("LOOKUP_OTHER", ServiceChannelSamples.lookupRequestFields(),
            ExternalApiSamples.standardResponseFields());
        externalApi.implementation(gmap, other, ServiceChannelSamples.lookupFlow());
        Channel disabled = channelWithEndpoint("SMS", ChannelType.SERVICE, ChannelStatus.ACTIVE);
        implementLookup(disabled, lookup);
        externalApi.grant(CALLER, disabled, lookup, ChannelCallGrantStatus.DISABLED);

        assertNotGranted(execute("risk-service", GMAP, ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("1")));
        assertNotGranted(execute(CALLER, GMAP, "LOOKUP_OTHER", ServiceChannelSamples.input("1")));
        assertNotGranted(execute(CALLER, "SMS", ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("1")));
        assertNotGranted(execute(CALLER, GMAP, "NOT_EXISTING", ServiceChannelSamples.input("1")));

        assertNotSent();
    }

    /** 渠道类型与状态：支付渠道 400、DRAFT / SUSPENDED 服务渠道 400、渠道不存在 404，均不发出请求（AC-06）。 */
    @Test
    void test_channelTypeAndStatus() {
        Channel payment = channelWithEndpoint("NI", ChannelType.PAYMENT, ChannelStatus.ACTIVE);
        implementLookup(payment, lookup);
        externalApi.grant(CALLER, payment, lookup, ChannelCallGrantStatus.ACTIVE);
        for (ChannelStatus status : List.of(ChannelStatus.DRAFT, ChannelStatus.SUSPENDED)) {
            Channel inactive = channelWithEndpoint("SVC_" + status.getCode(), ChannelType.SERVICE, status);
            implementLookup(inactive, lookup);
            externalApi.grant(CALLER, inactive, lookup, ChannelCallGrantStatus.ACTIVE);
            execute(CALLER, inactive.getChannelCode(), ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("1"))
                .assertBusinessRejected("渠道未激活");
        }

        execute(CALLER, "NI", ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("1"))
            .assertBusinessRejected("支付渠道只能经支付入口执行");
        execute(CALLER, "UNKNOWN", ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("1"))
            .assertProblem(404, CommonErrorCode.NOT_FOUND.getCode());

        assertNotSent();
    }

    /** 未发出类失败的 Problem：输入不满足请求字段 422 带字段位置；已保存配置未通过运行前校验 500；均不发出请求。 */
    @Test
    void test_notSentProblems() {
        ObjectNode missingAmount = ServiceChannelSamples.input("1");
        ((ObjectNode)missingAmount.get("paymentContext")).remove("amount");
        Channel broken = channelWithEndpoint("BROKEN", ChannelType.SERVICE, ChannelStatus.ACTIVE);
        externalApi.implementation(broken, lookup, ExternalApiSamples.object("{\"timeoutMs\":0}"));
        externalApi.grant(CALLER, broken, lookup, ChannelCallGrantStatus.ACTIVE);

        execute(CALLER, GMAP, ServiceChannelSamples.LOOKUP, missingAmount)
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR.getCode()).assertFieldError("input.paymentContext.amount");
        execute(CALLER, "BROKEN", ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("1"))
            .assertProblem(500, ServiceExecutionErrorCode.INTEGRATION_CONFIG_INVALID.getCode());

        assertNotSent();
    }

    /** 出站地址是未放行的私网 IP 字面量（绕过管理接口写入）：按配置无效 500，不发出请求。 */
    @Test
    void test_privateAddressBlocked() {
        Channel internal = externalApi.channel("INTERNAL", ChannelType.SERVICE, ChannelStatus.ACTIVE,
            externalApi.endpointsJson("http://10.255.0.1:8080/api"));
        implementLookup(internal, lookup);
        externalApi.grant(CALLER, internal, lookup, ChannelCallGrantStatus.ACTIVE);

        execute(CALLER, "INTERNAL", ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("1"))
            .assertProblem(500, ServiceExecutionErrorCode.INTEGRATION_CONFIG_INVALID.getCode());

        assertNotSent();
    }

    /**
     * 账号材料：唯一 ACTIVE 凭据由本服务注入并以本服务身份 Reveal（负载 entityNo 为 null），调用方自带的 channelAccount 被忽略；
     * 没有 ACTIVE 凭据时不提供账号材料，要求凭据的标准 API 以 422 拒绝且不读 Vault、不发出请求。
     */
    @Test
    void test_accountMaterial() {
        StandardApi secure = externalApi.standardApi(ServiceChannelSamples.LOOKUP_SECURE,
            ServiceChannelSamples.secureRequestFields(), ExternalApiSamples.standardResponseFields());
        Channel withCredential = channelWithEndpoint("KYC", ChannelType.SERVICE, ChannelStatus.ACTIVE);
        implementSecure(withCredential, secure);
        externalApi.grant(CALLER, withCredential, secure, ChannelCallGrantStatus.ACTIVE);
        String token = VaultMocker.token("kyc-credential");
        var account = data.account(withCredential.getChannelId(), null, token, ChannelStatus.ACTIVE);
        vault.respond(token, """
            {"schemaVersion":1,"channelCode":"KYC","channelAccountId":"%s","entityNo":null,
             "parameters":{"outletId":"kyc-outlet"},"credentials":{"apiKey":"synthetic-kyc-key"}}"""
            .formatted(account.getChannelAccountId()));
        Channel withoutCredential = channelWithEndpoint("KYC_NONE", ChannelType.SERVICE, ChannelStatus.ACTIVE);
        implementSecure(withoutCredential, secure);
        externalApi.grant(CALLER, withoutCredential, secure, ChannelCallGrantStatus.ACTIVE);
        channel.respond("POST", "/api/outlets/kyc-outlet/orders", 201, ServiceChannelSamples.captured("K-1"));
        ObjectNode forged = ServiceChannelSamples.input("8001");
        forged.putObject("channelAccount").put("channelCode", "KYC").put("channelAccountId", "1")
            .put("accountVaultToken", VaultMocker.token("forged"));

        execute(CALLER, "KYC", ServiceChannelSamples.LOOKUP_SECURE, forged).assertSuccess(200)
            .assertText("executeStatus", ExecuteStatus.SUCCESS.getCode());
        vault.assertOnly(token);
        execute(CALLER, "KYC_NONE", ServiceChannelSamples.LOOKUP_SECURE, ServiceChannelSamples.input("8002"))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR.getCode()).assertFieldError("input.channelAccount");

        assertAll(() -> assertEquals(1, channel.requests().size()), () -> assertEquals(1, vault.revealCount()));
    }

    /** 渠道以需要凭据的样例下单 API 与流程实现 LOOKUP_SECURE。 */
    private void implementSecure(Channel target, StandardApi secure) {
        externalApi.integrationApi(target, ServiceChannelSamples.SECURE_CALL, ExternalApiSamples.createOrderDefinition());
        externalApi.implementation(target, secure, ServiceChannelSamples.secureFlow());
    }

    /** 403 CHANNEL_CALL_NOT_GRANTED。 */
    private static void assertNotGranted(ChannelCostResponseChecker checker) {
        checker.assertProblem(403, ServiceExecutionErrorCode.CHANNEL_CALL_NOT_GRANTED.getCode());
    }

    /** 没有发出任何外部请求，也没有读取 Vault 与写访问日志。 */
    private void assertNotSent() {
        assertAll(() -> assertTrue(channel.requests().isEmpty(), "渠道不应收到请求"),
            () -> assertEquals(0, vault.revealCount(), "不应读取 Vault"),
            () -> assertEquals(List.of(), accessLog.lines(), "不应有访问日志"));
    }
}
