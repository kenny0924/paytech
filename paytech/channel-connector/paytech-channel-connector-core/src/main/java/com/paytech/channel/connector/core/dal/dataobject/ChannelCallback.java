package com.paytech.channel.connector.core.dal.dataobject;

import java.time.Instant;

import com.paytech.channel.connector.core.enums.CallbackSignatureStatus;
import com.paytech.channel.connector.core.enums.CallbackStatus;

import lombok.Getter;
import lombok.Setter;

/** t_channel_callback：一次渠道回调信封的账号上下文、验签、去重与处理状态；不保存原始 Body。 */
@Getter
@Setter
public class ChannelCallback {
    /** 回调信封内部标识。 */
    private Long channelCallbackId;

    /** 收到回调的渠道 ID。 */
    private Long channelId;

    /** 通知路径中的渠道技术账号 ID。 */
    private Long channelAccountId;

    /** 渠道提供的回调事件唯一编号；PayerMax 不提供，为空。 */
    private String externalEventId;

    /**
     * 原始 Body 的 SHA-256 小写十六进制
     * <li>验签通过时写入，同一账号内用于去重；验签失败时为空，避免伪造通知占用真实通知的去重键。</li>
     */
    private String payloadFingerprint;

    /** 签名结论。 */
    private CallbackSignatureStatus signatureStatus;

    /** 处理状态。 */
    private CallbackStatus callbackStatus;

    /** 首次收到的 UTC 时间。 */
    private Instant receivedAt;

    /** 留存原文文件 ID；本期不留存，为空。 */
    private Long fileId;

    /** 留存原文的 SHA-256；本期不留存，为空。 */
    private String contentHash;

    /** 并发版本，初始为 1。 */
    private Long version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
