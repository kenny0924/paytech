package com.paytech.channel.connector.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一致读取的某 OE 渠道账号、MID 及全部 MID 的能力配置；MID 候选查询与渠道身份就绪判定共用同一筛选口径
 * <li>可用：MID、所属渠道账号、渠道均为 ACTIVE（{@link #usable(Map)}）；渠道身份归渠道协议模块，由调用方 Processor 读取后传入。</li>
 * <li>只承载已读取的事实，不访问数据库或外部依赖；能力匹配由 {@link MidCapabilityContext} 执行。</li>
 */
@Getter
@AllArgsConstructor
public class OperatingEntityMids {
    /** 读取的 OE 编号（区分大小写）。 */
    private final String entityNo;

    /** 该 OE 的渠道账号，按账号 ID。 */
    private final Map<Long, ChannelAccount> accounts;

    /** 这些账号下的全部 MID，按 MID 读取顺序。 */
    private final List<ChannelMid> mids;

    /** 这些 MID 的全部能力条件组（含 DISABLED，按读取顺序）。 */
    private final List<MidCapability> capabilities;

    /** 这些条件组的全部明细（按读取顺序）。 */
    private final List<MidCapabilityDetail> details;

    /** 需要读取的渠道：账号所属渠道 ID，去重。 */
    public List<Long> channelIds() {
        return accounts.values().stream().map(ChannelAccount::getChannelId).distinct().toList();
    }

    /**
     * 筛出 MID、渠道账号、渠道均 ACTIVE 的可用 MID，按 MID 读取顺序
     *
     * @param channels 按渠道 ID 的渠道身份（同一一致性快照内读取）
     * @return 可用 MID 及各自的能力；没有时为空列表
     *         <li>悬空关联或 MID 与账号的 Channel/OE 不一致属于配置损坏，抛出 ErrorException，不伪装成没有可用 MID。</li>
     */
    public List<UsableMid> usable(Map<Long, ChannelIdentity> channels) {
        return mids.stream().filter(mid -> usable(mid, channels))
            .map(mid -> usableMid(mid, channels.get(mid.getChannelId()))).toList();
    }

    /** 单个 MID 是否可用；关系损坏时报错。 */
    boolean usable(ChannelMid mid, Map<Long, ChannelIdentity> channels) {
        ChannelAccount account = accounts.get(mid.getChannelAccountId());
        ChannelIdentity channel = channels.get(mid.getChannelId());
        if (account == null || channel == null || !Objects.equals(mid.getChannelId(), account.getChannelId())
            || !Objects.equals(mid.getEntityNo(), account.getEntityNo())) {
            throw new ErrorException("MID 与渠道账号的 Channel/OE 关系不一致");
        }
        return mid.getMidStatus() == ChannelStatus.ACTIVE && account.getAccountStatus() == ChannelStatus.ACTIVE
            && channel.getChannelStatus() == ChannelStatus.ACTIVE;
    }

    /** 归集一个 MID 的条件组与明细。 */
    UsableMid usableMid(ChannelMid mid, ChannelIdentity channel) {
        List<MidCapability> midScopes = capabilities.stream().filter(scope -> mid.getMidId().equals(scope.getMidId()))
            .toList();
        Set<Long> scopeIds = midScopes.stream().map(MidCapability::getMidCapabilityId).collect(Collectors.toSet());
        List<MidCapabilityDetail> midDetails = details.stream()
            .filter(detail -> scopeIds.contains(detail.getMidCapabilityId())).toList();
        return new UsableMid(mid, channel, midScopes, midDetails);
    }

    /** 一个可用 MID：MID、所属渠道与它的全部能力条件组和明细。 */
    @Getter
    @AllArgsConstructor
    public static class UsableMid {
        /** MID。 */
        private final ChannelMid mid;

        /** MID 所属渠道。 */
        private final ChannelIdentity channel;

        /** 该 MID 的全部能力条件组（含 DISABLED，按读取顺序）。 */
        private final List<MidCapability> capabilities;

        /** 这些条件组的全部明细（按读取顺序）。 */
        private final List<MidCapabilityDetail> details;

        /** 该 MID 的能力匹配上下文。 */
        public MidCapabilityContext capabilityContext() {
            return new MidCapabilityContext(capabilities, details);
        }
    }
}
