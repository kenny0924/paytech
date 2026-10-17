package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** Operations 本库事实；由 Repository 在写入成功后同步原对象。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class OperationsSigningKey {
    /** JWT签名Key主键。 */
    private long id;

    /** 公开KID。 */
    private String kid;

    /** 仅非秘密签名Key引用。 */
    private String keyRef;

    /** 规范公开RSA JWK。 */
    private String publicJwk;

    /** Key生效时间。 */
    private Instant notBefore;

    /** 避免认证材料进入普通日志。 */
    @Override
    public String toString() { return "OperationsSigningKey[REDACTED]"; }
}
