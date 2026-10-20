package com.paytech.channel.connector.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.channel.MidCapabilityApi;
import com.paytech.channel.connector.api.channel.request.ChangeMidCapabilityStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateMidCapabilityRequestBody;
import com.paytech.channel.connector.api.channel.request.ReplaceMidCapabilityDetailsRequestBody;
import com.paytech.channel.connector.api.channel.response.CapabilityDefinitionsResponseBody;
import com.paytech.channel.connector.api.channel.response.MidCapabilityResponseBody;
import com.paytech.channel.connector.domainservice.service.MidCapabilityService;
import com.paytech.channel.connector.ext.rest.converter.MidCapabilityConverter;
import com.paytech.channel.connector.service.processor.ChangeMidCapabilityStatusProcessor;
import com.paytech.channel.connector.service.processor.CreateMidCapabilityProcessor;
import com.paytech.channel.connector.service.processor.ReplaceMidCapabilityDetailsProcessor;

import lombok.AllArgsConstructor;

/** MID 能力组管理的 HTTP 协议适配：写入口委托 Processor，能力目录直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class MidCapabilityController implements MidCapabilityApi {
    private final MidCapabilityConverter converter;
    private final MidCapabilityService midCapabilityService;
    private final CreateMidCapabilityProcessor createMidCapabilityProcessor;
    private final ChangeMidCapabilityStatusProcessor changeMidCapabilityStatusProcessor;
    private final ReplaceMidCapabilityDetailsProcessor replaceMidCapabilityDetailsProcessor;

    /** 已注册能力目录。 */
    @Override
    public CapabilityDefinitionsResponseBody listCapabilityDefinitions() {
        return converter.toDefinitionsBody(midCapabilityService.capabilityDefinitions());
    }

    /** 创建能力组并返回 201。 */
    @Override
    public ResponseEntity<MidCapabilityResponseBody> createMidCapability(String actorId,
        CreateMidCapabilityRequestBody body) {
        MidCapabilityResponseBody response =
            converter.toResponseBody(createMidCapabilityProcessor.process(converter.toRequest(actorId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 整组替换明细。 */
    @Override
    public MidCapabilityResponseBody replaceMidCapabilityDetails(String actorId, String midCapabilityId,
        ReplaceMidCapabilityDetailsRequestBody body) {
        return converter.toResponseBody(
            replaceMidCapabilityDetailsProcessor.process(converter.toRequest(actorId, midCapabilityId, body)));
    }

    /** 启停能力组。 */
    @Override
    public MidCapabilityResponseBody changeMidCapabilityStatus(String actorId, String midCapabilityId,
        ChangeMidCapabilityStatusRequestBody body) {
        return converter.toResponseBody(
            changeMidCapabilityStatusProcessor.process(converter.toRequest(actorId, midCapabilityId, body)));
    }
}
