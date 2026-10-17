package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

import lombok.Getter;
import lombok.Setter;

/** 撤销操作锁定的凭证及 Tenant 校验字段，不包含 PHC；更新成功后同步原对象。 */
@Getter
@Setter
public class ApiCredentialManagement {

    /** 凭证稳定标识。 */
    private Long credentialId;

    /** 归属 Tenant 标识。 */
    private Long tenantId;

    /** 所属 Tenant 类型，只允许 CLIENT 管理。 */
    private AccessTenantType tenantType;

    /** Tenant 投影的 Client 标识。 */
    private Long clientId;

    /** Tenant 投影的名称。 */
    private String clientName;

    /** 非秘密查找前缀，禁止读取 PHC。 */
    private String keyPrefix;

    /** 持久化状态，未知值拒绝映射。 */
    private ApiCredentialStatus storedStatus;

    /** 所属 Tenant 的 Owner 状态。 */
    private OperationsTenantOwnerStatus tenantOwnerStatus;

    /** 所属 Tenant 的安全状态。 */
    private OperationsTenantSecurityStatus tenantSecurityStatus;

    /** UTC 到期时刻，认证的有效区间不包含该时刻。 */
    private Instant expiresTime;

    /** 同 Tenant 的旧凭证标识，可空。 */
    private Long rotatedFromId;

    /** 当前并发版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;

    /** 校验本地关联事实，只接受 CLIENT，保留失效 Tenant 的管理可见性。 */
    public void validateForManagement() {
        if (tenantType == null || clientId == null || clientName == null || storedStatus == null
            || tenantOwnerStatus == null || tenantSecurityStatus == null || expiresTime == null || version <= 0) {
            throw new ErrorException("凭证与 Tenant 记录不一致");
        }
        if (tenantType != AccessTenantType.CLIENT) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
    }
}
