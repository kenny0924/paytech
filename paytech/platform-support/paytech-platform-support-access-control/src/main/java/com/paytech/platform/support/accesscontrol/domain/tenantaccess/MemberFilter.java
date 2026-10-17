package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 指定租户下的成员筛选。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberFilter {
    /** 账号筛选。 */
    private Long accountId;

    /** 已转义脱敏邮箱筛选。 */
    private String emailMasked;

    /** 成员类型筛选。 */
    private TenantMemberType memberType;

    /** 成员状态筛选。 */
    private OperationsMemberStatus memberStatus;
}
