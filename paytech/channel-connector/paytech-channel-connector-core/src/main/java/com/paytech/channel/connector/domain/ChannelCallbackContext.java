package com.paytech.channel.connector.domain;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCallback;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCallbackItem;
import com.paytech.channel.connector.core.enums.CallbackMappingStatus;
import com.paytech.channel.connector.core.enums.CallbackSignatureStatus;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;

import lombok.Getter;
import lombok.Setter;

/** 一次渠道支付通知入站：渠道、账号、通知定义，以及验签与映射后待保存的回调信封与回调项。 */
@Getter
@Setter
public class ChannelCallbackContext {
    /** 收到通知的支付渠道。 */
    private final ChannelIdentity channel;

    /** 通知路径中的渠道技术账号。 */
    private final ChannelAccount account;

    /** 渠道通知定义。 */
    private final ChannelCallbackDefinition definition;

    /** 待保存的回调信封；验签后构造。 */
    private ChannelCallback callback;

    /** 待保存的回调项；验签失败时为 null（不映射未经验证的内容）。 */
    private ChannelCallbackItem item;

    /** 一次构造渠道、账号与定义。 */
    public ChannelCallbackContext(ChannelIdentity channel, ChannelAccount account, ChannelCallbackDefinition definition) {
        this.channel = channel;
        this.account = account;
        this.definition = definition;
    }

    /** 验签通过且已映射到支付尝试：同事务登记 ChannelPaymentNotified 发布任务。 */
    public boolean notifyPayment() {
        return callback.getSignatureStatus() == CallbackSignatureStatus.VERIFIED && item != null
            && item.getMappingStatus() == CallbackMappingStatus.MAPPED;
    }

    /** 验签是否通过。 */
    public boolean verified() {
        return callback.getSignatureStatus() == CallbackSignatureStatus.VERIFIED;
    }
}
