package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsCredentialStatus;

/** Operations 本库事实；由 Repository 在写入成功后同步原对象。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class OperationsPassword {
    /** 密码凭据主键。 */
    private long id;

    /** 所属账号。 */
    private long accountId;

    /** Argon2id PHC，禁止日志与对外暴露。 */
    private String verifier;

    /** Argon2版本。 */
    private int algorithmVersion;

    /** 内存KiB。 */
    private int memoryKib;

    /** 迭代次数。 */
    private int iterations;

    /** 并行参数。 */
    private int parallelism;

    /** 须与账号一致。 */
    private int credentialVersion;

    /** 初始密码强制修改。 */
    private boolean forceChange;

    /** 当前凭据状态。 */
    private OperationsCredentialStatus status;

    /** 避免认证材料进入普通日志。 */
    @Override
    public String toString() { return "OperationsPassword[REDACTED]"; }
}
