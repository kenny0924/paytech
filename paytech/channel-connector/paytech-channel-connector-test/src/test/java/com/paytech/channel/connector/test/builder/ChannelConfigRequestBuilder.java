package com.paytech.channel.connector.test.builder;

import com.paytech.channel.connector.api.channel.request.ChannelEndpointBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelEndpointsRequestBody;
import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.api.grant.request.ChangeChannelCallGrantStatusRequestBody;
import com.paytech.channel.connector.api.grant.request.CreateChannelCallGrantRequestBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.List;
import java.util.Map;

import com.paytech.channel.connector.api.channel.request.ConfigureChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelMidRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateMidCapabilityRequestBody;
import com.paytech.channel.connector.api.channel.request.MidCapabilityDetailRequestBody;
import com.paytech.channel.connector.api.channel.request.RebindChannelMidAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.ReplaceMidCapabilityDetailsRequestBody;

/** 渠道基础配置写入口的合法默认请求体；用例只覆盖与场景相关的字段，不修复刻意设置的非法值。 */
public final class ChannelConfigRequestBuilder {
    /** 合成账号参数。 */
    public static final Map<String, String> PARAMETERS = Map.of("outletId", "synthetic-outlet");

    /** 合成凭据。 */
    public static final Map<String, String> CREDENTIALS = Map.of("apiKey", "synthetic-api-key-value");

    /** 工具类不实例化。 */
    private ChannelConfigRequestBuilder() {
    }

    /** 创建支付渠道。 */
    public static CreateChannelRequestBody channel(String channelCode) {
        return channel(channelCode, ChannelType.PAYMENT);
    }

    /** 创建指定类型的渠道；类型可为 null 以构造缺字段负例。 */
    public static CreateChannelRequestBody channel(String channelCode, ChannelType type) {
        CreateChannelRequestBody body = new CreateChannelRequestBody();
        body.setChannelCode(channelCode);
        body.setChannelName("合成渠道 " + channelCode);
        body.setChannelType(type == null ? null : type.getCode());
        return body;
    }

    /** 创建服务渠道的凭据（不绑定 OE）。 */
    public static CreateChannelAccountRequestBody credential(long channelId) {
        return account(channelId, null);
    }

    /** 整组替换出站地址。 */
    public static UpdateChannelEndpointsRequestBody endpoints(int expectedVersion, ChannelEndpointBody... endpoints) {
        UpdateChannelEndpointsRequestBody body = new UpdateChannelEndpointsRequestBody();
        body.setExpectedVersion(expectedVersion);
        body.setEndpoints(List.of(endpoints));
        return body;
    }

    /** 一个出站地址；sandbox 为 null 表示不填写。 */
    public static ChannelEndpointBody endpoint(String endpointCode, String baseUrl, Boolean sandbox) {
        ChannelEndpointBody body = new ChannelEndpointBody();
        body.setEndpointCode(endpointCode);
        body.setBaseUrl(baseUrl);
        body.setSandbox(sandbox);
        return body;
    }

    /** 新建调用授权。 */
    public static CreateChannelCallGrantRequestBody grant(long channelId, String callerService,
        String standardApiCode) {
        CreateChannelCallGrantRequestBody body = new CreateChannelCallGrantRequestBody();
        body.setChannelId(String.valueOf(channelId));
        body.setCallerService(callerService);
        body.setStandardApiCode(standardApiCode);
        return body;
    }

    /** 启停调用授权。 */
    public static ChangeChannelCallGrantStatusRequestBody grantStatus(ChannelCallGrantStatus target,
        int expectedVersion, String reason) {
        ChangeChannelCallGrantStatusRequestBody body = new ChangeChannelCallGrantStatusRequestBody();
        body.setTargetStatus(target.getCode());
        body.setExpectedVersion(expectedVersion);
        body.setReason(reason);
        return body;
    }

    /** 创建技术账号。 */
    public static CreateChannelAccountRequestBody account(long channelId, String entityNo) {
        CreateChannelAccountRequestBody body = new CreateChannelAccountRequestBody();
        body.setChannelId(String.valueOf(channelId));
        body.setEntityNo(entityNo);
        body.setAccountName("合成技术账号");
        body.setRemark("合成备注");
        return body;
    }

    /** 写入账号参数与凭据。 */
    public static ConfigureChannelAccountRequestBody configuration(Map<String, String> parameters,
        Map<String, String> credentials) {
        ConfigureChannelAccountRequestBody body = new ConfigureChannelAccountRequestBody();
        body.setParameters(parameters == null ? null : new LinkedHashMap<>(parameters));
        body.setCredentials(credentials == null ? null : new LinkedHashMap<>(credentials));
        return body;
    }

    /** 创建 MID。 */
    public static CreateChannelMidRequestBody mid(long channelId, long accountId, String externalMidCode,
        String entityNo) {
        CreateChannelMidRequestBody body = new CreateChannelMidRequestBody();
        body.setChannelId(String.valueOf(channelId));
        body.setChannelAccountId(String.valueOf(accountId));
        body.setExternalMidCode(externalMidCode);
        body.setEntityNo(entityNo);
        return body;
    }

    /** MID 改绑。 */
    public static RebindChannelMidAccountRequestBody rebind(long accountId) {
        RebindChannelMidAccountRequestBody body = new RebindChannelMidAccountRequestBody();
        body.setChannelAccountId(String.valueOf(accountId));
        return body;
    }

    /** 创建 CARD / VISA / AE / AED / 5734 能力组。 */
    public static CreateMidCapabilityRequestBody capability(long midId, MidCapabilityDetailRequestBody... details) {
        CreateMidCapabilityRequestBody body = new CreateMidCapabilityRequestBody();
        body.setMidId(String.valueOf(midId));
        body.setPaymentMethodCode("CARD");
        body.setSchemeCode("VISA");
        body.setCountryCode("AE");
        body.setCurrency("AED");
        body.setMccCode("5734");
        body.setDetails(List.of(details));
        return body;
    }

    /** 整组替换明细。 */
    public static ReplaceMidCapabilityDetailsRequestBody replace(MidCapabilityDetailRequestBody... details) {
        ReplaceMidCapabilityDetailsRequestBody body = new ReplaceMidCapabilityDetailsRequestBody();
        body.setDetails(List.of(details));
        return body;
    }

    /** BOOLEAN 明细。 */
    public static MidCapabilityDetailRequestBody bool(String code, boolean value) {
        MidCapabilityDetailRequestBody body = new MidCapabilityDetailRequestBody();
        body.setCapabilityCode(code);
        body.setBooleanValue(value);
        return body;
    }

    /** DECIMAL 明细。 */
    public static MidCapabilityDetailRequestBody decimal(String code, String value) {
        MidCapabilityDetailRequestBody body = new MidCapabilityDetailRequestBody();
        body.setCapabilityCode(code);
        body.setDecimalValue(value);
        return body;
    }

    /** CODE 明细。 */
    public static MidCapabilityDetailRequestBody code(String code, String value) {
        MidCapabilityDetailRequestBody body = new MidCapabilityDetailRequestBody();
        body.setCapabilityCode(code);
        body.setCodeValue(value);
        return body;
    }
}
