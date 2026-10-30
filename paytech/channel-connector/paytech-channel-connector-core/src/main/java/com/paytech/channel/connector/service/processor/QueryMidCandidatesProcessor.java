package com.paytech.channel.connector.service.processor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.api.ChannelCapabilityCodes;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.core.configuration.ChannelProperties;
import com.paytech.channel.connector.domain.CapabilityView;
import com.paytech.channel.connector.domain.MidCandidateView;
import com.paytech.channel.connector.domain.OperatingEntityMids;
import com.paytech.channel.connector.domain.OperatingEntityMids.UsableMid;
import com.paytech.channel.connector.domainservice.service.OperatingEntityMidService;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.service.processor.request.MidCriteria;
import com.paytech.channel.connector.service.processor.request.QueryMidCandidatesRequest;

import lombok.AllArgsConstructor;

/**
 * Routing 的候选查询入口；只读取配置，不承担最终选路或外部访问
 * <li>在只读可重复读事务内经 {@link OperatingEntityMidService} 读取 OE 的账号、MID 与能力，经渠道协议模块的 {@link ChannelService}
 * 读取渠道，再由 {@link OperatingEntityMids#usable} 筛出可用 MID；固定一致快照，防止账号换绑或主从配置更新被分次读取后混用。</li>
 * <li>悬空关联、能力冲突或未注册属于配置损坏，抛错而不是返回空候选；空候选是正常业务结果。</li>
 */
@Component
@AllArgsConstructor
public class QueryMidCandidatesProcessor extends AbstractProcessor<QueryMidCandidatesRequest, List<MidCandidateView>> {
    private final ChannelService channelService;
    private final ChannelProperties properties;
    private final OperatingEntityMidService operatingEntityMidService;
    private final PlatformTransactionManager transactionManager;

    @Override
    protected List<MidCandidateView> processRequest(QueryMidCandidatesRequest request) {
        var read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(status -> candidates(usableMids(request.getEntityNo()), request.getCriteria()));
    }

    /** 读取 OE 的账号、MID 与能力及其渠道身份，筛出可用 MID；悬空关联抛 ErrorException。 */
    List<UsableMid> usableMids(String entityNo) {
        OperatingEntityMids mids = operatingEntityMidService.loadMids(entityNo);
        return mids.usable(channelService.findChannels(mids.channelIds()).stream()
            .collect(Collectors.toMap(ChannelIdentity::getChannelId, Function.identity())));
    }

    /** 逐个可用 MID 解析本次维度下的能力，没有任何能力命中的 MID 不成为候选。 */
    List<MidCandidateView> candidates(List<UsableMid> mids, MidCriteria criteria) {
        return mids.stream().map(mid -> candidate(mid, criteria))
            .filter(candidate -> !candidate.getCapabilities().isEmpty()).toList();
    }

    /** 只投影候选身份与有效能力，绝不携带账号参数、Token 或密钥；能力代码在此一处解析为支持操作与金额上下限（D06）。 */
    MidCandidateView candidate(UsableMid usable, MidCriteria criteria) {
        var mid = usable.getMid();
        var result = new MidCandidateView();
        result.setChannelId(mid.getChannelId());
        result.setChannelCode(usable.getChannel().getChannelCode());
        result.setChannelAccountId(mid.getChannelAccountId());
        result.setMidId(mid.getMidId());
        result.setCapabilities(usable.capabilityContext().resolve(criteria, properties.getCapabilityDefinitions()));
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
