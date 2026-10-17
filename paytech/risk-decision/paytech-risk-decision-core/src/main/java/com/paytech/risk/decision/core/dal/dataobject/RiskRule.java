package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/** Rule Set Version 内按优先级执行的风险规则。 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@Accessors(fluent = true)
public class RiskRule {

    /** 风险规则的稳定业务标识。 */
    private long ruleId;

    /** 所属规则集版本的稳定业务标识。 */
    private long ruleSetVersionId;

    /** Risk 受控目录中的规则编码。 */
    @NonNull
    private String ruleCode;

    /** 规则名称。 */
    @NonNull
    private String ruleName;

    /** 规则执行优先级。 */
    private int priorityNo;

    /** 规则是否启用。 */
    private boolean enabledFlag;

    /** 可空的 Risk 决策原因目录编码。 */
    private String reasonCode;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private Instant createTime;

    /** 可变记录最后一次持久化更新的 UTC 微秒时间。 */
    @NonNull
    private Instant updateTime;

    /** 当前规则版本冻结的手工内部文案；历史或非拒绝规则可空。 */
    private String reasonMessage;

    public RiskRule(long ruleId, long ruleSetVersionId, String ruleCode, String ruleName,
        int priorityNo, boolean enabledFlag, String reasonCode, Instant createTime, Instant updateTime) {
        this(ruleId, ruleSetVersionId, ruleCode, ruleName, priorityNo, enabledFlag, reasonCode,
            createTime, updateTime, null);
    }

}
