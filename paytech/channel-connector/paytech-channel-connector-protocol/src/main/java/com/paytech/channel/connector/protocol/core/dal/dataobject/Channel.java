package com.paytech.channel.connector.protocol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;

import lombok.Getter;
import lombok.Setter;

/** t_channel：渠道（支付渠道与服务渠道）的稳定身份、类型、运行状态与本环境出站地址。 */
@Getter
@Setter
public class Channel {
    /** 渠道内部标识。 */
    private Long channelId;

    /** 渠道编码，大小写敏感，创建后不可修改；也是外部 API 管理接口中的接入方编码。 */
    private String channelCode;

    /** 渠道名称。 */
    private String channelName;

    /** 渠道类型，创建后不可修改。 */
    private ChannelType channelType;

    /** 渠道生命周期状态。 */
    private ChannelStatus channelStatus;

    /** 渠道说明，未填写时为空。 */
    private String description;

    /**
     * 出站地址 JSON 数组 [{endpointCode, baseUrl, sandbox}]
     * <li>未配置时为空；由领域层解析。</li>
     */
    private String endpoints;

    /** 名称、说明或出站地址保存时递增的并发版本，初始为 1。 */
    private int version;

    /** 创建渠道的 Operations 账号标识；合表前创建的渠道可能为空。 */
    private Long createdBy;

    /** 最后修改渠道的 Operations 账号标识；合表前创建的渠道可能为空。 */
    private Long updatedBy;

    /** 创建 UTC 时间，微秒精度。 */
    private Instant createTime;

    /** 最后变更 UTC 时间，微秒精度。 */
    private Instant updateTime;
}
