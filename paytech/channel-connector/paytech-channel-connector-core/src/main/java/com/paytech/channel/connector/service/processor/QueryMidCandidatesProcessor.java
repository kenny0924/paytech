package com.paytech.payment.channel.service.processor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payment.channel.core.configuration.ChannelProperties;
import com.paytech.payment.channel.core.dal.dataobject.*;
import com.paytech.payment.channel.core.enums.ChannelStatus;
import com.paytech.payment.channel.domain.MidCandidateView;
import com.paytech.payment.channel.domain.MidCapabilityContext;
import com.paytech.payment.channel.domainservice.repository.*;
import com.paytech.payment.channel.service.processor.request.QueryMidCandidatesRequest;

import lombok.AllArgsConstructor;

/** Routing 的候选查询入口；只读取配置，不承担最终选路或外部访问。 */
@Component
@AllArgsConstructor
public class QueryMidCandidatesProcessor extends AbstractProcessor<QueryMidCandidatesRequest, List<MidCandidateView>> {
    private final ChannelProperties properties;
    private final ChannelMidRepository mids;
    private final MidCapabilityRepository capabilities;
    private final ChannelAccountRepository accounts;
    private final PaymentChannelRepository channels;
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
            channels.findByIds(accountRows.values().stream().map(ChannelAccount::getChannelId).distinct().toList())
                .stream().collect(Collectors.toMap(PaymentChannel::getChannelId, Function.identity()));
        var eligible = midRows.stream().filter(mid -> eligible(mid, accountRows, channelRows)).toList();
        var scopeRows = capabilities.findByMidIds(eligible.stream().map(ChannelMid::getMidId).toList());
        var detailRows =
            details.findByCapabilityIds(scopeRows.stream().map(MidCapability::getMidCapabilityId).toList());
        return eligible.stream().map(mid -> candidate(mid, scopeRows, detailRows, request))
            .filter(candidate -> !candidate.getCapabilities().isEmpty()).toList();
    }

    /** 悬空关联或 Channel/OE 不一致属于配置损坏，不能伪装成没有候选。 */
    boolean eligible(ChannelMid mid, Map<Long, ChannelAccount> accountRows, Map<Long, PaymentChannel> channelRows) {
        var account = accountRows.get(mid.getChannelAccountId());
        var channel = channelRows.get(mid.getChannelId());
        if (account == null || channel == null || !Objects.equals(mid.getChannelId(), account.getChannelId())
            || !Objects.equals(mid.getEntityNo(), account.getEntityNo())) {
            throw new ErrorException("MID 与渠道账号的 Channel/OE 关系不一致");
        }
        return mid.getMidStatus() == ChannelStatus.ACTIVE && account.getAccountStatus() == ChannelStatus.ACTIVE
            && channel.getChannelStatus() == ChannelStatus.ACTIVE;
    }

    /** 只投影候选身份与有效能力，绝不携带账号参数、Token 或密钥。 */
    MidCandidateView candidate(ChannelMid mid, List<MidCapability> scopes, List<MidCapabilityDetail> values,
        QueryMidCandidatesRequest request) {
        var midScopes = scopes.stream().filter(scope -> mid.getMidId().equals(scope.getMidId())).toList();
        var scopeIds = midScopes.stream().map(MidCapability::getMidCapabilityId).collect(Collectors.toSet());
        var midValues = values.stream().filter(value -> scopeIds.contains(value.getMidCapabilityId())).toList();
        var result = new MidCandidateView();
        result.setChannelId(mid.getChannelId());
        result.setChannelAccountId(mid.getChannelAccountId());
        result.setMidId(mid.getMidId());
        result.setCapabilities(new MidCapabilityContext(midScopes, midValues).resolve(request.getCriteria(),
            properties.getCapabilityDefinitions()));
        return result;
    }
}
