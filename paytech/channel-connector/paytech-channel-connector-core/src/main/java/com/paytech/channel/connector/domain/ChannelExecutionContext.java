package com.paytech.channel.connector.domain;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次执行配置读取所使用的一致本地记录；不持有解密凭据。 */
@Getter
@AllArgsConstructor
public class ChannelExecutionContext {
    /** 冻结渠道。 */
    private final ChannelIdentity channel;

    /** 冻结账号，不能用 MID 的当前绑定替换。 */
    private final ChannelAccount account;

    /** 冻结 MID。 */
    private final ChannelMid mid;
}
