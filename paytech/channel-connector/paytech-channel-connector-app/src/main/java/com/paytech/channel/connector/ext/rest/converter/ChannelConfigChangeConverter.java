package com.paytech.channel.connector.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.change.response.ChannelConfigChangeResponseBody;
import com.paytech.channel.connector.core.enums.ChannelCostCodes;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.domain.ChannelConfigChangeRecord;

/** 变更记录接口的查询参数解析与 Wire 转换；ID 输出为十进制字符串。 */
@Component
public class ChannelConfigChangeConverter {

    /** 解析被变更对象类型；格式已由入口校验，未知代码以参数错误拒绝。 */
    public ChangeTargetType targetType(String targetType) {
        return ChannelCostCodes.parse(ChangeTargetType.class, "targetType", targetType);
    }

    /** 转换一条变更记录。 */
    public ChannelConfigChangeResponseBody toResponseBody(ChannelConfigChangeRecord change) {
        ChannelConfigChangeResponseBody body = new ChannelConfigChangeResponseBody();
        body.setChangeId(change.getId().toString());
        body.setTargetType(change.getTargetType().getCode());
        body.setTargetId(change.getTargetId().toString());
        body.setChangeType(change.getChangeType().getCode());
        body.setFromStatus(change.getFromStatus());
        body.setToStatus(change.getToStatus());
        body.setOperatorId(change.getOperatorId().toString());
        body.setReason(change.getReason());
        body.setCreateTime(change.getCreateTime());
        return body;
    }
}
