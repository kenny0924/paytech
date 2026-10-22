package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CapabilityDefinitionsResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChangeChannelCallGrantStatusRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChangeChannelStatusRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChangeMidCapabilityStatusRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelAccountResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelAccountSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelCallGrantResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelConfigChangeResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelMidResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelMidSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ConfigureChannelAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelCallGrantRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelMidRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateChannelRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.CreateMidCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.MidCapabilityResponseBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.RebindChannelMidAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.ReplaceMidCapabilityDetailsRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.UpdateChannelAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.UpdateChannelEndpointsRequestBody;
import com.paytech.operations.console.bff.service.model.ChannelModels.UpdateChannelRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * channel-connector 渠道（含出站地址）、技术账号、MID、能力、调用授权与变更记录的本地端口；状态迁移、归属、渠道类型规则、出站地址防护、
 * 调用授权规则与 Vault Protect 归 channel-connector
 * <li>命令以会话 Account ID 作为 X-Gateway-Actor-Subject 转发；查询不携带操作人。</li>
 * <li>每个方法只调用一次 channel-connector，不重试。</li>
 *
 * @author Claude
 */
public interface ChannelConfigurationClient {

    /**
     * 渠道分页
     *
     * @param channelType 可选渠道类型
     * @param status 可选状态
     * @param keyword 可选渠道编码或名称前缀
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 渠道列表项与总数
     */
    PagingResponseBody<ChannelSummaryResponseBody> listChannels(String channelType, String status, String keyword, int page,
        int size);

    /**
     * 渠道详情
     *
     * @param channelId 渠道 ID
     * @return 渠道
     */
    ChannelResponseBody channel(String channelId);

    /**
     * 创建 DRAFT 渠道
     *
     * @param operator 操作人
     * @param request 渠道编码、名称、类型与描述
     * @return 渠道
     */
    ChannelResponseBody createChannel(OperatorContext operator, CreateChannelRequestBody request);

    /**
     * 修改渠道名称与描述
     *
     * @param operator 操作人
     * @param channelId 渠道 ID
     * @param request 新名称与描述
     * @return 渠道
     */
    ChannelResponseBody updateChannel(OperatorContext operator, String channelId, UpdateChannelRequestBody request);

    /**
     * 整组替换渠道出站地址
     *
     * @param operator 操作人
     * @param channelId 渠道 ID
     * @param request 期望版本与出站地址
     * @return 渠道
     */
    ChannelResponseBody updateChannelEndpoints(OperatorContext operator, String channelId,
        UpdateChannelEndpointsRequestBody request);

    /**
     * 变更渠道状态
     *
     * @param operator 操作人
     * @param channelId 渠道 ID
     * @param request 目标状态与原因
     * @return 渠道
     */
    ChannelResponseBody changeChannelStatus(OperatorContext operator, String channelId,
        ChangeChannelStatusRequestBody request);

    /**
     * 技术账号分页
     *
     * @param channelId 可选渠道 ID
     * @param entityNo 可选 OE 编号
     * @param status 可选状态
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 账号列表项与总数
     */
    PagingResponseBody<ChannelAccountSummaryResponseBody> listAccounts(String channelId, String entityNo, String status,
        int page, int size);

    /**
     * 技术账号详情
     *
     * @param channelAccountId 技术账号 ID
     * @return 账号
     */
    ChannelAccountResponseBody account(String channelAccountId);

    /**
     * 创建 DRAFT 技术账号
     *
     * @param operator 操作人
     * @param request 渠道、OE、名称与备注
     * @return 账号
     */
    ChannelAccountResponseBody createAccount(OperatorContext operator, CreateChannelAccountRequestBody request);

    /**
     * 修改技术账号名称与备注
     *
     * @param operator 操作人
     * @param channelAccountId 技术账号 ID
     * @param request 名称与备注
     * @return 账号
     */
    ChannelAccountResponseBody updateAccount(OperatorContext operator, String channelAccountId,
        UpdateChannelAccountRequestBody request);

    /**
     * 整组写入技术账号参数与凭据
     *
     * @param operator 操作人
     * @param channelAccountId 技术账号 ID
     * @param request 参数与凭据明文
     * @return 账号（只含配置标志与 Token 末 4 位）
     */
    ChannelAccountResponseBody configureAccount(OperatorContext operator, String channelAccountId,
        ConfigureChannelAccountRequestBody request);

