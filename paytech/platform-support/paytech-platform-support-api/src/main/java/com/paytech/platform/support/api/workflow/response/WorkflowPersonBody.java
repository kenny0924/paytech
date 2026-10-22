package com.paytech.platform.support.api.workflow.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人员：账号与显示名（登录名，没有时为脱敏邮箱）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowPersonBody {

    /** 账号 ID。 */
    private String accountId;

    /** 显示名；账号已不存在时为空。 */
    private String displayName;
}
