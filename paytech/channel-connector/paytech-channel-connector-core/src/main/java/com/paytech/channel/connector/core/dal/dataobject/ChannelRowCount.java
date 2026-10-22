package com.paytech.channel.connector.core.dal.dataobject;

import lombok.Getter;
import lombok.Setter;

/** 按渠道分组的行数投影（账号数或 MID 数），只用于渠道列表的计数。 */
@Getter
@Setter
public class ChannelRowCount {
    /** 渠道 ID。 */
    private Long channelId;

    /** 该渠道下的行数（含已关闭）。 */
    private long rowCount;
}
