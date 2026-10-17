package com.paytech.platform.support.accesscontrol.domain.apimanagement;

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

/** 凭证与所属 Tenant 的非秘密列表投影，不读取 PHC 或轮换详情。 */
@Getter
@Setter
public class ApiCredentialSummaryView {

    /** 凭证稳定标识。 */
    private Long credentialId;

    /** 所属 Tenant 标识。 */
    private Long tenantId;

    /** 所属 Tenant 类型；缺失关联时为空，由用例拒绝返回损坏记录。 */
    private AccessTenantType tenantType;

    /** Tenant 投影的 Client 主档标识。 */
    private Long clientId;

    /** Tenant 当前显示名称。 */
    private String clientName;

    /** 非秘密查找前缀，不包含完整凭证。 */
    private String keyPrefix;

    /** 数据库存储状态，不受到期时间派生结果覆盖。 */
    private ApiCredentialStatus storedStatus;

    /** 按本次冻结时点派生的展示状态，不代表真实认证结果。 */
    private ApiCredentialStatus status;

    /** Tenant 的 Owner 状态；失效 Tenant 仍可查看凭证。 */
    private OperationsTenantOwnerStatus tenantOwnerStatus;

    /** Tenant 的安全限制状态，与凭证展示状态分开返回。 */
    private OperationsTenantSecurityStatus tenantSecurityStatus;

    /** UTC 到期时刻；等于该时刻即过期。 */
    private Instant expiresTime;

    /** 当前行级并发版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;

    /**
     * 校验关联事实，并按本次查询时点计算展示状态。
     *
     * <p>
     * 失效 Tenant 仍可展示，撤销状态优先于到期；仅更新本 View 的派生字段。
     *
     * @param asOf
     *            Processor 冻结的 UTC 查询时点，列表和总数共用
     */
    public void prepareForDisplay(Instant asOf) {
        if (tenantType == null || clientId == null || clientName == null || storedStatus == null
            || tenantOwnerStatus == null || tenantSecurityStatus == null || expiresTime == null || version <= 0) {
            throw new ErrorException("凭证与 Tenant 记录不一致");
        }
        if (tenantType != AccessTenantType.CLIENT) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (storedStatus == ApiCredentialStatus.REVOKED) {
            status = ApiCredentialStatus.REVOKED;
        } else if (storedStatus == ApiCredentialStatus.EXPIRED || !expiresTime.isAfter(asOf)) {
            status = ApiCredentialStatus.EXPIRED;
        } else {
            status = ApiCredentialStatus.ACTIVE;
        }
    }
}
