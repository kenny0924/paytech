package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 初始 OWNER 身份，不包含邮箱或登录凭据。 */
@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
@Accessors(fluent = true)
public class TenantProjectionMember {
    /** 初始 OWNER 成员主键。 */
    private long memberId;

    /** 冻结的原申请账号。 */
    private long accountId;
}
