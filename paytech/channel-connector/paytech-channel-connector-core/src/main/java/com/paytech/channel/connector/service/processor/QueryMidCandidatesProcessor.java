package com.paytech.channel.connector.service.processor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.api.ChannelCapabilityCodes;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.core.configuration.ChannelProperties;
import com.paytech.channel.connector.core.dal.dataobject.*;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.CapabilityView;
import com.paytech.channel.connector.domain.MidCandidateView;
import com.paytech.channel.connector.domain.MidCapabilityContext;
import com.paytech.channel.connector.domainservice.repository.*;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.service.processor.request.QueryMidCandidatesRequest;

import lombok.AllArgsConstructor;

/** Routing 的候选查询入口；只读取配置，不承担最终选路或外部访问。 */
@Component
@AllArgsConstructor
public class QueryMidCandidatesProcessor extends AbstractProcessor<QueryMidCandidatesRequest, List<MidCandidateView>> {
    private final ChannelProperties properties;
    private final ChannelMidRepository mids;
    private final MidCapabilityRepository capabilities;
    private final ChannelAccountRepository accounts;
    private final ChannelService channels;
    private final PlatformTransactionManager transactionManager;
    private final MidCapabilityDetailRepository details;

    /** 固定本次查询的一致快照，防止账号换绑或主从配置更新被分次读取后混用。 */
    @Override
    protected List<MidCandidateView> processRequest(QueryMidCandidatesRequest request) {
        var read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(status -> queryCandidates(request));
    }

    /** 批量读取当前 OE 的账号、MID 和能力，空候选是正常业务结果。 */
    List<MidCandidateView> queryCandidates(QueryMidCandidatesRequest request) {
        var accountRows = accounts.findByEntity(request.getEntityNo()).stream()
            .filter(account -> request.getEntityNo().equals(account.getEntityNo()))
            .collect(Collectors.toMap(ChannelAccount::getChannelAccountId, Function.identity()));
        var midRows = mids.findByAccountIds(List.copyOf(accountRows.keySet()));
        var channelRows =
            channels.findChannels(accountRows.values().stream().map(ChannelAccount::getChannelId).distinct().toList())
                .stream().collect(Collectors.toMap(ChannelIdentity::getChannelId, Function.identity()));
        var eligible = midRows.stream().filter(mid -> eligible(mid, accountRows, channelRows)).toList();
        var scopeRows = capabilities.findByMidIds(eligible.stream().map(ChannelMid::getMidId).toList());
        var detailRows =
            details.findByCapabilityIds(scopeRows.stream().map(MidCapability::getMidCapabilityId).toList());
        return eligible.stream()
            .map(mid -> candidate(mid, channelRows.get(mid.getChannelId()), scopeRows, detailRows, request))
            .filter(candidate -> !candidate.getCapabilities().isEmpty()).toList();
    }

    /** 悬空关联或 Channel/OE 不一致属于配置损坏，不能伪装成没有候选。 */
    boolean eligible(ChannelMid mid, Map<Long, ChannelAccount> accountRows, Map<Long, ChannelIdentity> channelRows) {
        var account = accountRows.get(mid.getChannelAccountId());
        var channel = channelRows.get(mid.getChannelId());
        if (account == null || channel == null || !Objects.equals(mid.getChannelId(), account.getChannelId())
            || !Objects.equals(mid.getEntityNo(), account.getEntityNo())) {
            throw new ErrorException("MID 与渠道账号的 Channel/OE 关系不一致");
        }
        return mid.getMidStatus() == ChannelStatus.ACTIVE && account.getAccountStatus() == ChannelStatus.ACTIVE
            && channel.getChannelStatus() == ChannelStatus.ACTIVE;
    }

    /** 只投影候选身份与有效能力，绝不携带账号参数、Token 或密钥；能力代码在此一处解析为支持操作与金额上下限（D06）。 */
    MidCandidateView candidate(ChannelMid mid, ChannelIdentity channel, List<MidCapability> scopes,
        List<MidCapabilityDetail> values, QueryMidCandidatesRequest request) {
        var midScopes = scopes.stream().filter(scope -> mid.getMidId().equals(scope.getMidId())).toList();
        var scopeIds = midScopes.stream().map(MidCapability::getMidCapabilityId).collect(Collectors.toSet());
        var midValues = values.stream().filter(value -> scopeIds.contains(value.getMidCapabilityId())).toList();
        var result = new MidCandidateView();
        result.setChannelId(mid.getChannelId());
        result.setChannelCode(channel.getChannelCode());
        result.setChannelAccountId(mid.getChannelAccountId());
        result.setMidId(mid.getMidId());
        result.setCapabilities(new MidCapabilityContext(midScopes, midValues).resolve(request.getCriteria(),
            properties.getCapabilityDefinitions()));
        project(result);
        return result;
    }

    /** ACTION.* 为 true 的操作、AMOUNT.MIN/MAX 与 SECURITY.3DS_MODE；能力按代码升序，因此支持操作也按代码升序。 */
    static void project(MidCandidateView candidate) {
        var operations = new LinkedHashSet<String>();
        for (CapabilityView capability : candidate.getCapabilities()) {
            String code = capability.getCapabilityCode();
            if (code.startsWith(ChannelCapabilityCodes.ACTION_PREFIX) && Boolean.TRUE.equals(capability.getBooleanValue())) {
                operations.add(code.substring(ChannelCapabilityCodes.ACTION_PREFIX.length()));
            }
            if (ChannelCapabilityCodes.AMOUNT_MIN.equals(code)) {
                candidate.setMinAmount(capability.getDecimalValue());
            }
            if (ChannelCapabilityCodes.AMOUNT_MAX.equals(code)) {
                candidate.setMaxAmount(capability.getDecimalValue());
            }
            if (ChannelCapabilityCodes.SECURITY_3DS_MODE.equals(code)) {
                candidate.setThreeDsMode(capability.getCodeValue());
            }
        }
        candidate.setSupportedOperations(operations);
    }
}
