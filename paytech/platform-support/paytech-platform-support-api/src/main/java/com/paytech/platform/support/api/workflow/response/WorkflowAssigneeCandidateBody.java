package com.paytech.platform.support.api.workflow.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 选人候选：ACTIVE 的 Operations 人员；不含邮箱原文。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowAssigneeCandidateBody {

    /** 账号 ID（十进制字符串）。 */
    private String accountId;

    /** 登录名；人员账号通常为空。 */
    private String loginName;

    /** 脱敏邮箱。 */
    private String emailMasked;

    /** 显示名：登录名，没有时为脱敏邮箱。 */
    private String displayName;
}
