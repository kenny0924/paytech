package com.paytech.channel.connector.test.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.api.serviceexecution.request.ExecuteServiceChannelRequestBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.builder.ServiceChannelSamples;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.mock.ChannelMockController;
import com.paytech.channel.connector.test.mocker.AccessLogProbe;
import com.paytech.channel.connector.test.mocker.ExternalApiMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;

/**
 * 服务渠道通用执行入口测试的公共调用与协议配置
 * <li>渠道出站地址指向本机 Mock 渠道（127.0.0.1，测试配置放行该地址）；协议配置由 {@link ServiceChannelSamples} 派生自已验证的下单样例。</li>
 * <li>本类不放 {@code @Test}，也不声明 BeforeEach。</li>
 */
public abstract class AbstractServiceExecutionTest extends AbstractChannelConfigApiTest {
    /** 通用执行入口路径。 */
    protected static final String SERVICE_PATH = "/channel-connector/v1/service-executions";

    /** 已授权的调用方。 */
    protected static final String CALLER = "commerce-service";

    @Autowired
    protected ExternalApiMocker externalApi;

    @Autowired
    protected AccessLogProbe accessLog;

    /** 以指定调用方请求头调用通用入口；caller 为空表示不带该请求头。 */
    protected ChannelCostResponseChecker execute(String caller, String channelCode, String standardApiCode,
        JsonNode input) {
        HttpHeaders headers = TestRequestHeaders.internal(REQUEST_ID);
        if (caller != null) {
            headers.set(ChannelConnectorHeaders.CALLER_HEADER, caller);
        }
        ExecuteServiceChannelRequestBody body = new ExecuteServiceChannelRequestBody();
        body.setChannelCode(channelCode);
        body.setStandardApiCode(standardApiCode);
        body.setInput(input);
        return new ChannelCostResponseChecker(http.exchange(HttpMethod.POST, SERVICE_PATH, headers, body), objectMapper,
            REQUEST_ID);
    }

    /** 出站地址指向本机 Mock 渠道的指定类型与状态的渠道。 */
    protected Channel channelWithEndpoint(String code, ChannelType type, ChannelStatus status) {
        return externalApi.channel(code, type, status, externalApi.endpointsJson(channelBaseUrl()));
    }

    /** 标准 API LOOKUP（无凭据）。 */
    protected StandardApi lookupStandardApi() {
        return externalApi.standardApi(ServiceChannelSamples.LOOKUP, ServiceChannelSamples.lookupRequestFields(),
            ExternalApiSamples.standardResponseFields());
    }

    /** 渠道以无凭据原子 API 与流程实现 LOOKUP。 */
    protected void implementLookup(Channel channel, StandardApi lookup) {
        externalApi.integrationApi(channel, ServiceChannelSamples.LOOKUP_CALL, ServiceChannelSamples.lookupDefinition(),
            ServiceChannelSamples.lookupApiRequestFields(), ExternalApiSamples.createOrderResponseFields());
        externalApi.implementation(channel, lookup, ServiceChannelSamples.lookupFlow());
    }

    /** Mock 渠道基础地址。 */
    protected String channelBaseUrl() {
        return mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api";
    }
}
