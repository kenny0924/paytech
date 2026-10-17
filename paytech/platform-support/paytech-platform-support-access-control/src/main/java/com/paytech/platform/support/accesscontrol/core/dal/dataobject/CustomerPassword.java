package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsCredentialStatus;

/** CUSTOMER 密码投影仅在 Access Control Owner 内流转。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class CustomerPassword {
    /** 主键。 */
    private long id;

    /** 所属账号编号。 */
    private long accountId;

    /** 不可逆密码校验材料。 */
    private String verifier;

    /** 密码算法版本。 */
    private int algorithmVersion;

    /** 算法内存，单位 KiB。 */
    private int memoryKib;

    /** 算法迭代次数。 */
    private int iterations;

    /** 算法并行度。 */
    private int parallelism;

    /** 凭据版本。 */
    private int credentialVersion;

    /** 是否强制改密。 */
    private boolean forceChange;

    /** 当前状态。 */
    private OperationsCredentialStatus status;

    /** 隐藏密码校验材料。 */
    @Override public String toString() { return "CustomerPassword[REDACTED]"; }
}
