package com.paytech.finance.test.dal.row;

import java.time.Instant;

import lombok.Data;

/** 测试夹具直接插入的 t_channel_account（Finance 渠道清算账户）行。 */
@Data
public class ChannelAccountRow {
    private Long id;
    private Long channelId;
    private Long midId;
    private String entityNo;
    private String currency;
    private String status;
    private Instant createTime;
    private Instant updateTime;
}
