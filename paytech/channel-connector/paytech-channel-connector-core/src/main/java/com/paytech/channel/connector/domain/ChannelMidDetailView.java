package com.paytech.channel.connector.domain;

import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** MID 详情：MID 与其全部能力组、明细的组合结果。 */
@Getter
@AllArgsConstructor
public class ChannelMidDetailView {
    /** MID。 */
    private final ChannelMid mid;

    /** 能力组及明细，按能力组 ID 升序。 */
    private final List<MidCapabilityView> capabilities;
}
