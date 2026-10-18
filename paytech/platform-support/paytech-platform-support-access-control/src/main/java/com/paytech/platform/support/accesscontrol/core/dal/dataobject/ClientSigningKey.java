package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

/** CUSTOMER 当前签名密钥，私钥始终由适配端口提供。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class ClientSigningKey {
    /** 主键。 */
    private long id;

    /** 公开签名密钥标识。 */
    private String kid;

    /** 签名密钥引用。 */
    private String keyRef;

    /** 公开验签材料。 */
    private String publicJwk;

    /** 密钥生效时间。 */
    private Instant notBefore;

    /** 并发版本。 */
    private int version;

    /** 不输出密钥引用。 */
    @Override public String toString() { return "ClientSigningKey[REDACTED]"; }
}
