package com.paytech.channel.connector.ext.rest.converter;

import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.ConfigureChannelAccountRequestBody;
import com.paytech.channel.connector.service.processor.request.ChangeChannelStatusRequest;
import com.paytech.channel.connector.api.channel.request.CreateChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.response.ChannelAccountResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelAccountSummaryResponseBody;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelAccountDetailView;
import com.paytech.channel.connector.service.processor.request.ConfigureChannelAccountRequest;
import com.paytech.channel.connector.service.processor.request.CreateChannelAccountRequest;
import com.paytech.channel.connector.service.processor.request.UpdateChannelAccountRequest;

/**
 * 渠道技术账号接口的 Wire 模型与内部请求 / 结果转换
 * <li>响应只输出是否已配置与 Vault Token 末 4 位，绝不输出 Token 全文、参数或凭据。</li>
 */
@Component
public class ChannelAccountConverter {
    private static final int TOKEN_SUFFIX_LENGTH = 4;

    /** 转换创建请求。 */
    public CreateChannelAccountRequest toRequest(String actorId, CreateChannelAccountRequestBody body) {
        CreateChannelAccountRequest request = new CreateChannelAccountRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setChannelId(ChannelConfigurationWire.id("channelId", body.getChannelId()));
        request.setEntityNo(body.getEntityNo());
        request.setAccountName(body.getAccountName());
        request.setRemark(body.getRemark());
        return request;
    }

    /** 转换修改请求。 */
    public UpdateChannelAccountRequest toRequest(String actorId, String channelAccountId,
        UpdateChannelAccountRequestBody body) {
        UpdateChannelAccountRequest request = new UpdateChannelAccountRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setChannelAccountId(ChannelConfigurationWire.id("channelAccountId", channelAccountId));
        request.setAccountName(body.getAccountName());
        request.setRemark(body.getRemark());
        return request;
    }

    /** 转换配置写入请求；复制键值，不记录内容。 */
    public ConfigureChannelAccountRequest toRequest(String actorId, String channelAccountId,
        ConfigureChannelAccountRequestBody body) {
        ConfigureChannelAccountRequest request = new ConfigureChannelAccountRequest();
        request.setActorId(ChannelConfigurationWire.actor(actorId));
        request.setChannelAccountId(ChannelConfigurationWire.id("channelAccountId", channelAccountId));
        request.setParameters(body.getParameters() == null ? null : new LinkedHashMap<>(body.getParameters()));
        request.setCredentials(body.getCredentials() == null ? null : new LinkedHashMap<>(body.getCredentials()));
        return request;
    }

    /** 解析账号路径 ID。 */
    public long channelAccountId(String channelAccountId) {
        return ChannelConfigurationWire.id("channelAccountId", channelAccountId);
    }

    /** 解析可空渠道 ID 查询参数。 */
    public Long optionalChannelId(String channelId) {
        return ChannelConfigurationWire.optionalId("channelId", channelId);
    }

    /** 解析可空状态查询参数。 */
    public ChannelStatus status(String status) {
        return ChannelConfigurationWire.channelStatus(status);
    }

    /** 转换账号状态变更请求。 */
    public ChangeChannelStatusRequest toStatusRequest(String actorId, String channelAccountId,
        ChangeChannelStatusRequestBody body) {
        return ChannelConfigurationWire.statusRequest(actorId, "channelAccountId", channelAccountId, body);
    }

    /** 转换账号详情。 */
    public ChannelAccountResponseBody toResponseBody(ChannelAccountDetailView view) {
        ChannelAccount account = view.getAccount();
        ChannelAccountResponseBody body = new ChannelAccountResponseBody();
        body.setChannelAccountId(account.getChannelAccountId().toString());
        body.setChannelId(account.getChannelId().toString());
        body.setEntityNo(account.getEntityNo());
        body.setAccountName(account.getAccountName());
        body.setStatus(account.getAccountStatus().getCode());
        body.setRemark(account.getRemark());
        body.setConfigured(account.getAccountVaultToken() != null);
        body.setVaultTokenSuffix(tokenSuffix(account.getAccountVaultToken()));
        body.setBoundMidCount(view.getBoundMidCount());
        body.setCreateTime(account.getCreateTime());
        body.setUpdateTime(account.getUpdateTime());
        return body;
    }

    /** 转换账号列表项。 */
    public ChannelAccountSummaryResponseBody toSummaryResponseBody(ChannelAccount account) {
        ChannelAccountSummaryResponseBody body = new ChannelAccountSummaryResponseBody();
        body.setChannelAccountId(account.getChannelAccountId().toString());
        body.setChannelId(account.getChannelId().toString());
        body.setEntityNo(account.getEntityNo());
        body.setAccountName(account.getAccountName());
        body.setStatus(account.getAccountStatus().getCode());
        body.setConfigured(account.getAccountVaultToken() != null);
        body.setVaultTokenSuffix(tokenSuffix(account.getAccountVaultToken()));
        body.setUpdateTime(account.getUpdateTime());
        return body;
    }

    /** Token 末 4 位；未配置为 null。 */
    static String tokenSuffix(String token) {
        if (token == null || token.length() <= TOKEN_SUFFIX_LENGTH) {
            return null;
        }
        return token.substring(token.length() - TOKEN_SUFFIX_LENGTH);
    }
}
