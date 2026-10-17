package com.paytech.vault.client.admin.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 维护后的访问规则与新策略版本。
 */
@Getter
@Setter
public class AccessRuleResponseBody {
    /**
     * 保存后的访问规则
     */
    private AccessRule accessRule;

    /**
     * 本次变更产生的策略版本号
     */
    private int policyVersion;
}
