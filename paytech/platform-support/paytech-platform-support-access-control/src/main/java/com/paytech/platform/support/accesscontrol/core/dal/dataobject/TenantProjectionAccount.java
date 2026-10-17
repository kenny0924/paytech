package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 来源开通只读取账号身份和当前状态。 */
@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
@Accessors(fluent = true)
public class TenantProjectionAccount {
    /** 全局账号主键。 */
    private long accountId;

    /** 账号认证域。 */
    private AuthenticationRealm realm;

    /** 账号类型。 */
    private OperationsAccountType accountType;

    /** 当前账号状态。 */
    private OperationsAccountStatus status;
}
