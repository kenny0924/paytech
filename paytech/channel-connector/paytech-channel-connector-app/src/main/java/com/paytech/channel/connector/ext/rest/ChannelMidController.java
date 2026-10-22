package com.paytech.channel.connector.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelMidRequestBody;
import com.paytech.channel.connector.api.channel.request.RebindChannelMidAccountRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidSummaryResponseBody;
import com.paytech.channel.connector.domainservice.service.ChannelMidService;
import com.paytech.channel.connector.ext.rest.converter.ChannelMidConverter;
import com.paytech.channel.connector.service.processor.ChangeChannelMidStatusProcessor;
import com.paytech.channel.connector.service.processor.CreateChannelMidProcessor;
import com.paytech.channel.connector.service.processor.ListChannelMidsProcessor;
import com.paytech.channel.connector.service.processor.RebindChannelMidAccountProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** MID 管理的 HTTP 协议适配：列表与详情直接委托本地 Service，写入口委托 Processor。 */
@RestController
@AllArgsConstructor
public class ChannelMidController implements ChannelMidApi {
    private final ChannelMidConverter converter;
    private final ListChannelMidsProcessor listChannelMidsProcessor;

    // ---- CC-B 追加：MID 写入口与详情 ----
    private final ChannelMidService channelMidService;
    private final CreateChannelMidProcessor createChannelMidProcessor;
    private final ChangeChannelMidStatusProcessor changeChannelMidStatusProcessor;
    private final RebindChannelMidAccountProcessor rebindChannelMidAccountProcessor;

    /** 分页查询 MID。 */
    @Override
    public PagingResponseBody<ChannelMidSummaryResponseBody> listChannelMids(String channelId, String channelAccountId,
        String entityNo, String status, String externalMidCode, Integer page, Integer size) {
        return listChannelMidsProcessor.process(
            converter.toCriteria(channelId, channelAccountId, entityNo, status, externalMidCode, page, size))
            .map(converter::toSummaryResponseBody);
    }

    /** 创建 MID 并返回 201。 */
    @Override
    public ResponseEntity<ChannelMidResponseBody> createChannelMid(String actorId, CreateChannelMidRequestBody body) {
        ChannelMidResponseBody response =
            converter.toResponseBody(createChannelMidProcessor.process(converter.toRequest(actorId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** MID 详情，含能力组与明细。 */
    @Override
    public ChannelMidResponseBody getChannelMid(String midId) {
        return converter.toResponseBody(channelMidService.getMid(converter.midId(midId)));
    }

    /** MID 改绑技术账号。 */
    @Override
    public ChannelMidResponseBody rebindChannelMidAccount(String actorId, String midId,
        RebindChannelMidAccountRequestBody body) {
        return converter.toResponseBody(
            rebindChannelMidAccountProcessor.process(converter.toRequest(actorId, midId, body)));
    }

    /** 变更 MID 状态。 */
    @Override
    public ChannelMidResponseBody changeChannelMidStatus(String actorId, String midId,
        ChangeChannelStatusRequestBody body) {
        return converter.toResponseBody(
            changeChannelMidStatusProcessor.process(converter.toStatusRequest(actorId, midId, body)));
    }
}
