package com.paytech.channel.connector.ext.rest.converter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.channel.request.ChangeMidCapabilityStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateMidCapabilityRequestBody;
import com.paytech.channel.connector.api.channel.request.MidCapabilityDetailRequestBody;
import com.paytech.channel.connector.api.channel.request.ReplaceMidCapabilityDetailsRequestBody;
import com.paytech.channel.connector.api.channel.response.CapabilityDefinitionResponseBody;
import com.paytech.channel.connector.api.channel.response.CapabilityDefinitionsResponseBody;
import com.paytech.channel.connector.api.channel.response.MidCapabilityResponseBody;
import com.paytech.channel.connector.core.configuration.CapabilityDefinition;
import com.paytech.channel.connector.core.enums.CapabilityStatus;
import com.paytech.channel.connector.core.enums.ChannelCostCodes;
import com.paytech.channel.connector.domain.MidCapabilityView;
import com.paytech.channel.connector.service.processor.request.ChangeMidCapabilityStatusRequest;
import com.paytech.channel.connector.service.processor.request.CreateMidCapabilityRequest;
import com.paytech.channel.connector.service.processor.request.MidCapabilityDetailItem;
import com.paytech.channel.connector.service.processor.request.ReplaceMidCapabilityDetailsRequest;

/** MID 能力组接口的 Wire 模型与内部请求 / 结果转换；DECIMAL 字符串与 BigDecimal 精确互转。 */
@Component
public class MidCapabilityConverter {

    /** 转换创建请求。 */
    public CreateMidCapabilityRequest toRequest(String actorId, CreateMidCapabilityRequestBody body) {
        CreateMidCapabilityRequest request = new CreateMidCapabilityRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setMidId(ChannelConfigurationWire.id("midId", body.getMidId()));
        request.setPaymentMethodCode(body.getPaymentMethodCode());
        request.setSchemeCode(body.getSchemeCode());
        request.setCountryCode(body.getCountryCode());
        request.setCurrency(body.getCurrency());
        request.setMccCode(body.getMccCode());
        request.setDetails(details(body.getDetails()));
        return request;
    }

    /** 转换整组替换请求。 */
    public ReplaceMidCapabilityDetailsRequest toRequest(String actorId, String midCapabilityId,
        ReplaceMidCapabilityDetailsRequestBody body) {
        ReplaceMidCapabilityDetailsRequest request = new ReplaceMidCapabilityDetailsRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setMidCapabilityId(ChannelConfigurationWire.id("midCapabilityId", midCapabilityId));
        request.setDetails(details(body.getDetails()));
        return request;
    }

    /** 转换启停请求。 */
    public ChangeMidCapabilityStatusRequest toRequest(String actorId, String midCapabilityId,
        ChangeMidCapabilityStatusRequestBody body) {
        ChangeMidCapabilityStatusRequest request = new ChangeMidCapabilityStatusRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setMidCapabilityId(ChannelConfigurationWire.id("midCapabilityId", midCapabilityId));
        request.setTargetStatus(ChannelCostCodes.parse(CapabilityStatus.class, "targetStatus", body.getTargetStatus()));
        return request;
    }

    /** 转换明细列表。 */
    List<MidCapabilityDetailItem> details(List<MidCapabilityDetailRequestBody> bodies) {
        return bodies.stream().map(this::detail).toList();
    }

    /** 转换单项明细。 */
    MidCapabilityDetailItem detail(MidCapabilityDetailRequestBody body) {
        MidCapabilityDetailItem item = new MidCapabilityDetailItem();
        item.setCapabilityCode(body.getCapabilityCode());
        item.setBooleanValue(body.getBooleanValue());
        item.setDecimalValue(body.getDecimalValue() == null ? null : new BigDecimal(body.getDecimalValue()));
        item.setCodeValue(body.getCodeValue());
        return item;
    }

    /** 转换能力组及明细。 */
    public MidCapabilityResponseBody toResponseBody(MidCapabilityView view) {
        return ChannelConfigurationWire.capability(view);
    }

    /** 转换能力目录，按代码升序，CODE 允许值升序。 */
    public CapabilityDefinitionsResponseBody toDefinitionsBody(Map<String, CapabilityDefinition> definitions) {
        CapabilityDefinitionsResponseBody body = new CapabilityDefinitionsResponseBody();
        body.setDefinitions(definitions.entrySet().stream().map(entry -> {
            CapabilityDefinitionResponseBody definition = new CapabilityDefinitionResponseBody();
            definition.setCapabilityCode(entry.getKey());
            definition.setValueType(entry.getValue().getValueType().getCode());
            definition.setAllowedCodes(entry.getValue().getAllowedCodes() == null ? List.of()
                : List.copyOf(new TreeSet<>(entry.getValue().getAllowedCodes())));
            return definition;
        }).toList());
        return body;
    }
}
