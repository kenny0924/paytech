package com.paytech.channel.connector.test.mocker;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.channel.connector.api.enums.ApiFieldDataType;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.api.externalapi.request.ApiFieldBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.*;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.EndpointDefinition;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.protocol.domainservice.repository.*;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.foundation.id.IdGenerator;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * 外部 API 配置的本地前置数据：经生产 Repository 直接插入已保存形态的行，版本为 1（2026-10-20 随渠道协议自 Platform 迁入）
 * <li>只做 Arrange，不调用被测管理接口；协议表由 {@link ChannelDataMocker} 整表回收，本组件不持有可变状态。</li>
 * <li>主键使用 channel-connector 的正式 Snowflake 接口，时间取进程时间源并截断到微秒。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class ExternalApiMocker {

    private static final long CREATOR = 900_000_000_000_000_001L;

    private final IdGenerator ids;
    private final Clock clock;
    private final ExternalApiConfigurationCodec codec;
    private final ApiFieldRepository fields;
    private final StandardApiRepository standardApis;
    private final ChannelRepository channels;
    private final ChannelApiRepository channelApis;
    private final ChannelScriptRepository scripts;
    private final ChannelImplementationRepository implementations;
    private final ChannelCallGrantRepository grants;

    /** 使用样例字段的标准 API。 */
    public StandardApi standardApi(String code) {
        return standardApi(code, ExternalApiSamples.standardRequestFields(), ExternalApiSamples.standardResponseFields());
    }

    /** 指定字段的标准 API。 */
    public StandardApi standardApi(String code, List<ApiFieldBody> requestFields, List<ApiFieldBody> responseFields) {
        Instant now = now();
        var row = new StandardApi(ids.nextId(), code, code + " 名称", null, 1, CREATOR, CREATOR, now, now);
        standardApis.insert(row);
        insertFields(row.getStandardApiId(), ApiFieldDirection.STANDARD_API_REQUEST, requestFields, now);
        insertFields(row.getStandardApiId(), ApiFieldDirection.STANDARD_API_RESPONSE, responseFields, now);
        return row;
    }

    /** 使用样例出站地址的渠道。 */
    public Channel channel(String code) {
        return channel(code, ExternalApiSamples.endpoints().getFirst().getBaseUrl());
    }

    /** 出站地址 DEMO_GATEWAY 指向指定基础地址的渠道，执行测试用它指向本机 Mock 渠道。 */
    public Channel channel(String code, String baseUrl) {
        return channel(code, baseUrl, false);
    }

    /**
     * 出站地址 DEMO_GATEWAY 指向指定基础地址并标记是否沙箱的渠道，沙箱验证测试使用
     * <li>同编码渠道已由 {@link ChannelDataMocker} 插入时（支付执行测试），经生产 Repository 写入出站地址并返回该渠道；
     * 否则插入一个 DRAFT 支付渠道（合表后渠道与原接入方是同一行）。</li>
     */
    public Channel channel(String code, String baseUrl, boolean sandbox) {
        Instant now = now();
        var endpoints = codec.endpointsJson(List.of(new EndpointDefinition(ExternalApiSamples.ENDPOINT, baseUrl, sandbox)));
        Channel existing = channels.byCode(code);
        if (existing != null) {
            channels.updateEndpoints(existing, endpoints, CREATOR, now);
            return existing;
        }
        return channel(code, ChannelType.PAYMENT, ChannelStatus.DRAFT, endpoints);
    }

    /** 指定类型、状态与出站地址 JSON（可空）的新渠道，版本为 1。 */
    public Channel channel(String code, ChannelType type, ChannelStatus status, String endpointsJson) {
        Instant now = now();
        var row = new Channel();
        row.setChannelId(ids.nextId());
        row.setChannelCode(code);
        row.setChannelName(code + " 渠道");
        row.setChannelType(type);
        row.setChannelStatus(status);
        row.setEndpoints(endpointsJson);
        row.setVersion(1);
        row.setCreatedBy(CREATOR);
        row.setUpdatedBy(CREATOR);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        channels.insert(row);
        return row;
    }

    /** 指定基础地址的单个出站地址 JSON。 */
    public String endpointsJson(String baseUrl) {
        return codec.endpointsJson(List.of(new EndpointDefinition(ExternalApiSamples.ENDPOINT, baseUrl, false)));
    }

    /** 使用样例字段与定义的下单原子 API。 */
    public ChannelApi createOrderApi(Channel integration) {
        return integrationApi(integration, ExternalApiSamples.CREATE_ORDER, ExternalApiSamples.createOrderDefinition());
    }

    /** 使用样例字段、指定定义的原子 API。 */
    public ChannelApi integrationApi(Channel integration, String code, JsonNode definition) {
        return integrationApi(integration, code, definition, ExternalApiSamples.createOrderRequestFields(),
            ExternalApiSamples.createOrderResponseFields());
    }

    /** 指定字段与定义的原子 API。 */
    public ChannelApi integrationApi(Channel integration, String code, JsonNode definition,
        List<ApiFieldBody> requestFields, List<ApiFieldBody> responseFields) {
        Instant now = now();
        var row = new ChannelApi(ids.nextId(), integration.getChannelId(), code, code + " 名称",
            null, ExternalApiSamples.text(definition), 1, CREATOR, CREATOR, now, now);
        channelApis.insert(row);
        insertFields(row.getChannelApiId(), ApiFieldDirection.INTEGRATION_API_REQUEST, requestFields, now);
        insertFields(row.getChannelApiId(), ApiFieldDirection.INTEGRATION_API_RESPONSE, responseFields, now);
        return row;
    }

    /** 使用样例参数与源码的签名脚本。 */
    public ChannelScript signScript(Channel integration, ScriptExecutionStage stage) {
        var parameters = ExternalApiSamples.signParameters().stream()
            .map(parameter -> new ParameterDeclaration(parameter.getName(), parameter.getDataType(),
                parameter.getRequired())).toList();
        return script(integration, ExternalApiSamples.SIGN_SCRIPT, stage, parameters, ExternalApiSamples.signSource());
    }

    /** 指定编码、阶段、参数与源码的公共脚本。 */
    public ChannelScript script(Channel integration, String code, ScriptExecutionStage stage,
        List<ParameterDeclaration> parameters, String sourceCode) {
        Instant now = now();
        var row = new ChannelScript(ids.nextId(), integration.getChannelId(), code, "请求签名", stage,
            codec.parametersJson(parameters), sourceCode, null, 1, CREATOR, CREATOR, now, now);
        scripts.insert(row);
        return row;
    }

    /** 指定流程的标准 API 实现。 */
    public ChannelImplementation implementation(Channel integration, StandardApi standardApi, JsonNode flow) {
        Instant now = now();
        var row = new ChannelImplementation(ids.nextId(), integration.getChannelId(),
            standardApi.getStandardApiId(), ExternalApiSamples.text(flow), 1, CREATOR, CREATOR, now, now);
        implementations.insert(row);
        return row;
    }

    /** 指定调用方、渠道、标准 API 与状态的调用授权，版本为 1。 */
    public ChannelCallGrant grant(String callerService, Channel channel, StandardApi standardApi,
        ChannelCallGrantStatus status) {
        Instant now = now();
        var row = new ChannelCallGrant();
        row.setCallGrantId(ids.nextId());
        row.setCallerService(callerService);
        row.setChannelId(channel.getChannelId());
        row.setStandardApiId(standardApi.getStandardApiId());
        row.setGrantStatus(status);
        row.setVersion(1);
        row.setCreatedBy(CREATOR);
        row.setUpdatedBy(CREATOR);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        grants.insert(row);
        return row;
    }

    /** 本次插入使用的 UTC 微秒时间。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 按数组顺序写入字段，顺序号从 1 开始，与接口保存一致。 */
    private void insertFields(long apiId, ApiFieldDirection direction, List<ApiFieldBody> bodies, Instant now) {
        var rows = new ArrayList<ApiField>();
        for (int index = 0; index < bodies.size(); index++) {
            ApiFieldBody body = bodies.get(index);
            rows.add(new ApiField(ids.nextId(), apiId, direction,
                ApiFieldLocation.fromCode(body.getFieldLocation()), body.getFieldPath(), body.getFieldName(),
                ApiFieldDataType.fromCode(body.getDataType()), body.getRequired(), index + 1, body.getDescription(),
                now, now));
        }
        fields.insertAll(rows);
    }
}
