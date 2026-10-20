package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * 渠道、技术账号、MID、能力与变更记录的运营入口（21 条路由，注册 DML 块 92）
 * <li>Gateway 绑定：GET 为 CHANNEL_CONFIG_READ（变更记录为 CHANNEL_CHANGE_READ）；写入为 CHANNEL_CONFIG_MANAGE；账号参数与凭据写入
 * 为独立的 CHANNEL_ACCOUNT_CREDENTIAL。</li>
 * <li>全部简单转发 channel-connector：操作人取自会话并经 X-Gateway-Actor-Subject 传递；Query 只放行块 92 登记的键。</li>
 * <li>账号配置请求含凭据明文，/channel-admin/v1/channel-accounts 在 web.logging 中只记元数据。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/channel-admin/v1")
public class ChannelConfigurationController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String STATUS = "DRAFT|ACTIVE|SUSPENDED|CLOSED";
    private static final String ENTITY_NO = "[A-Z]{2}[0-9]{3}";
    private static final String TARGET_TYPE = "CHANNEL|CHANNEL_ACCOUNT|CHANNEL_MID|MID_CAPABILITY|CHANNEL_COST_VERSION";

    private final ChannelConfigurationClient client;

    /** 渠道分页：按状态与编码 / 名称前缀筛选。 */
    @GetMapping("/channels")
    public PagingResponseBody<ChannelSummaryResponseBody> listChannels(HttpServletRequest servletRequest,
        @RequestParam(value = "status", required = false) @Pattern(regexp = STATUS) String status,
        @RequestParam(value = "keyword", required = false) @Size(max = 255) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("status", "keyword", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listChannels(status, keyword, paging.page(), paging.size());
    }

    /** 渠道详情。 */
    @GetMapping("/channels/{channelId}")
    public ChannelResponseBody channel(HttpServletRequest servletRequest,
        @PathVariable("channelId") @Pattern(regexp = ID) String channelId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.channel(channelId);
    }

    /** 创建 DRAFT 渠道。 */
    @PostMapping("/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelResponseBody createChannel(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid CreateChannelRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createChannel(operator, body);
    }

    /** 修改渠道名称。 */
    @PutMapping("/channels/{channelId}")
    public ChannelResponseBody updateChannel(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("channelId") @Pattern(regexp = ID) String channelId,
        @RequestBody @NotNull @Valid UpdateChannelRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.updateChannel(operator, channelId, body);
    }

    /** 变更渠道状态；激活由 channel-connector 核对 Platform 同编码接入方。 */
    @PostMapping("/channels/{channelId}/status")
    public ChannelResponseBody changeChannelStatus(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("channelId") @Pattern(regexp = ID) String channelId,
        @RequestBody @NotNull @Valid ChangeChannelStatusRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.changeChannelStatus(operator, channelId, body);
    }

    /** 技术账号分页。 */
    @GetMapping("/channel-accounts")
    public PagingResponseBody<ChannelAccountSummaryResponseBody> listAccounts(HttpServletRequest servletRequest,
        @RequestParam(value = "channelId", required = false) @Pattern(regexp = ID) String channelId,
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = ENTITY_NO) String entityNo,
        @RequestParam(value = "status", required = false) @Pattern(regexp = STATUS) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("channelId", "entityNo", "status", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listAccounts(channelId, entityNo, status, paging.page(), paging.size());
    }

    /** 技术账号详情；不含参数与凭据。 */
    @GetMapping("/channel-accounts/{channelAccountId}")
    public ChannelAccountResponseBody account(HttpServletRequest servletRequest,
        @PathVariable("channelAccountId") @Pattern(regexp = ID) String channelAccountId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.account(channelAccountId);
    }

    /** 创建 DRAFT 技术账号。 */
    @PostMapping("/channel-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelAccountResponseBody createAccount(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid CreateChannelAccountRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createAccount(operator, body);
    }

    /** 修改技术账号名称与备注。 */
    @PutMapping("/channel-accounts/{channelAccountId}")
    public ChannelAccountResponseBody updateAccount(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("channelAccountId") @Pattern(regexp = ID) String channelAccountId,
        @RequestBody @NotNull @Valid UpdateChannelAccountRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.updateAccount(operator, channelAccountId, body);
    }

    /** 整组写入参数与凭据（CHANNEL_ACCOUNT_CREDENTIAL）。 */
    @PutMapping("/channel-accounts/{channelAccountId}/configuration")
    public ChannelAccountResponseBody configureAccount(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("channelAccountId") @Pattern(regexp = ID) String channelAccountId,
        @RequestBody @NotNull @Valid ConfigureChannelAccountRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.configureAccount(operator, channelAccountId, body);
    }

    /** 变更技术账号状态。 */
    @PostMapping("/channel-accounts/{channelAccountId}/status")
    public ChannelAccountResponseBody changeAccountStatus(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("channelAccountId") @Pattern(regexp = ID) String channelAccountId,
        @RequestBody @NotNull @Valid ChangeChannelStatusRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.changeAccountStatus(operator, channelAccountId, body);
    }

    /** MID 分页；externalMidCode 为外部 MID 前缀；成本版本编制的 MID 下拉用 channelId + status=ACTIVE。 */
    @GetMapping("/mids")
    public PagingResponseBody<ChannelMidSummaryResponseBody> listMids(HttpServletRequest servletRequest,
        @RequestParam(value = "channelId", required = false) @Pattern(regexp = ID) String channelId,
        @RequestParam(value = "channelAccountId", required = false) @Pattern(regexp = ID) String channelAccountId,
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = ENTITY_NO) String entityNo,
        @RequestParam(value = "status", required = false) @Pattern(regexp = STATUS) String status,
        @RequestParam(value = "externalMidCode", required = false) @Size(max = 128) String externalMidCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("channelId", "channelAccountId", "entityNo", "status", "externalMidCode",
            "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listMids(channelId, channelAccountId, entityNo, status, externalMidCode, paging.page(), paging.size());
    }

    /** MID 详情，含能力组与明细。 */
    @GetMapping("/mids/{midId}")
    public ChannelMidResponseBody mid(HttpServletRequest servletRequest,
        @PathVariable("midId") @Pattern(regexp = ID) String midId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.mid(midId);
    }

    /** 创建 DRAFT MID。 */
    @PostMapping("/mids")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelMidResponseBody createMid(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid CreateChannelMidRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createMid(operator, body);
    }

    /** MID 改绑技术账号。 */
    @PutMapping("/mids/{midId}/account")
    public ChannelMidResponseBody rebindMidAccount(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("midId") @Pattern(regexp = ID) String midId,
        @RequestBody @NotNull @Valid RebindChannelMidAccountRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.rebindMidAccount(operator, midId, body);
    }

    /** 变更 MID 状态。 */
    @PostMapping("/mids/{midId}/status")
    public ChannelMidResponseBody changeMidStatus(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("midId") @Pattern(regexp = ID) String midId,
        @RequestBody @NotNull @Valid ChangeChannelStatusRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.changeMidStatus(operator, midId, body);
    }

    /** 能力目录（能力组编辑器按此渲染）。 */
    @GetMapping("/mid-capabilities/definitions")
    public CapabilityDefinitionsResponseBody capabilityDefinitions(HttpServletRequest servletRequest) {
        requireQueryKeys(servletRequest, Set.of());
        return client.capabilityDefinitions();
    }

    /** 创建能力条件组。 */
    @PostMapping("/mid-capabilities")
    @ResponseStatus(HttpStatus.CREATED)
    public MidCapabilityResponseBody createCapability(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid CreateMidCapabilityRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createCapability(operator, body);
    }

    /** 整组替换能力明细。 */
    @PutMapping("/mid-capabilities/{midCapabilityId}/details")
    public MidCapabilityResponseBody replaceCapabilityDetails(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("midCapabilityId") @Pattern(regexp = ID) String midCapabilityId,
        @RequestBody @NotNull @Valid ReplaceMidCapabilityDetailsRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.replaceCapabilityDetails(operator, midCapabilityId, body);
    }

    /** 启用或停用能力条件组。 */
    @PostMapping("/mid-capabilities/{midCapabilityId}/status")
    public MidCapabilityResponseBody changeCapabilityStatus(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("midCapabilityId") @Pattern(regexp = ID) String midCapabilityId,
        @RequestBody @NotNull @Valid ChangeMidCapabilityStatusRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.changeCapabilityStatus(operator, midCapabilityId, body);
    }

    /** 一个对象的变更记录（CHANNEL_CHANGE_READ）。 */
    @GetMapping("/config-changes")
    public PagingResponseBody<ChannelConfigChangeResponseBody> listChanges(HttpServletRequest servletRequest,
        @RequestParam("targetType") @Pattern(regexp = TARGET_TYPE) String targetType,
        @RequestParam("targetId") @Pattern(regexp = ID) String targetId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("targetType", "targetId", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listChanges(targetType, targetId, paging.page(), paging.size());
    }
}
