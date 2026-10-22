package com.paytech.channel.connector.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.api.grant.request.ChangeChannelCallGrantStatusRequestBody;
import com.paytech.channel.connector.api.grant.request.CreateChannelCallGrantRequestBody;
import com.paytech.channel.connector.api.grant.response.ChannelCallGrantResponseBody;
import com.paytech.channel.connector.core.enums.ChannelCostCodes;
import com.paytech.channel.connector.protocol.domain.ChannelCallGrantView;
import com.paytech.channel.connector.protocol.service.processor.request.ChangeChannelCallGrantStatusRequest;
import com.paytech.channel.connector.protocol.service.processor.request.CreateChannelCallGrantRequest;
import com.paytech.foundation.common.error.InvalidParameterException;

/** 调用授权接口的 Wire 模型与内部请求 / 结果转换；ID 与账号标识输出为十进制字符串。 */
@Component
public class ChannelCallGrantConverter {

    /** 调用方服务名格式。 */
    private static final String CALLER_SERVICE = "[a-z][a-z0-9-]{1,63}";

    /** 标准 API 编码格式。 */
    private static final String STANDARD_API_CODE = "[A-Z][A-Z0-9_]{0,63}";

    /** 转换新建请求。 */
    public CreateChannelCallGrantRequest toRequest(String actorId, CreateChannelCallGrantRequestBody body) {
        CreateChannelCallGrantRequest request = new CreateChannelCallGrantRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setChannelId(ChannelConfigurationWire.id("channelId", body.getChannelId()));
        request.setCallerService(body.getCallerService());
        request.setStandardApiCode(body.getStandardApiCode());
        return request;
    }

    /** 转换启停请求。 */
    public ChangeChannelCallGrantStatusRequest toRequest(String actorId, String callGrantId,
        ChangeChannelCallGrantStatusRequestBody body) {
        ChangeChannelCallGrantStatusRequest request = new ChangeChannelCallGrantStatusRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setCallGrantId(ChannelConfigurationWire.id("callGrantId", callGrantId));
        request.setTargetStatus(grantStatus(body.getTargetStatus()));
        request.setExpectedVersion(body.getExpectedVersion());
        request.setReason(body.getReason());
        return request;
    }

    /** 解析可空渠道 ID 查询参数。 */
    public Long channelId(String channelId) {
        return ChannelConfigurationWire.optionalId("channelId", channelId);
    }

    /** 校验可空调用方服务名查询参数。 */
    public String callerService(String callerService) {
        return optionalMatch("callerService", callerService, CALLER_SERVICE);
    }

    /** 校验可空标准 API 编码查询参数。 */
    public String standardApiCode(String standardApiCode) {
        return optionalMatch("standardApiCode", standardApiCode, STANDARD_API_CODE);
    }

    /** 解析可空授权状态。 */
    public ChannelCallGrantStatus grantStatus(String grantStatus) {
        return grantStatus == null ? null
            : ChannelCostCodes.parse(ChannelCallGrantStatus.class, "grantStatus", grantStatus);
    }

    /** 转换授权响应。 */
    public ChannelCallGrantResponseBody toResponseBody(ChannelCallGrantView view) {
        ChannelCallGrantResponseBody body = new ChannelCallGrantResponseBody();
        body.setCallGrantId(view.getCallGrantId().toString());
        body.setChannelId(view.getChannelId().toString());
        body.setCallerService(view.getCallerService());
        body.setStandardApiCode(view.getStandardApiCode());
        body.setStandardApiName(view.getStandardApiName());
        body.setGrantStatus(view.getGrantStatus().getCode());
        body.setVersion(view.getVersion());
        body.setCreatedBy(view.getCreatedBy().toString());
        body.setUpdatedBy(view.getUpdatedBy().toString());
        body.setCreateTime(view.getCreateTime());
        body.setUpdateTime(view.getUpdateTime());
        return body;
    }

    /** 可空查询参数须匹配格式，否则 400。 */
    static String optionalMatch(String field, String value, String pattern) {
        if (value == null) {
            return null;
        }
        if (!value.matches(pattern)) {
            throw new InvalidParameterException(field + " 格式不正确");
        }
        return value;
    }
}
