package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import java.time.Instant;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 成员及全局账号的只读安全投影。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantMemberView {
    /** 所属租户标识。 */
    private Long tenantId;

    /** 成员标识。 */
    private Long memberId;

    /** 全局账号标识。 */
    private Long accountId;

    /** 脱敏邮箱，可空。 */
    private String emailMasked;

    /** 成员类型。 */
    private TenantMemberType memberType;

    /** 成员状态。 */
    private OperationsMemberStatus memberStatus;

    /** 账号状态。 */
    private OperationsAccountStatus accountStatus;

    /** 成员 UTC 创建时间。 */
    private Instant createdAt;

    /** 成员 UTC 更新时间。 */
    private Instant updatedAt;

    /** 账号认证域。 */
    private AuthenticationRealm realm;

    /** 账号类型。 */
    private OperationsAccountType accountType;

    /** 账号必须属于 CUSTOMER/HUMAN，缺失关联不能显示为空权限。 */
    public void requireValidMember() {
        if (memberId <= 0 || accountId <= 0 || tenantId <= 0
            || realm != AuthenticationRealm.CUSTOMER || accountType != OperationsAccountType.HUMAN)
            throw new ErrorException("租户成员授权记录不一致，请联系管理员检查");
    }
}
