package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PLT-I03 查询时点内按 Scope Code 去重后的有效授权投影。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiCredentialEffectiveScope {
    /** 同一 Scope Code 有多条有效授权时取最小的稳定业务标识。 */
    private long scopeId;

    /** Scope 所属 API Credential 的稳定业务标识。 */
    private long credentialId;

    /** Gateway 用于 API Scope 授权匹配的稳定代码。 */
    private String scopeCode;

    /** 同一 Scope Code 有多条有效授权时取最小的并发版本，用于暴露非法持久化状态。 */
    private int version;
}
