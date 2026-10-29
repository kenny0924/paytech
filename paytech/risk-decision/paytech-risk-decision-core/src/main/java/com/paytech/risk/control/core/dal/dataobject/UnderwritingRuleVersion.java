package com.paytech.risk.control.core.dal.dataobject;

import java.time.Instant;

import com.paytech.risk.control.core.enums.UnderwritingRuleStatus;

import lombok.Data;

/** 自动承保规则版本（t_underwriting_rule_version）；本期以 DML 维护，应用只读。 */
@Data
public class UnderwritingRuleVersion {
    /** 规则版本 ID。 */
    private long ruleVersionId;

    /** 从 1 递增的版本号。 */
    private int versionNo;

    /** 状态，同时至多一个 ACTIVE。 */
    private UnderwritingRuleStatus status;

    /** ACTIVE 占位；其他状态为空。 */
    private Boolean activeSlot;

    /** 风控额外排除的 MCC，升序英文逗号分隔；为空表示只排除卡组织高风险。 */
    private String excludedMccCodes;

    /** 置为 ACTIVE 的 UTC 时间。 */
    private Instant activatedTime;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
