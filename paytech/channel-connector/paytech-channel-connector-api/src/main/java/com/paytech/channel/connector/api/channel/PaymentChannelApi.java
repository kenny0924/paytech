package com.paytech.channel.connector.api.channel;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelSummaryResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.Valid;

/**
 * 支付渠道运营管理（仅私网，调用方 operations-console-bff）
 * <li>写命令必带 {@value ChannelConnectorHeaders#ACTOR_HEADER}（规范正十进制 Account ID），缺失或格式不符 400；查询不带。</li>
 * <li>业务拒绝 400 BUSINESS_CHECK_FAILED（detail 为中文原因）、字段校验 422、唯一冲突或并发 409；写入不处理 Idempotency-Key。</li>
 * <li>每次写入与业务变更同事务写一条渠道配置变更记录。</li>
 */
@HttpExchange(url = "/channel-connector/v1/channels", accept = "application/json")
public interface PaymentChannelApi {

    /**
     * 创建 DRAFT 渠道
     *
     * @param actorId 操作者 Account ID
     * @param body 渠道编码与名称
     * @return 201 与渠道；渠道编码已存在 409
     */
    @PostExchange
    ResponseEntity<ChannelResponseBody> createChannel(@RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @Valid @RequestBody CreateChannelRequestBody body);

    /**
     * 分页查询渠道，按 update_time、channel_id 降序
     *
     * @param status 可选状态筛选
     * @param keyword 可选渠道编码或名称前缀
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，仅允许 10、20、50、100、200
     * @return 渠道列表项与总数
     */
    @GetExchange
    PagingResponseBody<ChannelSummaryResponseBody> listChannels(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 渠道详情；不调用 Platform
     *
     * @param channelId 渠道 ID
     * @return 渠道详情；不存在 400
     */
    @GetExchange("/{channelId}")
    ChannelResponseBody getChannel(@PathVariable("channelId") String channelId);

    /**
     * 修改渠道名称
     *
     * @param actorId 操作者 Account ID
     * @param channelId 渠道 ID
     * @param body 新名称
     * @return 修改后的渠道；不存在或已关闭 400
     */
    @PutExchange("/{channelId}")
    ChannelResponseBody updateChannel(@RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @PathVariable("channelId") String channelId, @Valid @RequestBody UpdateChannelRequestBody body);

    /**
     * 变更渠道状态
     * <li>激活前在事务外向 Platform 查询同编码接入方：不存在 400；Platform 不可用 500，不写库。</li>
     * <li>关闭要求渠道下没有未关闭的技术账号与 MID。</li>
     *
     * @param actorId 操作者 Account ID
     * @param channelId 渠道 ID
     * @param body 目标状态与可选原因
     * @return 变更后的渠道；非法迁移 400；并发变更 409
     */
    @PostExchange("/{channelId}/status")
    ChannelResponseBody changeChannelStatus(@RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @PathVariable("channelId") String channelId, @Valid @RequestBody ChangeChannelStatusRequestBody body);
}
