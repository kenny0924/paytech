package com.paytech.channel.connector.protocol.service.processor.request;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建或修改验证账号的已规范化输入；创建时 expectedVersion 为空。 */
@Getter
@AllArgsConstructor
public class SaveVerifyAccountRequest {

    /** 操作者 Account ID（X-Gateway-Actor-Subject，规范正十进制）。 */
    private final long actorId;

    /** 路径中的接入方编码。 */
    private final String integrationCode;

    /** 账号编码。 */
    private final String code;

    /** 名称，已去除首尾空白。 */
    private final String name;

    /** 渠道编码。 */
    private final String channelCode;

    /** 渠道账号标识。 */
    private final String channelAccountId;

    /** 经营主体编号。 */
    private final String entityNo;

    /** 普通参数。 */
    private final Map<String, String> parameters;

    /** 沙箱凭据。 */
    private final Map<String, String> credentials;

    /** 修改时的期望版本；创建为空。 */
    private final Integer expectedVersion;

    /** 原因。 */
    private final String reason;
}
