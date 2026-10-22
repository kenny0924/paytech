package com.paytech.channel.connector.protocol.core.dal.dataobject;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按渠道分组的下级配置数量，只读聚合结果。 */
@Getter
@Setter
@NoArgsConstructor
public class ChannelChildCount {

    /** 渠道标识。 */
    private Long channelId;

    /** 该渠道下的配置行数。 */
    private long childCount;
}
