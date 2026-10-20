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
import com.paytech.channel.connector.api.channel.request.ConfigureChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelAccountResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelAccountSummaryResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.Valid;

/**
 * 渠道技术账号运营管理（仅私网，调用方 operations-console-bff）
 * <li>写命令必带 {@value ChannelConnectorHeaders#ACTOR_HEADER}；查询不带。错误语义同 {@link PaymentChannelApi}。</li>
 * <li>任何响应都不返回 Vault Token 全文、账号参数或凭据。</li>
 */
@HttpExchange(url = "/channel-connector/v1/channel-accounts", accept = "application/json")
public interface ChannelAccountApi {

    /**
     * 创建 DRAFT 技术账号，尚无配置
     *
     * @param actorId 操作者 Account ID
     * @param body 所属渠道、OE、名称与备注
     * @return 201 与账号；渠道不存在或已关闭 400
     */
    @PostExchange
    ResponseEntity<ChannelAccountResponseBody> createChannelAccount(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @Valid @RequestBody CreateChannelAccountRequestBody body);

    /**
     * 分页查询技术账号，按 update_time、channel_account_id 降序
     *
     * @param channelId 可选渠道 ID
     * @param entityNo 可选 OE 编号
     * @param status 可选状态
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @return 账号列表项与总数
     */
    @GetExchange
    PagingResponseBody<ChannelAccountSummaryResponseBody> listChannelAccounts(
        @RequestParam(value = "channelId", required = false) String channelId,
        @RequestParam(value = "entityNo", required = false) String entityNo,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 技术账号详情
     *
     * @param channelAccountId 技术账号 ID
     * @return 账号详情，只含配置标志与 Token 末 4 位；不存在 400
     */
    @GetExchange("/{channelAccountId}")
    ChannelAccountResponseBody getChannelAccount(@PathVariable("channelAccountId") String channelAccountId);

    /**
     * 修改账号名称与备注；OE 不可修改
     *
     * @param actorId 操作者 Account ID
     * @param channelAccountId 技术账号 ID
     * @param body 名称与备注
     * @return 修改后的账号；不存在或已关闭 400
     */
    @PutExchange("/{channelAccountId}")
    ChannelAccountResponseBody updateChannelAccount(@RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @PathVariable("channelAccountId") String channelAccountId, @Valid @RequestBody UpdateChannelAccountRequestBody body);

    /**
     * 写入账号参数与凭据
     * <li>事务外经 Vault Protect 生成 REFERENCE Token，再以短事务条件更新本地 Token；同内容重放复用同一幂等键。</li>
     * <li>Vault 拒绝按其错误返回、不可用 5xx，均不写库；旧 Token 不回收。</li>
     *
     * @param actorId 操作者 Account ID
     * @param channelAccountId 技术账号 ID
     * @param body 完整参数与凭据
     * @return 配置后的账号；不存在或已关闭 400
     */
    @PutExchange("/{channelAccountId}/configuration")
    ChannelAccountResponseBody configureChannelAccount(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @PathVariable("channelAccountId") String channelAccountId,
        @Valid @RequestBody ConfigureChannelAccountRequestBody body);

    /**
     * 变更账号状态
     * <li>激活要求渠道 ACTIVE 且已写入配置；关闭要求没有未关闭的 MID 绑定本账号。</li>
     *
     * @param actorId 操作者 Account ID
     * @param channelAccountId 技术账号 ID
     * @param body 目标状态与可选原因
     * @return 变更后的账号；非法迁移 400；并发变更 409
     */
    @PostExchange("/{channelAccountId}/status")
    ChannelAccountResponseBody changeChannelAccountStatus(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @PathVariable("channelAccountId") String channelAccountId, @Valid @RequestBody ChangeChannelStatusRequestBody body);
}
