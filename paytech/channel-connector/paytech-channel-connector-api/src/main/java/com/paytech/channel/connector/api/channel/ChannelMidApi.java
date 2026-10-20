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
import com.paytech.channel.connector.api.channel.request.CreateChannelMidRequestBody;
import com.paytech.channel.connector.api.channel.request.RebindChannelMidAccountRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidSummaryResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * MID 运营管理（仅私网，调用方 operations-console-bff）
 * <li>写命令必带 {@value ChannelConnectorHeaders#ACTOR_HEADER}；查询（列表、详情）不带。错误语义同 {@link PaymentChannelApi}。</li>
 * <li>改绑只改变当前绑定；已冻结到支付尝试的 Channel / 账号 / MID 三元组不受影响。</li>
 */
@HttpExchange(url = "/channel-connector/v1/mids", accept = "application/json")
public interface ChannelMidApi {

    /**
     * 创建 DRAFT MID
     *
     * @param actorId 操作者 Account ID
     * @param body 渠道、账号、外部 MID 与 OE
     * @return 201 与 MID；账号不属于渠道、已关闭或 OE 不一致 400；同渠道外部 MID 重复 409
     */
    @PostExchange
    ResponseEntity<ChannelMidResponseBody> createChannelMid(@RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @Valid @RequestBody CreateChannelMidRequestBody body);

    /**
     * 分页查询 MID
     *
     * @param channelId 可选 Channel 主键，正十进制整数字符串
     * @param channelAccountId 可选当前技术账号主键，正十进制整数字符串
     * @param entityNo 可选 Operating Entity 编号，两位大写字母加三位数字
     * @param status 可选 MID 状态：DRAFT、ACTIVE、SUSPENDED、CLOSED
     * @param externalMidCode 可选外部 MID 前缀，[A-Za-z0-9_.-]{1,128}，通配符按文字处理
     * @param page 页码，默认 1，范围 1–100000
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按更新时间降序、MID 主键降序的分页结果，含渠道编码、账号名称与能力组计数；筛选或分页参数非法时 400 INVALID_REQUEST
     */
    @GetExchange
    PagingResponseBody<ChannelMidSummaryResponseBody> listChannelMids(
        @RequestParam(value = "channelId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String channelId,
        @RequestParam(value = "channelAccountId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}")
        String channelAccountId,
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "DRAFT|ACTIVE|SUSPENDED|CLOSED")
        String status,
        @RequestParam(value = "externalMidCode", required = false) @Pattern(regexp = "[A-Za-z0-9_.-]{1,128}")
        String externalMidCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * MID 详情，含能力条件组与明细
     *
     * @param midId MID ID
     * @return MID 详情；不存在 400
     */
    @GetExchange("/{midId}")
    ChannelMidResponseBody getChannelMid(@PathVariable("midId") String midId);

    /**
     * MID 改绑技术账号
     *
     * @param actorId 操作者 Account ID
     * @param midId MID ID
     * @param body 目标技术账号
     * @return 改绑后的 MID；MID 已关闭、目标账号跨渠道 / 跨 OE / 已关闭 400；并发 409
     */
    @PutExchange("/{midId}/account")
    ChannelMidResponseBody rebindChannelMidAccount(@RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @PathVariable("midId") String midId, @Valid @RequestBody RebindChannelMidAccountRequestBody body);

    /**
     * 变更 MID 状态
     * <li>激活要求渠道与当前账号均 ACTIVE；首次激活记录激活时间，关闭记录关闭时间。</li>
     *
     * @param actorId 操作者 Account ID
     * @param midId MID ID
     * @param body 目标状态与可选原因
     * @return 变更后的 MID；非法迁移 400；并发 409
     */
    @PostExchange("/{midId}/status")
    ChannelMidResponseBody changeChannelMidStatus(@RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @PathVariable("midId") String midId, @Valid @RequestBody ChangeChannelStatusRequestBody body);
}
