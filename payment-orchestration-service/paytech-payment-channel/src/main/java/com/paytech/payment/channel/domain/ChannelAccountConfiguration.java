package com.paytech.payment.channel.domain;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** CHANNEL_ACCOUNT_CONFIG_V1 Payload；禁止日志、缓存、持久化或进入普通业务上下文。 */
@Getter
@Setter
public class ChannelAccountConfiguration {
    /** 首期固定为 1，用于校验 Payload 结构，不是配置发布版本。 */
    private Integer schemaVersion;

    /** 所属渠道代码，必须与本地记录一致。 */
    private String channelCode;

    /** 所属账号 ID 的十进制字符串，必须与冻结账号一致。 */
    private String channelAccountId;

    /** 所属 OE，必须与本地记录一致。 */
    private String entityNo;

    /** 渠道账号参数，例如 outletId；最多 64 项，键最多 64 字符，值最多 8192 字符。 */
    private Map<String, String> parameters;

    /** 渠道 API 密钥等凭据；同样有界，只在受控执行边界瞬时使用。 */
    private Map<String, String> credentials;
}
