package com.paytech.client.master.core.dal.dataobject;

import com.paytech.client.master.domain.enums.CatalogStatus;
import com.paytech.client.master.domain.enums.RuleScopeType;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 规则开关（t_rule_setting）：键值一值一行；rule_code 为开放代码，只解析已登记的代码。 */
@Getter
@Setter
public class RuleSetting {
    /** 规则行 ID。 */
    private Long ruleSettingId;
    /** 作用层级。 */
    private RuleScopeType scopeType;
    /** 作用对象代码；* 为同层默认。 */
    private String scopeCode;
    /** 规则代码。 */
    private String ruleCode;
    /** 规则取值文本。 */
    private String ruleValue;
    /** 状态，只有 ACTIVE 参与解析。 */
    private CatalogStatus status;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
