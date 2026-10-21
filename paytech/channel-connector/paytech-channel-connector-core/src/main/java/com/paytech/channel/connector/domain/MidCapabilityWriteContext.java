package com.paytech.channel.connector.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.protocol.domain.ChannelConfigChangeRecord;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次能力组写操作的上下文：所属 MID、能力组、本次全部明细、变更记录与写入时间
 * <li>与候选查询使用的只读 {@link MidCapabilityContext} 区分命名；明细已按能力目录校验。</li>
 * <li>创建与整组替换时 details 为待插入的全部明细；启停时为能力组现有明细，仅用于响应。</li>
 */
@Getter
@AllArgsConstructor
public class MidCapabilityWriteContext {
    /** 能力组所属 MID。 */
    private final ChannelMid mid;

    /** 被写入的能力组；新建时为待插入对象。 */
    private final MidCapability capability;

    /** 能力组的全部明细。 */
    private final List<MidCapabilityDetail> details;

    /** 与本次写入同事务插入的变更记录。 */
    private final ChannelConfigChangeRecord change;

    /** 本次写入时间，UTC 微秒。 */
    private final Instant now;

    /** 返回本次能力组与明细的组合结果。 */
    public MidCapabilityView toView() {
        return new MidCapabilityView(capability, details);
    }
}
