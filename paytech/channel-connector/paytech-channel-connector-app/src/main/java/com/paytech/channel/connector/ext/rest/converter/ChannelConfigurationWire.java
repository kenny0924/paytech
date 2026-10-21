package com.paytech.channel.connector.ext.rest.converter;

import java.math.BigDecimal;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.service.processor.request.ChangeChannelStatusRequest;
import com.paytech.channel.connector.api.channel.response.MidCapabilityDetailResponseBody;
import com.paytech.channel.connector.api.channel.response.MidCapabilityResponseBody;
import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.channel.connector.core.enums.ChannelCostCodes;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.MidCapabilityView;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 渠道 / 账号 / MID / 能力管理接口共用的协议转换
 * <li>操作者请求头与路径 ID 必须是规范正十进制（不允许前导零与符号），否则 400 INVALID_REQUEST。</li>
 */
public final class ChannelConfigurationWire {
    private static final String DECIMAL_ID = "[1-9][0-9]{0,18}";

    /** 工具类不实例化。 */
    private ChannelConfigurationWire() {
    }

    /** 解析操作者 Account ID 请求头；渠道配置与外部 API 管理的写命令共用。 */
    public static long actor(String actorId) {
        if (actorId == null || !actorId.matches(DECIMAL_ID)) {
            throw new InvalidParameterException("操作者请求头 " + ChannelConnectorHeaders.ACTOR_HEADER + " 必须是规范正十进制");
        }
        return Long.parseLong(actorId);
    }

    /** 解析必填十进制 ID。 */
    static long id(String field, String value) {
        if (value == null || !value.matches(DECIMAL_ID)) {
            throw new InvalidParameterException(field + " 必须是规范正十进制");
        }
        return Long.parseLong(value);
    }

    /** 解析可空十进制 ID 查询参数。 */
    static Long optionalId(String field, String value) {
        return value == null ? null : id(field, value);
    }

    /** 解析可空状态查询参数或已校验的目标状态。 */
    static ChannelStatus channelStatus(String code) {
        return code == null ? null : ChannelCostCodes.parse(ChannelStatus.class, "status", code);
    }

    /** 渠道、账号或 MID 共用的状态变更请求。 */
    static ChangeChannelStatusRequest statusRequest(String actorId, String field, String targetId,
        ChangeChannelStatusRequestBody body) {
        ChangeChannelStatusRequest request = new ChangeChannelStatusRequest();
        request.setActorId(actor(actorId));
        request.setTargetId(id(field, targetId));
        request.setTargetStatus(channelStatus(body.getTargetStatus()));
        request.setReason(body.getReason());
        return request;
    }

    /** 能力组及明细响应。 */
    static MidCapabilityResponseBody capability(MidCapabilityView view) {
        var capability = view.getCapability();
        MidCapabilityResponseBody body = new MidCapabilityResponseBody();
        body.setMidCapabilityId(capability.getMidCapabilityId().toString());
        body.setMidId(capability.getMidId().toString());
        body.setPaymentMethodCode(capability.getPaymentMethodCode());
        body.setSchemeCode(capability.getSchemeCode());
        body.setCountryCode(capability.getCountryCode());
        body.setCurrency(capability.getCurrency());
        body.setMccCode(capability.getMccCode());
        body.setStatus(capability.getCapabilityStatus().getCode());
        body.setCreateTime(capability.getCreateTime());
        body.setUpdateTime(capability.getUpdateTime());
        body.setDetails(view.getDetails().stream().map(ChannelConfigurationWire::detail).toList());
        return body;
    }

    /** 能力明细响应；DECIMAL 去掉末尾零。 */
    static MidCapabilityDetailResponseBody detail(MidCapabilityDetail detail) {
        MidCapabilityDetailResponseBody body = new MidCapabilityDetailResponseBody();
        body.setCapabilityCode(detail.getCapabilityCode());
        body.setValueType(detail.getValueType().getCode());
        body.setBooleanValue(detail.getBooleanValue() == null ? null : detail.getBooleanValue() == 1);
        body.setDecimalValue(plain(detail.getDecimalValue()));
        body.setCodeValue(detail.getCodeValue());
        return body;
    }

    /** 可空 BigDecimal 转去掉末尾零的普通十进制字符串。 */
    static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
