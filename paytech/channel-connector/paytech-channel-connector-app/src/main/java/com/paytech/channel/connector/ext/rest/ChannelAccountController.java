package com.paytech.channel.connector.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.channel.ChannelAccountApi;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.ConfigureChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelAccountResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelAccountSummaryResponseBody;
import com.paytech.channel.connector.domainservice.service.ChannelAccountService;
import com.paytech.channel.connector.ext.rest.converter.ChannelAccountConverter;
import com.paytech.channel.connector.service.processor.ChangeChannelAccountStatusProcessor;
import com.paytech.channel.connector.service.processor.ConfigureChannelAccountProcessor;
import com.paytech.channel.connector.service.processor.CreateChannelAccountProcessor;
import com.paytech.channel.connector.service.processor.UpdateChannelAccountProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 渠道技术账号管理的 HTTP 协议适配：写入口委托 Processor，查询直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class ChannelAccountController implements ChannelAccountApi {
    private final ChannelAccountConverter converter;
    private final ChannelAccountService channelAccountService;
    private final CreateChannelAccountProcessor createChannelAccountProcessor;
    private final UpdateChannelAccountProcessor updateChannelAccountProcessor;
    private final ConfigureChannelAccountProcessor configureChannelAccountProcessor;
    private final ChangeChannelAccountStatusProcessor changeChannelAccountStatusProcessor;

    /** 创建账号并返回 201。 */
    @Override
    public ResponseEntity<ChannelAccountResponseBody> createChannelAccount(String actorId,
        CreateChannelAccountRequestBody body) {
        ChannelAccountResponseBody response =
            converter.toResponseBody(createChannelAccountProcessor.process(converter.toRequest(actorId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 分页查询账号。 */
    @Override
    public PagingResponseBody<ChannelAccountSummaryResponseBody> listChannelAccounts(String channelId, String entityNo,
        String status, Integer page, Integer size) {
        return channelAccountService.listAccounts(converter.optionalChannelId(channelId), entityNo,
            converter.status(status), PagingRequestBody.of(page, size)).map(converter::toSummaryResponseBody);
    }

    /** 账号详情。 */
    @Override
    public ChannelAccountResponseBody getChannelAccount(String channelAccountId) {
        return converter.toResponseBody(channelAccountService.getAccount(converter.channelAccountId(channelAccountId)));
    }

    /** 修改账号名称与备注。 */
    @Override
    public ChannelAccountResponseBody updateChannelAccount(String actorId, String channelAccountId,
        UpdateChannelAccountRequestBody body) {
        return converter.toResponseBody(
            updateChannelAccountProcessor.process(converter.toRequest(actorId, channelAccountId, body)));
    }

    /** 写入账号参数与凭据。 */
    @Override
    public ChannelAccountResponseBody configureChannelAccount(String actorId, String channelAccountId,
        ConfigureChannelAccountRequestBody body) {
        return converter.toResponseBody(
            configureChannelAccountProcessor.process(converter.toRequest(actorId, channelAccountId, body)));
    }

    /** 变更账号状态。 */
    @Override
    public ChannelAccountResponseBody changeChannelAccountStatus(String actorId, String channelAccountId,
        ChangeChannelStatusRequestBody body) {
        return converter.toResponseBody(
            changeChannelAccountStatusProcessor.process(converter.toStatusRequest(actorId, channelAccountId, body)));
    }
}
