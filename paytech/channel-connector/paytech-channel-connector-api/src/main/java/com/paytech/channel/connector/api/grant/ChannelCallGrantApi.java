package com.paytech.channel.connector.api.grant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.channel.connector.api.grant.request.ChangeChannelCallGrantStatusRequestBody;
import com.paytech.channel.connector.api.grant.request.CreateChannelCallGrantRequestBody;
import com.paytech.channel.connector.api.grant.response.ChannelCallGrantResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.Valid;

/**
 * 服务渠道调用授权（调用方服务 × 渠道 × 标准 API）的运营维护（仅私网，调用方 operations-console-bff）
 * <li>只有 ACTIVE 授权的组合才能经通用执行入口调用服务渠道；授权只停用、不删除，同一组合只有一行，再启用沿用该行并递增版本。</li>
 * <li>写命令必带 {@value ChannelConnectorHeaders#ACTOR_HEADER}，与授权变更同事务写一条 CHANNEL_CALL_GRANT 变更记录；查询不带。</li>
 * <li>业务拒绝 400 BUSINESS_CHECK_FAILED、字段校验 422、组合重复或版本冲突 409。</li>
 */
@HttpExchange(url = "/channel-connector/v1/channel-call-grants", accept = "application/json")
public interface ChannelCallGrantApi {

    /**
     * 分页查询调用授权，按 update_time、call_grant_id 降序
     *
     * @param channelId 可选渠道 ID，正十进制
     * @param callerService 可选调用方服务名，精确匹配
     * @param standardApiCode 可选标准 API 编码，精确匹配
     * @param grantStatus 可选状态：ACTIVE、DISABLED
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，仅允许 10、20、50、100、200
     * @return 授权列表项（含标准 API 编码与名称）与总数
     */
    @GetExchange
    PagingResponseBody<ChannelCallGrantResponseBody> listChannelCallGrants(
        @RequestParam(value = "channelId", required = false) String channelId,
        @RequestParam(value = "callerService", required = false) String callerService,
        @RequestParam(value = "standardApiCode", required = false) String standardApiCode,
        @RequestParam(value = "grantStatus", required = false) String grantStatus,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 新建 ACTIVE 调用授权
     * <li>渠道须为服务渠道、标准 API 须已被该渠道实现，否则 400。</li>
     * <li>同一「调用方 × 渠道 × 标准 API」已存在时 409，detail 含现有授权 ID 与状态；停用的授权应改为再启用。</li>
     *
     * @param actorId 操作者 Account ID
     * @param body 渠道、调用方服务名与标准 API 编码
     * @return 201 与授权
     */
    @PostExchange
    ResponseEntity<ChannelCallGrantResponseBody> createChannelCallGrant(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @Valid @RequestBody CreateChannelCallGrantRequestBody body);

    /**
     * 启用或停用调用授权
     * <li>按 expectedVersion 条件更新并递增版本，版本不一致 409；目标状态与当前相同 400。</li>
     * <li>再启用要求渠道仍实现该标准 API。</li>
     *
     * @param actorId 操作者 Account ID
     * @param callGrantId 授权 ID
     * @param body 目标状态、期望版本与可选原因
     * @return 变更后的授权；不存在 400
     */
    @PostExchange("/{callGrantId}/status")
    ChannelCallGrantResponseBody changeChannelCallGrantStatus(
        @RequestHeader(ChannelConnectorHeaders.ACTOR_HEADER) String actorId,
        @PathVariable("callGrantId") String callGrantId,
        @Valid @RequestBody ChangeChannelCallGrantStatusRequestBody body);
}
