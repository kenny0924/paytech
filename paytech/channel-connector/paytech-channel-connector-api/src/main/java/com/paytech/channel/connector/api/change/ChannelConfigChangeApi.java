package com.paytech.channel.connector.api.change;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.channel.connector.api.change.response.ChannelConfigChangeResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 渠道配置与成本版本的人工变更记录查询
 * <li>变更记录与业务变更同事务写入、只增不改；本接口只读，不建事务。</li>
 * <li>channel-connector 内部接口，由 Operations BFF 经私网调用。</li>
 */
@HttpExchange(url = "/channel-connector/v1/config-changes", accept = "application/json")
public interface ChannelConfigChangeApi {

    /**
     * 分页查询一个对象的变更记录
     *
     * @param targetType 被变更对象类型：CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION、STANDARD_API、CHANNEL_API、
     *        CHANNEL_SCRIPT、CHANNEL_IMPLEMENTATION、VERIFY_ACCOUNT、VERIFY_CASE、CHANNEL_CALL_GRANT
     * @param targetId 被变更对象在本库的主键，正十进制整数字符串
     * @param page 页码，默认 1，范围 1–100000
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按变更记录主键降序的分页结果；对象没有变更记录时为空页；参数格式或分页参数非法时 400 INVALID_REQUEST
     */
    @GetExchange
    PagingResponseBody<ChannelConfigChangeResponseBody> listConfigChanges(
        @RequestParam("targetType") @Pattern(regexp = "CHANNEL|CHANNEL_ACCOUNT|CHANNEL_MID|MID_CAPABILITY|CHANNEL_COST_VERSION|STANDARD_API"
            + "|CHANNEL_API|CHANNEL_SCRIPT|CHANNEL_IMPLEMENTATION|VERIFY_ACCOUNT|VERIFY_CASE|CHANNEL_CALL_GRANT")
        String targetType,
        @RequestParam("targetId") @Pattern(regexp = "[1-9][0-9]{0,18}") String targetId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
