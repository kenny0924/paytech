package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.body;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.request;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.ChannelContractConverter.write;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.change.ChannelConfigChangeApi;
import com.paytech.channel.connector.api.channel.ChannelAccountApi;
import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.channel.connector.api.channel.MidCapabilityApi;
import com.paytech.channel.connector.api.channel.PaymentChannelApi;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ChannelConfigurationClient;
import com.paytech.operations.console.bff.service.model.ChannelModels.CapabilityDefinitionsResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChangeChannelStatusRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChangeMidCapabilityStatusRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelAccountResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelAccountSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelConfigChangeResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelMidResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelMidSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ConfigureChannelAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelMidRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateMidCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.MidCapabilityResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.RebindChannelMidAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ReplaceMidCapabilityDetailsRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.UpdateChannelAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.UpdateChannelRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * channel-connector 渠道、技术账号、MID、能力与变更记录接口的 HTTP 转发：每个入口只调用一次 channel-connector，不重试
 * <li>命令把会话 Account ID 填入 X-Gateway-Actor-Subject；Owner 4xx 由开启 Owner Problem detail 的代理工厂转为带 Owner 原因的
 * 本地异常，本类不捕获（422 除外：字段路径投影回页面 Body）。</li>
 * <li>回执的对象身份必须与请求一致，状态变更回执的状态必须等于目标状态；详情与目录必须带集合。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class ChannelConfigurationClientImpl implements ChannelConfigurationClient {
    private final ChannelMidApi midApi;
    private final ChannelAccountApi accountApi;
    private final MidCapabilityApi capabilityApi;
    private final PaymentChannelApi channelApi;
    private final ChannelConfigChangeApi changeApi;

    /** 渠道分页。 */
    @Override
    public PagingResponseBody<ChannelSummaryResponseBody> listChannels(String status, String keyword, int page, int size) {
        return page(channelApi.listChannels(status, keyword, page, size), page, size, ChannelSummaryResponseBody.class);
    }

    /** 渠道详情；回执渠道 ID 与请求一致。 */
    @Override
    public ChannelResponseBody channel(String channelId) {
        var channel = convert(channelApi.getChannel(channelId), ChannelResponseBody.class);
        requireSame(channelId, channel.getChannelId());
        return channel;
    }

    /** 创建渠道；回执渠道编码与请求一致。 */
    @Override
    public ChannelResponseBody createChannel(OperatorContext operator, CreateChannelRequestBody request) {
        var channel = write(request, () -> body(channelApi.createChannel(operator.accountId(), request(request,
            com.paytech.channel.connector.api.channel.request.CreateChannelRequestBody.class))), ChannelResponseBody.class);
        requireSame(request.getChannelCode(), channel.getChannelCode());
        return channel;
    }

    /** 修改渠道名称。 */
    @Override
    public ChannelResponseBody updateChannel(OperatorContext operator, String channelId, UpdateChannelRequestBody request) {
        return sameChannel(channelId, request, () -> channelApi.updateChannel(operator.accountId(), channelId, request(request,
            com.paytech.channel.connector.api.channel.request.UpdateChannelRequestBody.class)));
    }

    /** 变更渠道状态；回执状态等于目标状态。 */
    @Override
    public ChannelResponseBody changeChannelStatus(OperatorContext operator, String channelId,
        ChangeChannelStatusRequestBody request) {
        var channel = sameChannel(channelId, request, () -> channelApi.changeChannelStatus(operator.accountId(), channelId,
            status(request)));
        requireSame(request.getTargetStatus(), channel.getStatus());
        return channel;
    }

    /** 技术账号分页。 */
    @Override
    public PagingResponseBody<ChannelAccountSummaryResponseBody> listAccounts(String channelId, String entityNo,
        String status, int page, int size) {
        return page(accountApi.listChannelAccounts(channelId, entityNo, status, page, size), page, size,
            ChannelAccountSummaryResponseBody.class);
    }

    /** 技术账号详情；回执账号 ID 与请求一致。 */
    @Override
    public ChannelAccountResponseBody account(String channelAccountId) {
        var account = convert(accountApi.getChannelAccount(channelAccountId), ChannelAccountResponseBody.class);
        requireSame(channelAccountId, account.getChannelAccountId());
        return account;
    }

    /** 创建技术账号；回执渠道与 OE 与请求一致。 */
    @Override
    public ChannelAccountResponseBody createAccount(OperatorContext operator, CreateChannelAccountRequestBody request) {
        var account = write(request, () -> body(accountApi.createChannelAccount(operator.accountId(), request(request,
            com.paytech.channel.connector.api.channel.request.CreateChannelAccountRequestBody.class))),
            ChannelAccountResponseBody.class);
        requireSame(request.getChannelId(), account.getChannelId());
        requireSame(request.getEntityNo(), account.getEntityNo());
        return account;
    }

    /** 修改技术账号名称与备注。 */
    @Override
    public ChannelAccountResponseBody updateAccount(OperatorContext operator, String channelAccountId,
        UpdateChannelAccountRequestBody request) {
        return sameAccount(channelAccountId, request, () -> accountApi.updateChannelAccount(operator.accountId(),
            channelAccountId, request(request,
                com.paytech.channel.connector.api.channel.request.UpdateChannelAccountRequestBody.class)));
    }

    /** 整组写入参数与凭据；回执必须标为已配置。 */
    @Override
    public ChannelAccountResponseBody configureAccount(OperatorContext operator, String channelAccountId,
        ConfigureChannelAccountRequestBody request) {
        var account = sameAccount(channelAccountId, request, () -> accountApi.configureChannelAccount(operator.accountId(),
            channelAccountId, request(request,
                com.paytech.channel.connector.api.channel.request.ConfigureChannelAccountRequestBody.class)));
        require(account.isConfigured());
        return account;
    }

    /** 变更技术账号状态；回执状态等于目标状态。 */
    @Override
    public ChannelAccountResponseBody changeAccountStatus(OperatorContext operator, String channelAccountId,
        ChangeChannelStatusRequestBody request) {
        var account = sameAccount(channelAccountId, request, () -> accountApi.changeChannelAccountStatus(
            operator.accountId(), channelAccountId, status(request)));
        requireSame(request.getTargetStatus(), account.getStatus());
        return account;
    }

    /** MID 分页。 */
    @Override
    public PagingResponseBody<ChannelMidSummaryResponseBody> listMids(String channelId, String channelAccountId,
        String entityNo, String status, String externalMidCode, int page, int size) {
        return page(midApi.listChannelMids(channelId, channelAccountId, entityNo, status, externalMidCode, page, size),
            page, size,
            ChannelMidSummaryResponseBody.class);
    }

    /** MID 详情；回执 MID 与请求一致且带能力组集合。 */
    @Override
    public ChannelMidResponseBody mid(String midId) {
        var mid = convert(midApi.getChannelMid(midId), ChannelMidResponseBody.class);
        requireSame(midId, mid.getMidId());
        requireCapabilities(mid);
        return mid;
    }

    /** 创建 MID；回执渠道、账号、外部 MID 与 OE 与请求一致。 */
    @Override
    public ChannelMidResponseBody createMid(OperatorContext operator, CreateChannelMidRequestBody request) {
        var mid = write(request, () -> body(midApi.createChannelMid(operator.accountId(), request(request,
            com.paytech.channel.connector.api.channel.request.CreateChannelMidRequestBody.class))), ChannelMidResponseBody.class);
        requireSame(request.getChannelId(), mid.getChannelId());
        requireSame(request.getChannelAccountId(), mid.getChannelAccountId());
        requireSame(request.getExternalMidCode(), mid.getExternalMidCode());
        requireSame(request.getEntityNo(), mid.getEntityNo());
        requireCapabilities(mid);
        return mid;
    }

    /** MID 改绑；回执当前账号等于目标账号。 */
    @Override
    public ChannelMidResponseBody rebindMidAccount(OperatorContext operator, String midId,
        RebindChannelMidAccountRequestBody request) {
        var mid = sameMid(midId, request, () -> midApi.rebindChannelMidAccount(operator.accountId(), midId, request(request,
            com.paytech.channel.connector.api.channel.request.RebindChannelMidAccountRequestBody.class)));
        requireSame(request.getChannelAccountId(), mid.getChannelAccountId());
        return mid;
    }

    /** 变更 MID 状态；回执状态等于目标状态。 */
    @Override
    public ChannelMidResponseBody changeMidStatus(OperatorContext operator, String midId,
        ChangeChannelStatusRequestBody request) {
        var mid = sameMid(midId, request, () -> midApi.changeChannelMidStatus(operator.accountId(), midId, status(request)));
        requireSame(request.getTargetStatus(), mid.getStatus());
        return mid;
    }

    /** 能力目录；集合必须存在且无空项。 */
    @Override
    public CapabilityDefinitionsResponseBody capabilityDefinitions() {
        var definitions = convert(capabilityApi.listCapabilityDefinitions(), CapabilityDefinitionsResponseBody.class);
        require(definitions.getDefinitions() != null && definitions.getDefinitions().stream().allMatch(Objects::nonNull));
        return definitions;
    }

    /** 创建能力组；回执 MID 与请求一致。 */
    @Override
    public MidCapabilityResponseBody createCapability(OperatorContext operator, CreateMidCapabilityRequestBody request) {
        var capability = write(request, () -> body(capabilityApi.createMidCapability(operator.accountId(), request(request,
            com.paytech.channel.connector.api.channel.request.CreateMidCapabilityRequestBody.class))),
            MidCapabilityResponseBody.class);
        requireSame(request.getMidId(), capability.getMidId());
        require(capability.getDetails() != null);
        return capability;
    }

    /** 整组替换能力明细。 */
    @Override
    public MidCapabilityResponseBody replaceCapabilityDetails(OperatorContext operator, String midCapabilityId,
        ReplaceMidCapabilityDetailsRequestBody request) {
        return sameCapability(midCapabilityId, request, () -> capabilityApi.replaceMidCapabilityDetails(
            operator.accountId(), midCapabilityId, request(request,
                com.paytech.channel.connector.api.channel.request.ReplaceMidCapabilityDetailsRequestBody.class)));
    }

    /** 启用或停用能力组；回执状态等于目标状态。 */
    @Override
    public MidCapabilityResponseBody changeCapabilityStatus(OperatorContext operator, String midCapabilityId,
        ChangeMidCapabilityStatusRequestBody request) {
        var capability = sameCapability(midCapabilityId, request, () -> capabilityApi.changeMidCapabilityStatus(
            operator.accountId(), midCapabilityId, request(request,
                com.paytech.channel.connector.api.channel.request.ChangeMidCapabilityStatusRequestBody.class)));
        requireSame(request.getTargetStatus(), capability.getStatus());
        return capability;
    }

    /** 变更记录分页；每条记录必须属于请求的对象。 */
    @Override
    public PagingResponseBody<ChannelConfigChangeResponseBody> listChanges(String targetType, String targetId, int page,
        int size) {
        var changes = page(changeApi.listConfigChanges(targetType, targetId, page, size), page, size,
            ChannelConfigChangeResponseBody.class);
        require(changes.items().stream().allMatch(change -> targetType.equals(change.getTargetType())
            && targetId.equals(change.getTargetId())));
        return changes;
    }

    /** 状态变更请求转 Owner 合同。 */
    private static com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody status(
        ChangeChannelStatusRequestBody request) {
        return request(request, com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody.class);
    }

    /** MID 回执必须带能力组集合（新建 MID 为空列表）。 */
    private static void requireCapabilities(ChannelMidResponseBody mid) {
        require(mid.getCapabilities() != null && mid.getCapabilities().stream()
            .allMatch(capability -> capability != null && capability.getDetails() != null));
    }

    /** 渠道命令回执；渠道 ID 与路径一致。 */
    private static ChannelResponseBody sameChannel(String channelId, Object request, Supplier<Object> call) {
        var channel = write(request, call, ChannelResponseBody.class);
        requireSame(channelId, channel.getChannelId());
        return channel;
    }

    /** 账号命令回执；账号 ID 与路径一致。 */
    private static ChannelAccountResponseBody sameAccount(String channelAccountId, Object request, Supplier<Object> call) {
        var account = write(request, call, ChannelAccountResponseBody.class);
        requireSame(channelAccountId, account.getChannelAccountId());
        return account;
    }

    /** MID 命令回执；MID 与路径一致且带能力组集合。 */
    private static ChannelMidResponseBody sameMid(String midId, Object request, Supplier<Object> call) {
        var mid = write(request, call, ChannelMidResponseBody.class);
        requireSame(midId, mid.getMidId());
        requireCapabilities(mid);
        return mid;
    }

    /** 能力组命令回执；能力组 ID 与路径一致且带明细集合。 */
    private static MidCapabilityResponseBody sameCapability(String midCapabilityId, Object request, Supplier<Object> call) {
        var capability = write(request, call, MidCapabilityResponseBody.class);
        requireSame(midCapabilityId, capability.getMidCapabilityId());
        require(capability.getDetails() != null);
        return capability;
    }
}
