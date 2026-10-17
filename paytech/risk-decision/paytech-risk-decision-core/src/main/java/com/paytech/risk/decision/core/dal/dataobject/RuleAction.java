package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/** 风险规则命中后产生的有序 Action 绑定。 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@Accessors(fluent = true)
public class RuleAction {

    /** 规则 Action 绑定的稳定业务标识。 */
    private long ruleActionId;

    /** 所属风险规则的稳定业务标识。 */
    private long ruleId;

    /** 被绑定 Action 版本的稳定业务标识。 */
    private long actionVersionId;

    /** 同一规则内的 Action 顺序。 */
    private int sequenceNo;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private Instant createTime;

    /** 工程定义的不可变主体绑定代码；终态动作为空。 */
    private String targetBindingCode;

    public RuleAction(long ruleActionId, long ruleId, long actionVersionId, int sequenceNo, Instant createTime) {
        this(ruleActionId, ruleId, actionVersionId, sequenceNo, createTime, null);
    }

}
