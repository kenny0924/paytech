package com.paytech.platform.support.api.clientapicredential.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** API 凭证摘要；不含密钥与校验值。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientApiCredentialResponseBody {

    /** 凭证 ID，十进制字符串。 */
    private String credentialId;

    /** 显示名称；历史凭证可能为空。 */
    private String name;

    /** 密钥前缀，用于识别是哪把密钥。 */
    private String keyPrefix;

    /** ACTIVE、REVOKED 或 EXPIRED（按到期时间推导）。 */
    private String status;

    /** 当前有效的 Scope 编码。 */
    private List<String> scopeCodes;

    /** 创建时间，UTC ISO-8601。 */
    private String createdAt;

    /** 到期时间，UTC ISO-8601。 */
    private String expiresAt;

    /** 轮换来源凭证 ID；非轮换产生为空。 */
    private String rotatedFromId;

    /** 并发版本，轮换与撤销时回传。 */
    private int version;
}
