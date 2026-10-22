package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import lombok.Getter;
import lombok.Setter;

/** OPERATIONS 人员账号的选人与通知视图（t_user_account + 内部租户成员）；只在 access-control 模块内使用。 */
@Getter
@Setter
public class OperationsPersonnel {

    /** 账号主键。 */
    private Long accountId;

    /** 登录名；人员账号通常为空。 */
    private String loginName;

    /** 批准的邮箱脱敏展示。 */
    private String emailMasked;

    /** 登录邮箱稳定 Vault Token，禁止外显。 */
    private String emailVaultToken;

    /** 账号 ACTIVE 且内部租户成员与租户均 ACTIVE 时为 true。 */
    private Boolean active;

    /** 避免 Token 进入普通日志。 */
    @Override
    public String toString() {
        return "OperationsPersonnel(accountId=" + accountId + ", active=" + active + ")";
    }
}
