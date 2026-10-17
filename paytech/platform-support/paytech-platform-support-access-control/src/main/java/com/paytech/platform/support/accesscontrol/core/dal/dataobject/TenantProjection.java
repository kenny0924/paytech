package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.api.enums.TenantProjectionErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 来源顺序和本地并发版本是独立事实；条件写成功后同步原对象。 */
@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
@Accessors(fluent = true)
public class TenantProjection {
    /** 工作区租户主键。 */
    private long tenantId;

    /** CCT Client 主键。 */
    private long ownerRefId;

    /** 独立于安全状态的来源业务状态。 */
    private OperationsTenantOwnerStatus ownerStatus;

    /** CCT 生命周期来源序号；旧记录可空，投影入口拒绝无来源数据。 */
    private Integer sourceVersion;

    /** 不可变来源事实摘要。 */
    private String sourcePayloadHash;

    /** 本地并发版本，与来源序号独立。 */
    private int version;

    /** 原来源必须完整，不能将历史夹具补成合法投影。 */
    public void requireSource() {
        if (sourceVersion == null || sourceVersion < 1 || sourcePayloadHash == null
            || !sourcePayloadHash.matches("[a-f0-9]{64}") || version < 1)
            throw new FailException(TenantProjectionErrorCode.TENANT_PROJECTION_CONFLICT);
    }

    /** 同一来源位置必须对应相同的不可变摘要和业务状态。 */
    public void requireSameFact(String payloadHash, OperationsTenantOwnerStatus status) {
        if (!sourcePayloadHash.equals(payloadHash) || ownerStatus != status)
            throw new FailException(TenantProjectionErrorCode.TENANT_PROJECTION_CONFLICT);
    }

    /** 只推进连续下一版，不允许终态复活或退回入驻状态。 */
    public void requireNextFact(int nextVersion, OperationsTenantOwnerStatus status) {
        if ((long) nextVersion != (long) sourceVersion + 1)
            throw new ServiceUnavailableException(TenantProjectionErrorCode.TENANT_PROJECTION_NOT_READY);
        if ((ownerStatus == OperationsTenantOwnerStatus.TERMINATED && status != ownerStatus)
            || (ownerStatus != OperationsTenantOwnerStatus.PENDING && status == OperationsTenantOwnerStatus.PENDING))
            throw new FailException(TenantProjectionErrorCode.TENANT_PROJECTION_CONFLICT);
    }
}