    /**
     * 变更技术账号状态
     *
     * @param operator 操作人
     * @param channelAccountId 技术账号 ID
     * @param request 目标状态与原因
     * @return 账号
     */
    ChannelAccountResponseBody changeAccountStatus(OperatorContext operator, String channelAccountId,
        ChangeChannelStatusRequestBody request);

    /**
     * MID 分页
     *
     * @param channelId 可选渠道 ID
     * @param channelAccountId 可选技术账号 ID
     * @param entityNo 可选 OE 编号
     * @param status 可选状态
     * @param externalMidCode 可选外部 MID 前缀
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return MID 列表项与总数
     */
    PagingResponseBody<ChannelMidSummaryResponseBody> listMids(String channelId, String channelAccountId, String entityNo,
        String status, String externalMidCode, int page, int size);

    /**
     * MID 详情，含能力组与明细
     *
     * @param midId MID ID
     * @return MID
     */
    ChannelMidResponseBody mid(String midId);

    /**
     * 创建 DRAFT MID
     *
     * @param operator 操作人
     * @param request 渠道、账号、外部 MID 与 OE
     * @return MID
     */
    ChannelMidResponseBody createMid(OperatorContext operator, CreateChannelMidRequestBody request);

    /**
     * MID 改绑技术账号
     *
     * @param operator 操作人
     * @param midId MID ID
     * @param request 目标账号
     * @return MID
     */
    ChannelMidResponseBody rebindMidAccount(OperatorContext operator, String midId, RebindChannelMidAccountRequestBody request);

    /**
     * 变更 MID 状态
     *
     * @param operator 操作人
     * @param midId MID ID
     * @param request 目标状态与原因
     * @return MID
     */
    ChannelMidResponseBody changeMidStatus(OperatorContext operator, String midId, ChangeChannelStatusRequestBody request);

    /**
     * 能力目录
     *
     * @return 能力代码、值类型与允许值
     */
    CapabilityDefinitionsResponseBody capabilityDefinitions();

    /**
     * 创建能力条件组
     *
     * @param operator 操作人
     * @param request 六维条件与明细
     * @return 能力组
     */
    MidCapabilityResponseBody createCapability(OperatorContext operator, CreateMidCapabilityRequestBody request);

    /**
     * 整组替换能力明细
     *
     * @param operator 操作人
     * @param midCapabilityId 能力组 ID
     * @param request 全部明细
     * @return 能力组
     */
    MidCapabilityResponseBody replaceCapabilityDetails(OperatorContext operator, String midCapabilityId,
        ReplaceMidCapabilityDetailsRequestBody request);

    /**
     * 启用或停用能力条件组
     *
     * @param operator 操作人
     * @param midCapabilityId 能力组 ID
     * @param request 目标状态
     * @return 能力组
     */
    MidCapabilityResponseBody changeCapabilityStatus(OperatorContext operator, String midCapabilityId,
        ChangeMidCapabilityStatusRequestBody request);

    /**
     * 一个对象的变更记录分页
     *
     * @param targetType 对象类型
     * @param targetId 对象 ID
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 变更记录与总数
     */
    PagingResponseBody<ChannelConfigChangeResponseBody> listChanges(String targetType, String targetId, int page, int size);

    /**
     * 调用授权分页
     *
     * @param channelId 可选渠道 ID
     * @param callerService 可选调用方服务名
     * @param standardApiCode 可选标准 API 编码
     * @param grantStatus 可选状态
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 授权列表项与总数
     */
    PagingResponseBody<ChannelCallGrantResponseBody> listCallGrants(String channelId, String callerService,
        String standardApiCode, String grantStatus, int page, int size);

    /**
     * 新建 ACTIVE 调用授权
     *
     * @param operator 操作人
     * @param request 渠道、调用方服务名与标准 API 编码
     * @return 授权
     */
    ChannelCallGrantResponseBody createCallGrant(OperatorContext operator, CreateChannelCallGrantRequestBody request);

    /**
     * 启用或停用调用授权
     *
     * @param operator 操作人
     * @param callGrantId 授权 ID
     * @param request 目标状态、期望版本与原因
     * @return 授权
     */
    ChannelCallGrantResponseBody changeCallGrantStatus(OperatorContext operator, String callGrantId,
        ChangeChannelCallGrantStatusRequestBody request);
}
