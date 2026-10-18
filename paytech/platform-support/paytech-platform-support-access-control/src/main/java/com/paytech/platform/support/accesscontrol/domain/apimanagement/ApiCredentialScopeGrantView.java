package com.paytech.platform.support.accesscontrol.domain.apimanagement;

import java.time.Instant;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialGrantStatus;

import lombok.Getter;
import lombok.Setter;

/** 授权记录与 Scope 目录的查询投影；缺失目录时仍展示原授权编码。 */
@Getter
@Setter
public class ApiCredentialScopeGrantView {

    /** 授权明细主键。 */
    private Long scopeId;

    /** 记录中的精确授权编码。 */
    private String scopeCode;

    /** 当前目录名称，旧未登记值为 null。 */
    private String displayName;

    /** 旧资源模式，只读展示。 */
    private String resourcePattern;

    /** 旧动作代码，不在本期解释执行。 */
    private String actionCode;

    /** 持久化授权状态。 */
    private ApiCredentialStatus storedStatus;

    /** 包含的 UTC 生效时刻。 */
    private Instant effectiveFrom;

    /** 不包含的 UTC 结束时刻，可空。 */
    private Instant effectiveTo;

    /** 授权记录版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;

    /** 按本次冻结时点派生的状态，不受父凭证状态覆盖。 */
    private ApiCredentialGrantStatus status;

    /** 校验授权事实并按半开有效区间计算展示状态，仅更新派生字段。 */
    public void prepareForDisplay(Instant asOf) {
        if (storedStatus == null || effectiveFrom == null || version <= 0
            || (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom))) {
            throw new ErrorException("凭证 Scope 授权区间不一致");
        }
        if (storedStatus == ApiCredentialStatus.REVOKED) {
            status = ApiCredentialGrantStatus.REVOKED;
        } else if (storedStatus == ApiCredentialStatus.EXPIRED || (effectiveTo != null && !effectiveTo.isAfter(asOf))) {
            status = ApiCredentialGrantStatus.EXPIRED;
        } else if (effectiveFrom.isAfter(asOf)) {
            status = ApiCredentialGrantStatus.PENDING;
        } else {
            status = ApiCredentialGrantStatus.ACTIVE;
        }
    }
}
